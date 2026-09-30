package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.BudgetSummary;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderItem;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Budget mode: the customer sets a monthly grocery budget. The app shows how much of it
 * is used, warns BEFORE checkout if the cart would go over, and suggests cheaper swaps.
 */
@Service
public class BudgetService {

    /** The shop is in India, so "this month" follows Indian time, not the server's clock. */
    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Kolkata");

    public static final BigDecimal MIN_BUDGET = new BigDecimal("100");
    public static final BigDecimal MAX_BUDGET = new BigDecimal("500000");
    /** At or above this share of the budget the page shows "almost used up". */
    public static final int WARN_PERCENT = 80;

    /** Orders that never cost the customer money. */
    private static final Set<OrderStatus> NOT_SPENT = Set.of(OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED);

    /** Everyday cheaper alternatives: product -> similar product that costs less. */
    public static final Map<String, String> CHEAPER_SWAPS = Map.of(
            "Basmati Rice", "Sona Masoori Rice",
            "Full Cream Milk", "Toned Milk",
            "Greek Yogurt", "Fresh Curd",
            "Mixed Nuts", "Roasted Peanuts",
            "Butter Croissants", "Whole Wheat Bread",
            "Chocolate Muffins", "Digestive Biscuits",
            "Orange Juice", "Fresh Oranges");

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final BillingService billingService;

    public BudgetService(UserRepository userRepository, OrderRepository orderRepository,
                         ProductRepository productRepository, CartItemRepository cartItemRepository,
                         BillingService billingService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.billingService = billingService;
    }

    /** Sets the budget. null or 0 turns budget mode off. */
    public void setBudget(User user, BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            user.setMonthlyBudget(null);
        } else if (amount.compareTo(MIN_BUDGET) < 0 || amount.compareTo(MAX_BUDGET) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Budget must be between Rs " + MIN_BUDGET.toPlainString() + " and Rs " + MAX_BUDGET.toPlainString() + ".");
        } else {
            user.setMonthlyBudget(amount.setScale(2, RoundingMode.HALF_UP));
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public BudgetSummary summary(User user) {
        YearMonth month = YearMonth.now(SHOP_ZONE);
        // Order times are saved in the server's clock, so convert "1st of this month in India" to it.
        LocalDateTime monthStart = month.atDay(1).atStartOfDay(SHOP_ZONE)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();

        BigDecimal spent = BigDecimal.ZERO;
        int orderCount = 0;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Order order : orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (order.getCreatedAt().isBefore(monthStart) || NOT_SPENT.contains(order.getStatus())) {
                continue;
            }
            orderCount++;
            // Refunds for missing / damaged items come back to the customer, so they don't count.
            spent = spent.add(order.getTotalAmount()).subtract(order.issueRefundTotal());
            for (OrderItem item : order.getItems()) {
                String category = categoryOf(item.getProductId());
                BigDecimal line = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                byCategory.merge(category, line, BigDecimal::add);
            }
        }

        List<CartItem> cart = cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
        BigDecimal cartTotal = cart.isEmpty() ? BigDecimal.ZERO
                : billingService.buildBill(user, cart, null, false).total();

        BigDecimal budget = user.getMonthlyBudget();
        BigDecimal remaining = null;
        BigDecimal afterCart = null;
        Integer percentUsed = null;
        String status = "NO_BUDGET";
        if (budget != null && budget.signum() > 0) {
            remaining = budget.subtract(spent);
            afterCart = remaining.subtract(cartTotal);
            percentUsed = spent.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.HALF_UP).intValue();
            int percentAfterCart = spent.add(cartTotal).multiply(BigDecimal.valueOf(100))
                    .divide(budget, 0, RoundingMode.HALF_UP).intValue();
            if (afterCart.signum() < 0) {
                status = "OVER";
            } else if (percentAfterCart >= WARN_PERCENT) {
                status = "NEAR";
            } else {
                status = "OK";
            }
        }

        List<BudgetSummary.CategorySpend> categories = new ArrayList<>();
        byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .forEach(e -> categories.add(new BudgetSummary.CategorySpend(e.getKey(), money(e.getValue()))));

        List<BudgetSummary.Swap> swaps = cheaperSwaps(cart);
        BigDecimal possibleSaving = swaps.stream().map(BudgetSummary.Swap::saving)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
        return new BudgetSummary(money(budget), monthLabel, money(spent), orderCount, money(remaining),
                percentUsed, money(cartTotal), money(afterCart), status, categories, swaps, money(possibleSaving));
    }

    /** For each cart item with a cheaper, buyable alternative: what to swap and how much it saves. */
    List<BudgetSummary.Swap> cheaperSwaps(List<CartItem> cart) {
        List<BudgetSummary.Swap> swaps = new ArrayList<>();
        for (CartItem item : cart) {
            Product from = item.getProduct();
            String toName = CHEAPER_SWAPS.get(from.getName());
            if (toName == null) {
                continue;
            }
            Optional<Product> to = productRepository.findAll().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(toName))
                    .min(Comparator.comparing(Product::getId));
            if (to.isEmpty()) {
                continue;
            }
            Product cheaper = to.get();
            boolean buyable = cheaper.getStock() >= item.getQuantity() && !cheaper.isExpired();
            BigDecimal saving = from.getPrice().subtract(cheaper.getPrice())
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            if (buyable && saving.signum() > 0) {
                swaps.add(new BudgetSummary.Swap(from.getId(), from.getName(), cheaper, item.getQuantity(), money(saving)));
            }
        }
        return swaps;
    }

    private String categoryOf(Long productId) {
        if (productId == null) {
            return "Other";
        }
        return productRepository.findById(productId)
                .filter(p -> p.getCategory() != null)
                .map(p -> p.getCategory().getName())
                .orElse("Other");
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
