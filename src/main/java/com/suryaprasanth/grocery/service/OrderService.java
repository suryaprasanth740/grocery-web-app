package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.Bill;
import com.suryaprasanth.grocery.dto.CheckoutRequest;
import com.suryaprasanth.grocery.dto.IssueRequest;
import com.suryaprasanth.grocery.dto.PincodeCheck;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
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
import java.util.EnumSet;
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

    /** Which status can follow which. Anything else is refused. */
    private static final Map<OrderStatus, Set<OrderStatus>> ADMIN_TRANSITIONS = Map.of(
            OrderStatus.PLACED, EnumSet.of(OrderStatus.PACKED, OrderStatus.CANCELLED),
            OrderStatus.PACKED, EnumSet.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELLED),
            OrderStatus.OUT_FOR_DELIVERY, EnumSet.of(OrderStatus.DELIVERED));

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final BillingService billingService;
    private final DeliveryService deliveryService;
    private final ShopRules rules;

    public OrderService(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                        ProductRepository productRepository, BillingService billingService,
                        DeliveryService deliveryService, ShopRules rules) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.billingService = billingService;
        this.deliveryService = deliveryService;
        this.rules = rules;
    }

    // =====================================================================
    // Bill preview (cart + checkout pages)
    // =====================================================================

    @Transactional(readOnly = true)
    public Bill quote(User user, String couponCode) {
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        return billingService.buildBill(user, items, couponCode, false);
    }

    // =====================================================================
    // Checkout
    // =====================================================================

    /**
     * Places an order. Every step is inside ONE database transaction: if any step fails,
     * nothing is saved (no stock taken, cart not emptied).
     */
    @Transactional
    public Order placeOrder(User user, CheckoutRequest request) {
        // 1. Duplicate protection: same request id already used -> return that order.
        var existing = orderRepository.findByUserIdAndRequestId(user.getId(), request.getRequestId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. PIN code must be one we deliver to.
        PincodeCheck pin = deliveryService.check(request.getPincode());
        if (!pin.serviceable()) {
            throw error(HttpStatus.BAD_REQUEST, pin.message());
        }

        // 3. Build the bill on the server. Never trust prices sent by the browser.
        List<CartItem> items = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        if (items.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Your cart is empty");
        }
        Bill bill = billingService.buildBill(user, items, request.getCouponCode(), true);
        if (!bill.problems().isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, bill.problems().get(0));
        }

        // 4. The customer must have seen the same total (price changed / coupon expired meanwhile?).
        if (bill.total().compareTo(request.getExpectedTotal().setScale(2, RoundingMode.HALF_UP)) != 0) {
            throw error(HttpStatus.CONFLICT, "Your bill changed (new total Rs " + bill.total().toPlainString()
                    + "). Please review it and place the order again.");
        }

        // 5. Cash on delivery limit.
        if (request.getPaymentMethod() == PaymentMethod.COD && !bill.codAllowed()) {
            throw error(HttpStatus.BAD_REQUEST, "Cash on delivery is available only for orders up to Rs "
                    + bill.codMaxAmount().toPlainString() + ". Please pay by UPI.");
        }

        // 6. Empty the cart in one statement. If another checkout for the same cart is running
        //    (second tab, double click), only one of them can delete the rows.
        int removed = cartItemRepository.deleteAllForUser(user.getId());
        if (removed != items.size()) {
            throw error(HttpStatus.CONFLICT, "Your cart changed or is already being checked out. Please refresh.");
        }

        // 7. Reserve stock with an atomic UPDATE ... WHERE stock >= qty.
        Order order = new Order();
        for (CartItem item : items) {
            Product product = item.getProduct();
            int updated = productRepository.reserveStock(product.getId(), item.getQuantity());
            if (updated == 0) {
                // Throwing rolls back everything done above, including the cart delete.
                throw error(HttpStatus.CONFLICT, "Sorry, " + product.getName()
                        + " just went out of stock. Please update your cart.");
            }
            order.getItems().add(new OrderItem(order, product, item.getQuantity()));
        }

        // 8. Save the order with the full bill.
        order.setUser(user);
        order.setRequestId(request.getRequestId());
        order.setShippingAddress(request.getShippingAddress().trim());
        order.setPincode(pin.pincode());
        order.setSubstitutionPreference(request.getSubstitutionPreference() != null
                ? request.getSubstitutionPreference() : SubstitutionPreference.REFUND_ITEM);
        order.setSubtotal(bill.subtotal());
        order.setDiscount(bill.discount());
        order.setCouponCode(bill.couponCode());
        order.setDeliveryFee(bill.deliveryFee());
        order.setGstIncluded(bill.gstIncluded());
        order.setTotalAmount(bill.total());
        order.setPaymentMethod(request.getPaymentMethod());
        if (request.getPaymentMethod() == PaymentMethod.UPI) {
            order.setStatus(OrderStatus.PAYMENT_PENDING);
            order.setPaymentStatus(PaymentStatus.PENDING);
            order.setStatusNote("Complete the UPI payment within " + rules.getUpiTimeoutMinutes() + " minutes.");
        } else {
            order.setStatus(OrderStatus.PLACED);
            order.setPaymentStatus(PaymentStatus.COD_PENDING);
        }
        return orderRepository.saveAndFlush(order);
    }

    // =====================================================================
    // Demo UPI payment
    // =====================================================================

    @Transactional
    public Order pay(User user, Long orderId, boolean success) {
        Order order = getOwnedOrder(user, orderId);

        // Payment came back too late: expire the order first.
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING && isPaymentTimedOut(order)) {
            failPayment(order, "Payment was not completed within " + rules.getUpiTimeoutMinutes()
                    + " minutes, so the order was cancelled.");
        }

        switch (order.getStatus()) {
            case PAYMENT_PENDING -> {
                if (success) {
                    order.setStatus(OrderStatus.PLACED);
                    order.setPaymentStatus(PaymentStatus.PAID);
                    order.setStatusNote("Payment received. Thank you!");
                } else {
                    failPayment(order, "UPI payment failed. No money was taken. Your items are back in your cart.");
                }
            }
            case PAYMENT_FAILED -> {
                if (success && order.getPaymentStatus() != PaymentStatus.REFUNDED) {
                    // The famous "money deducted but order failed" case: refund automatically.
                    order.setPaymentStatus(PaymentStatus.REFUNDED);
                    order.setStatusNote("Your payment of Rs " + order.getTotalAmount().toPlainString()
                            + " arrived after the order expired. A full refund has been started (3-5 working days).");
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
        if (order.getStatus() != OrderStatus.PLACED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw error(HttpStatus.CONFLICT, "This order is already " + readable(order.getStatus())
                    + " and can no longer be cancelled.");
        }
        cancel(order, "Cancelled by you.");
        return order;
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

    @Transactional
    public Order adminUpdateStatus(Long orderId, OrderStatus next) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Order not found"));
        Set<OrderStatus> allowed = ADMIN_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(next)) {
            throw error(HttpStatus.CONFLICT, "Cannot change order #" + order.getId() + " from "
                    + order.getStatus() + " to " + next + ".");
        }
        if (next == OrderStatus.CANCELLED) {
            cancel(order, "Cancelled by the store. Sorry for the trouble.");
            return order;
        }
        order.setStatus(next);
        order.setStatusNote(null);
        if (next == OrderStatus.DELIVERED) {
            order.setDeliveredAt(LocalDateTime.now());
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.setPaymentStatus(PaymentStatus.PAID); // cash collected at the door
            }
        }
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
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            note = note + " Refund of Rs " + order.getTotalAmount().toPlainString()
                    + " started to your UPI account (3-5 working days).";
        } else {
            order.setPaymentStatus(PaymentStatus.NOT_CHARGED);
        }
        order.setStatusNote(note);
    }

    private void releaseStock(Order order) {
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
