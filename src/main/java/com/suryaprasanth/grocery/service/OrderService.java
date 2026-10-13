package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.Bill;
import com.suryaprasanth.grocery.dto.BulkAddResult;
import com.suryaprasanth.grocery.dto.CartRequest;
import com.suryaprasanth.grocery.dto.CheckoutRequest;
import com.suryaprasanth.grocery.dto.IssueRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.CheckoutRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All order rules live here: checkout, payment, cancel, missing-item reports and
 * status changes. Controllers stay thin and only call these methods.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /**
     * Which status can follow which, for the shop (and admins helping out). Anything else is
     * refused with 409. Delivery-only and pickup-only steps are checked separately.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PLACED, EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.READY_FOR_PICKUP),
            OrderStatus.PACKED, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED), // old FreshCart rows
            OrderStatus.OUT_FOR_DELIVERY, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.READY_FOR_PICKUP, EnumSet.of(OrderStatus.DELIVERED));

    /** The buyer may cancel before the shop starts preparing. */
    private static final Set<OrderStatus> BUYER_CAN_CANCEL =
            EnumSet.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PLACED, OrderStatus.ACCEPTED);

    private final OrderRepository orderRepository;
    private final CheckoutRepository checkoutRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final BillingService billingService;
    private final ShopRules rules;
    private final CartService cartService;
    private final SlotService slotService;
    private final NotificationService notificationService;
    private final ShopRepository shopRepository;

    public OrderService(OrderRepository orderRepository, CheckoutRepository checkoutRepository,
                        CartItemRepository cartItemRepository, ProductRepository productRepository,
                        BillingService billingService, ShopRules rules, CartService cartService,
                        SlotService slotService, NotificationService notificationService,
                        ShopRepository shopRepository) {
        this.cartService = cartService;
        this.slotService = slotService;
        this.notificationService = notificationService;
        this.shopRepository = shopRepository;
        this.orderRepository = orderRepository;
        this.checkoutRepository = checkoutRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.billingService = billingService;
        this.rules = rules;
    }

    // =====================================================================
    // Bill preview (cart + checkout pages)
    // =====================================================================

    @Transactional(readOnly = true)
    public Bill quote(User user, String couponCode) {
        return quote(user, couponCode, Map.of(), null);
    }

    @Transactional(readOnly = true)
    public Bill quote(User user, String couponCode, Map<Long, Fulfilment> fulfilment, String pincode) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        return billingService.buildBill(user, items, couponCode, false, fulfilment, pincode);
    }

    // =====================================================================
    // Checkout
    // =====================================================================

    /**
     * Places the basket as one checkout with one order per shop. Every step is inside ONE
     * database transaction: if any step fails (for any shop), nothing is saved: no stock taken,
     * no order created, cart not emptied.
     */
    @Transactional
    public Checkout placeOrder(User user, CheckoutRequest request) {
        // 1. Duplicate protection: same request id already used -> return that checkout.
        var existing = checkoutRepository.findByUserIdAndRequestId(user.getId(), request.getRequestId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Build the bill on the server, with the buyer's delivery/pickup choices and PIN code.
        //    Never trust prices sent by the browser.
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        if (items.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Your cart is empty");
        }
        Bill bill = billingService.buildBill(user, items, request.getCouponCode(), true,
                request.getFulfilment(), request.getPincode());
        if (!bill.problems().isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, bill.problems().get(0));
        }
        boolean anyDelivery = bill.shops().stream().anyMatch(sb -> Fulfilment.DELIVERY.name().equals(sb.fulfilment()));
        if (anyDelivery && (request.getShippingAddress() == null || request.getShippingAddress().isBlank())) {
            throw error(HttpStatus.BAD_REQUEST, "Enter a delivery address");
        }

        // 3. The buyer must have seen the same total (price changed / coupon expired meanwhile?).
        if (bill.total().compareTo(request.getExpectedTotal().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw error(HttpStatus.CONFLICT, "Your bill changed (new total Rs " + bill.total().toPlainString()
                    + "). Please review it and place the order again.");
        }

        // 4. Cash on delivery limit (on the whole basket).
        if (request.getPaymentMethod() == PaymentMethod.COD && !bill.codAllowed()) {
            throw error(HttpStatus.BAD_REQUEST, "Cash on delivery is available only for orders up to Rs "
                    + bill.codMaxAmount().toPlainString() + ". Please pay by UPI.");
        }

        // 4b. Time slots. Each chosen shop is locked (in id order, so two checkouts never wait on
        //     each other in a circle) and then its slot is checked, including how many places are left.
        Map<Long, SlotService.Chosen> slots = new java.util.TreeMap<>();
        java.util.Set<Long> basketShops = new java.util.HashSet<>();
        bill.shops().forEach(sb -> basketShops.add(sb.shopId()));
        for (var entry : new java.util.TreeMap<>(request.getSlots()).entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (!basketShops.contains(entry.getKey())) {
                throw error(HttpStatus.BAD_REQUEST, "A time slot was chosen for a shop that isn't in your basket");
            }
            Shop locked = shopRepository.lockById(entry.getKey())
                    .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Shop not found"));
            slots.put(entry.getKey(), slotService.check(locked, entry.getValue()));
        }

        // 5. Empty the cart in one statement. If another checkout for the same cart is running
        //    (second tab, double click), only one of them can delete the rows.
        int removed = cartItemRepository.deleteAllForUser(user.getId());
        if (removed != items.size()) {
            throw error(HttpStatus.CONFLICT, "Your cart changed or is already being checked out. Please refresh.");
        }

        // 6. The checkout: what belongs to the whole press of "Place order".
        Checkout checkout = new Checkout();
        checkout.setUser(user);
        checkout.setRequestId(request.getRequestId());
        checkout.setTotalAmount(bill.total());
        checkout.setPaymentMethod(request.getPaymentMethod());
        checkout = checkoutRepository.save(checkout);

        // 7. One order per shop. Stock is reserved with an atomic UPDATE ... WHERE stock >= qty.
        Map<Long, List<CartItem>> byShop = new LinkedHashMap<>();
        for (CartItem item : items) {
            byShop.computeIfAbsent(item.getProduct().getShop().getId(), k -> new ArrayList<>()).add(item);
        }
        for (var shopBill : bill.shops()) {
            Order order = new Order();
            Shop shop = null;
            for (CartItem item : byShop.get(shopBill.shopId())) {
                Product product = item.getProduct();
                shop = product.getShop();
                int updated = productRepository.reserveStock(product.getId(), item.getQuantity());
                if (updated == 0) {
                    // Throwing rolls back everything done above, for every shop, including the cart delete.
                    throw error(HttpStatus.CONFLICT, "Sorry, " + product.getName()
                            + " just went out of stock. Please update your cart.");
                }
                order.getItems().add(new OrderItem(order, product, item.getQuantity()));
            }
            Fulfilment fulfilment = Fulfilment.valueOf(shopBill.fulfilment());
            order.setUser(user);
            order.setShop(shop);
            order.setCheckout(checkout);
            order.setFulfilment(fulfilment);
            order.setShippingAddress(fulfilment == Fulfilment.DELIVERY ? request.getShippingAddress().trim() : null);
            order.setPincode(request.getPincode());
            order.setSubstitutionPreference(request.getSubstitutionPreference() != null
                    ? request.getSubstitutionPreference() : SubstitutionPreference.REFUND_ITEM);
            order.setSubtotal(shopBill.subtotal());
            order.setDiscount(shopBill.discount());
            order.setCouponCode(shopBill.discount().signum() > 0 || bill.couponCode() != null ? bill.couponCode() : null);
            order.setDeliveryFee(shopBill.deliveryFee());
            order.setGstIncluded(shopBill.gstIncluded());
            order.setTotalAmount(shopBill.total());
            order.setPaymentMethod(request.getPaymentMethod());
            SlotService.Chosen slot = slots.get(shopBill.shopId());
            if (slot != null) {
                order.setSlot(slot.date(), slot.start(), slot.end());
            }
            if (request.getPaymentMethod() == PaymentMethod.UPI) {
                order.setStatus(OrderStatus.PAYMENT_PENDING);
                order.setPaymentStatus(PaymentStatus.PENDING);
                order.setStatusNote("Complete the UPI payment within " + rules.getUpiTimeoutMinutes() + " minutes.");
            } else {
                order.setStatus(OrderStatus.PLACED);
                order.setPaymentStatus(PaymentStatus.COD_PENDING);
            }
            checkout.getOrders().add(orderRepository.save(order));
            if (order.getStatus() == OrderStatus.PLACED) {
                notificationService.newOrderForShop(order);
            }
        }
        orderRepository.flush();
        return checkout;
    }

    // =====================================================================
    // Demo UPI payment
    // =====================================================================

    /**
     * Result from the demo UPI screen. One payment covers every shop order of the same checkout,
     * so success, failure, time-out and late-payment refunds apply to all of them together.
     */
    @Transactional
    public Order pay(User user, Long orderId, boolean success) {
        Order order = getOwnedOrder(user, orderId);
        List<Order> group = ordersPaidTogether(order);

        // Payment came back too late: expire the orders first.
        for (Order o : group) {
            if (o.getStatus() == OrderStatus.PAYMENT_PENDING && isPaymentTimedOut(o)) {
                failPayment(o, "Payment was not completed within " + rules.getUpiTimeoutMinutes()
                        + " minutes, so the order was cancelled.");
            }
        }

        switch (order.getStatus()) {
            case PAYMENT_PENDING -> {
                for (Order o : group) {
                    if (o.getStatus() != OrderStatus.PAYMENT_PENDING) continue;
                    if (success) {
                        o.setStatus(OrderStatus.PLACED);
                        o.setPaymentStatus(PaymentStatus.PAID);
                        o.setStatusNote("Payment received. Thank you!");
                        notificationService.newOrderForShop(o);
                    } else {
                        failPayment(o, "UPI payment failed. No money was taken. Your items are back in your cart.");
                    }
                }
            }
            case PAYMENT_FAILED -> {
                if (success) {
                    for (Order o : group) {
                        if (o.getStatus() == OrderStatus.PAYMENT_FAILED && o.getPaymentStatus() != PaymentStatus.REFUNDED) {
                            // The famous "money deducted but order failed" case: refund automatically.
                            o.setPaymentStatus(PaymentStatus.REFUNDED);
                            o.setStatusNote("Your payment of Rs " + o.getTotalAmount().toPlainString()
                                    + " arrived after the order expired. A full refund has been started (3-5 working days).");
                        }
                    }
                }
                // A second "failed" message changes nothing. We return instead of throwing,
                // because throwing would roll back the time-out handling done just above.
            }
            default -> {
                if (order.getPaymentStatus() == PaymentStatus.PAID && success) {
                    return order; // same success message twice: nothing to do
                }
                throw error(HttpStatus.CONFLICT, "This order is not waiting for payment.");
            }
        }
        return order;
    }

    /** Runs every minute: cancels UPI orders that were never paid and frees their stock. */
    @Scheduled(fixedDelayString = "60000", initialDelayString = "60000")
    @Transactional
    public void expireUnpaidOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(rules.getUpiTimeoutMinutes());
        List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PAYMENT_PENDING, cutoff);
        for (Order order : stale) {
            failPayment(order, "Payment was not completed within " + rules.getUpiTimeoutMinutes()
                    + " minutes, so the order was cancelled. Your items are back in your cart.");
            log.info("Expired unpaid order #{}", order.getId());
        }
    }

    // =====================================================================
    // Cancel
    // =====================================================================

    @Transactional
    public Order cancelByCustomer(User user, Long orderId) {
        Order order = getOwnedOrder(user, orderId);
        if (!BUYER_CAN_CANCEL.contains(order.getStatus())) {
            throw error(HttpStatus.CONFLICT, "This order is already " + readable(order.getStatus())
                    + " and can no longer be cancelled.");
        }
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            // One UPI payment covers all shops of this checkout, so an unpaid checkout is cancelled as a whole.
            for (Order o : ordersPaidTogether(order)) {
                if (o.getStatus() == OrderStatus.PAYMENT_PENDING) cancel(o, "Cancelled by you.");
            }
        } else {
            cancel(order, "Cancelled by you.");
        }
        return order;
    }

    /**
     * "Order again": puts the items of a past order back into the cart, using today's prices and
     * the normal cart checks. Items no longer sold or out of stock are skipped with the reason.
     */
    @Transactional
    public BulkAddResult reorder(User user, Long orderId) {
        Order order = getOwnedOrder(user, orderId);
        List<CartRequest> requests = new ArrayList<>();
        List<String> gone = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() == null) {
                gone.add(item.getProductName() + " is no longer sold");
                continue;
            }
            CartRequest r = new CartRequest();
            r.setProductId(item.getProductId());
            r.setQuantity(item.getQuantity());
            requests.add(r);
        }
        BulkAddResult result = cartService.addMany(user, requests);
        if (gone.isEmpty()) {
            return result;
        }
        List<String> skipped = new ArrayList<>(gone);
        skipped.addAll(result.skipped());
        return new BulkAddResult(result.added(), skipped, result.cart());
    }

    // =====================================================================
    // Report a missing / damaged / expired item
    // =====================================================================

    @Transactional
    public Order reportIssue(User user, Long orderId, IssueRequest request) {
        Order order = getOwnedOrder(user, orderId);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw error(HttpStatus.CONFLICT, "You can report a problem only after the order is delivered.");
        }
        if (LocalDateTime.now().isAfter(order.getDeliveredAt().plusHours(rules.getIssueWindowHours()))) {
            throw error(HttpStatus.CONFLICT, "Problems must be reported within " + rules.getIssueWindowHours()
                    + " hours of delivery.");
        }
        OrderItem item = order.getItems().stream()
                .filter(it -> it.getId().equals(request.getOrderItemId()))
                .findFirst()
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "That item is not part of this order."));

        int alreadyReported = order.getIssues().stream()
                .filter(i -> i.getOrderItemId().equals(item.getId()))
                .mapToInt(OrderIssue::getQuantity).sum();
        int canReport = item.getQuantity() - alreadyReported;
        if (request.getQuantity() > canReport) {
            throw error(HttpStatus.BAD_REQUEST, canReport <= 0
                    ? "You have already reported every " + item.getProductName() + " in this order."
                    : "You can report at most " + canReport + " " + item.getProductName() + ".");
        }

        // Refund what the customer really paid for the item (after the coupon share).
        BigDecimal refund = item.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        if (order.getDiscount().signum() > 0 && order.getSubtotal().signum() > 0) {
            BigDecimal paidShare = order.getSubtotal().subtract(order.getDiscount());
            refund = refund.multiply(paidShare).divide(order.getSubtotal(), 2, RoundingMode.HALF_UP);
        }

        OrderIssue issue = new OrderIssue();
        issue.setOrder(order);
        issue.setOrderItemId(item.getId());
        issue.setProductName(item.getProductName());
        issue.setQuantity(request.getQuantity());
        issue.setReason(request.getReason());
        issue.setNote(request.getNote() == null ? null : request.getNote().trim());
        issue.setRefundAmount(refund.setScale(2, RoundingMode.HALF_UP));
        order.getIssues().add(issue);
        order.touch();
        return orderRepository.saveAndFlush(order);
    }

    // =====================================================================
    // Admin
    // =====================================================================

    /** Admins can move any shop's order (e.g. the platform's own demo shop, or to help a seller). */
    @Transactional
    public Order adminUpdateStatus(Long orderId, OrderStatus next) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
        return moveTo(order, next, "Cancelled by NammaBasket support. Sorry for the trouble.");
    }

    // =====================================================================
    // Seller: only orders of the seller's own shop
    // =====================================================================

    @Transactional(readOnly = true)
    public Order getShopOrder(Shop shop, Long orderId) {
        // Another shop's order answers 404, exactly like an order that doesn't exist.
        return orderRepository.findForShop(orderId, shop.getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found in your shop"));
    }

    @Transactional
    public Order sellerUpdateStatus(Shop shop, Long orderId, OrderStatus next) {
        Order order = getShopOrder(shop, orderId);
        if (next == OrderStatus.CANCELLED) {
            throw error(HttpStatus.BAD_REQUEST, "To turn an order down, reject it with a reason.");
        }
        return moveTo(order, next, null);
    }

    /** The shop turns the order down before preparing it. The buyer sees the reason; stock goes back. */
    @Transactional
    public Order sellerReject(Shop shop, Long orderId, String reason) {
        Order order = getShopOrder(shop, orderId);
        if (order.getStatus() != OrderStatus.PLACED && order.getStatus() != OrderStatus.ACCEPTED) {
            throw error(HttpStatus.CONFLICT, "Only new or accepted orders can be rejected (this one is "
                    + readable(order.getStatus()) + ").");
        }
        order.setRejectionReason(reason.trim());
        cancel(order, "Rejected by " + shop.getName() + ": " + reason.trim());
        notificationService.orderChanged(order);
        return order;
    }

    /** Applies one step of the life cycle after checking it is allowed for this order. */
    private Order moveTo(Order order, OrderStatus next, String cancelNote) {
        Set<OrderStatus> allowed = TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(next)) {
            throw error(HttpStatus.CONFLICT, "Cannot change order #" + order.getId() + " from "
                    + readable(order.getStatus()) + " to " + readable(next) + ".");
        }
        if (next == OrderStatus.OUT_FOR_DELIVERY && order.getFulfilment() != Fulfilment.DELIVERY) {
            throw error(HttpStatus.CONFLICT, "Order #" + order.getId() + " is for pickup: mark it ready for pickup instead.");
        }
        if (next == OrderStatus.READY_FOR_PICKUP && order.getFulfilment() != Fulfilment.PICKUP) {
            throw error(HttpStatus.CONFLICT, "Order #" + order.getId() + " is for delivery: mark it out for delivery instead.");
        }
        if (next == OrderStatus.CANCELLED) {
            cancel(order, cancelNote);
            notificationService.orderChanged(order);
            return order;
        }
        order.setStatus(next);
        order.setStatusNote(null);
        order.touch();
        if (next == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.PAID); // cash collected at the door / counter
            }
        }
        notificationService.orderChanged(order);
        return order;
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Loads an order and makes sure it belongs to this user (stops "change the id in the URL" attacks). */
    public Order getOwnedOrder(User user, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw error(HttpStatus.FORBIDDEN, "This order does not belong to you");
        }
        return order;
    }

    /** All shop orders covered by the same UPI payment (just this one for old orders). */
    private List<Order> ordersPaidTogether(Order order) {
        return order.getCheckout() == null ? List.of(order) : order.getCheckout().getOrders();
    }

    private boolean isPaymentTimedOut(Order order) {
        return order.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(rules.getUpiTimeoutMinutes()));
    }

    private void failPayment(Order order, String note) {
        releaseStock(order);
        restoreCart(order);
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setStatusNote(note);
    }

    private void cancel(Order order, String note) {
        releaseStock(order);
        order.setStatus(OrderStatus.CANCELLED);
        order.touch();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            note = note + " Refund of Rs " + order.getTotalAmount().toPlainString()
                    + " started to your UPI account (3-5 working days).";
        } else {
            order.setPaymentStatus(PaymentStatus.NOT_CHARGED);
        }
        order.setStatusNote(note);
    }

    /** Puts the order's items back on the shelf, exactly once (the flag stops a second release). */
    private void releaseStock(Order order) {
        if (order.isStockReleased()) {
            return;
        }
        order.setStockReleased(true);
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() != null) {
                productRepository.releaseStock(item.getProductId(), item.getQuantity());
            }
        }
    }

    /** After a failed payment, put the items back in the cart so the customer can retry easily. */
    private void restoreCart(Order order) {
        User user = order.getUser();
        for (OrderItem item : order.getItems()) {
            if (item.getProductId() == null) {
                continue;
            }
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                CartItem cartItem = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                        .orElse(new CartItem(user, product, 0));
                int qty = Math.min(cartItem.getQuantity() + item.getQuantity(), rules.getMaxQtyPerItem());
                cartItem.setQuantity(qty);
                cartItem.setPriceWhenAdded(product.getPrice());
                cartItemRepository.save(cartItem);
            });
        }
    }

    private static String readable(OrderStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
