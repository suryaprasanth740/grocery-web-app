package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderItem;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Numbers for the seller dashboard, all worked out from the shop's real orders and products.
 * Nothing is estimated: a shop with no orders gets zeros and empty lists, and the page shows
 * an empty state.
 */
@Service
public class SellerDashboardService {

    /** Orders a buyer has actually placed (paid, or cash on delivery) and not cancelled. */
    public static final Set<OrderStatus> COUNTED = EnumSet.of(OrderStatus.PLACED, OrderStatus.ACCEPTED,
            OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
    public static final Set<OrderStatus> NEW = EnumSet.of(OrderStatus.PLACED);
    public static final Set<OrderStatus> IN_PROGRESS = EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING,
            OrderStatus.PACKED, OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY);
    public static final Set<OrderStatus> DONE = EnumSet.of(OrderStatus.DELIVERED);
    public static final Set<OrderStatus> CANCELLED = EnumSet.of(OrderStatus.CANCELLED);
    /** Unpaid UPI orders are not shown to the shop until the payment goes through. */
    public static final Set<OrderStatus> VISIBLE_TO_SHOP = EnumSet.of(OrderStatus.PLACED, OrderStatus.ACCEPTED,
            OrderStatus.PREPARING, OrderStatus.PACKED, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.CANCELLED);

    public static final int LOW_STOCK = 5;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public SellerDashboardService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(Shop shop) {
        Long id = shop.getId();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("newOrders", orderRepository.countForShop(id, NEW));
        body.put("inProgress", orderRepository.countForShop(id, IN_PROGRESS));
        body.put("completed", orderRepository.countForShop(id, DONE));
        body.put("cancelled", orderRepository.countForShop(id, CANCELLED));
        body.put("totalOrders", orderRepository.countForShop(id, COUNTED));

        // Days are counted in Indian time, whatever zone the server runs in.
        ZoneId server = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(Shop.SHOP_ZONE);
        LocalDateTime since = today.minusDays(29).atStartOfDay(Shop.SHOP_ZONE).withZoneSameInstant(server).toLocalDateTime();
        List<Order> recent30 = orderRepository.findForShopSince(id, since, COUNTED);

        Map<LocalDate, BigDecimal> salesByDay = new TreeMap<>();
        Map<LocalDate, Integer> ordersByDay = new TreeMap<>();
        for (int i = 6; i >= 0; i--) {
            salesByDay.put(today.minusDays(i), BigDecimal.ZERO);
            ordersByDay.put(today.minusDays(i), 0);
        }
        Map<String, long[]> qtyByProduct = new HashMap<>();      // name -> [quantity]
        Map<String, BigDecimal> salesByProduct = new HashMap<>();
        for (Order o : recent30) {
            LocalDate day = o.getCreatedAt().atZone(server).withZoneSameInstant(Shop.SHOP_ZONE).toLocalDate();
            if (salesByDay.containsKey(day)) {
                salesByDay.merge(day, o.getTotalAmount(), BigDecimal::add);
                ordersByDay.merge(day, 1, Integer::sum);
            }
            for (OrderItem item : o.getItems()) {
                qtyByProduct.computeIfAbsent(item.getProductName(), k -> new long[1])[0] += item.getQuantity();
                salesByProduct.merge(item.getProductName(),
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), BigDecimal::add);
            }
        }

        List<Map<String, Object>> daily = new ArrayList<>();
        BigDecimal week = BigDecimal.ZERO;
        int weekOrders = 0;
        for (LocalDate day : salesByDay.keySet()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", day.toString());
            d.put("orders", ordersByDay.get(day));
            d.put("sales", BillingService.money(salesByDay.get(day)));
            daily.add(d);
            week = week.add(salesByDay.get(day));
            weekOrders += ordersByDay.get(day);
        }
        body.put("salesToday", BillingService.money(salesByDay.get(today)));
        body.put("ordersToday", ordersByDay.get(today));
        body.put("salesThisWeek", BillingService.money(week));
        body.put("ordersThisWeek", weekOrders);
        body.put("daily", daily);

        List<Map<String, Object>> best = qtyByProduct.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("quantity", e.getValue()[0]);
                    m.put("sales", BillingService.money(salesByProduct.get(e.getKey())));
                    return m;
                }).toList();
        body.put("bestSellers", best);

        List<Product> low = productRepository.findLowStock(id, LOW_STOCK);
        body.put("lowStock", low.stream().limit(10).map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("stock", p.getStock());
            return m;
        }).toList());
        body.put("outOfStock", productRepository.countOutOfStock(id));
        body.put("activeProducts", productRepository.countActiveInShop(id));
        return body;
    }
}
