package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.ReorderSuggestion;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderItem;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * "Running low?" — learns how often the customer buys each product from their own order
 * history (milk every 3 days, rice every 30 days) and reminds them when it's about to run out.
 *
 * Example: milk bought on the 1st, 4th and 7th -> every 3 days -> due again on the 10th.
 */
@Service
public class ReorderService {

    /** Show "running low" this many days before the usual re-buy day. */
    public static final int SOON_DAYS = 2;
    private static final Set<OrderStatus> NOT_BOUGHT = Set.of(
            OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED, OrderStatus.PAYMENT_PENDING);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public ReorderService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ReorderSuggestion> suggestions(User user) {
        LocalDate today = LocalDate.now(BudgetService.SHOP_ZONE);

        // productId -> (day bought -> packs bought that day)
        Map<Long, TreeMap<LocalDate, Integer>> history = new TreeMap<>();
        for (Order order : orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (NOT_BOUGHT.contains(order.getStatus())) {
                continue;
            }
            LocalDate day = order.getCreatedAt().atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(BudgetService.SHOP_ZONE).toLocalDate();
            for (OrderItem item : order.getItems()) {
                if (item.getProductId() == null) {
                    continue;
                }
                history.computeIfAbsent(item.getProductId(), id -> new TreeMap<>())
                        .merge(day, item.getQuantity(), Integer::sum);
            }
        }

        List<ReorderSuggestion> result = new ArrayList<>();
        for (Map.Entry<Long, TreeMap<LocalDate, Integer>> entry : history.entrySet()) {
            Product product = productRepository.findById(entry.getKey()).orElse(null);
            if (product == null) {
                continue;
            }
            TreeMap<LocalDate, Integer> days = entry.getValue();
            int times = days.size();
            int totalPacks = days.values().stream().mapToInt(Integer::intValue).sum();
            int typical = Math.max(1, Math.round((float) totalPacks / times));
            LocalDate first = days.firstKey();
            LocalDate last = days.lastKey();

            Integer everyDays = null;
            Integer daysLeft = null;
            String status = "BOUGHT_ONCE";
            if (times >= 2) {
                long span = ChronoUnit.DAYS.between(first, last);
                everyDays = (int) Math.max(1, Math.round((double) span / (times - 1)));
                LocalDate due = last.plusDays(everyDays);
                daysLeft = (int) ChronoUnit.DAYS.between(today, due);
                status = daysLeft <= 0 ? "DUE" : daysLeft <= SOON_DAYS ? "SOON" : "LATER";
            }
            result.add(new ReorderSuggestion(product, typical, everyDays, last, daysLeft, status, times));
        }

        // Most urgent first; products bought only once go last, newest first.
        result.sort(Comparator
                .comparing((ReorderSuggestion s) -> s.daysLeft() == null ? Integer.MAX_VALUE : s.daysLeft())
                .thenComparing(ReorderSuggestion::lastBought, Comparator.reverseOrder()));
        return result;
    }
}
