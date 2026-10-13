package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.Notification;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-app notifications. They are written in the same transaction as the change they describe,
 * so a buyer is never told "accepted" about an order change that was rolled back.
 */
@Service
public class NotificationService {

    /** How many notifications the bell shows. */
    public static final int SHOWN = 20;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void notify(User user, String title, String body, String link) {
        if (user != null) {
            repository.save(new Notification(user, title, body, link));
        }
    }

    /** Tells the buyer what the shop (or support) just did with their order. */
    public void orderChanged(Order order) {
        String shop = order.getShop() == null ? "The shop" : order.getShop().getName();
        String title = switch (order.getStatus()) {
            case ACCEPTED -> "Order #" + order.getId() + " accepted";
            case PREPARING -> "Order #" + order.getId() + " is being packed";
            case OUT_FOR_DELIVERY -> "Order #" + order.getId() + " is out for delivery";
            case READY_FOR_PICKUP -> "Order #" + order.getId() + " is ready for pickup";
            case DELIVERED -> order.getFulfilment() == com.suryaprasanth.grocery.model.Fulfilment.PICKUP
                    ? "Order #" + order.getId() + " collected" : "Order #" + order.getId() + " delivered";
            case CANCELLED -> "Order #" + order.getId() + " was cancelled";
            default -> "Order #" + order.getId() + " updated";
        };
        String body = order.getStatus() == OrderStatus.CANCELLED && order.getStatusNote() != null
                ? order.getStatusNote()
                : order.getStatus() == OrderStatus.DELIVERED
                ? shop + " marked it done. Rate the items and download the invoice from the order page."
                : shop + " updated your order.";
        notify(order.getUser(), title, body, "/order.html?id=" + order.getId());
    }

    /** Tells the shop owner a new order is waiting (cash orders at once, UPI orders once paid). */
    public void newOrderForShop(Order order) {
        if (order.getShop() == null) {
            return;
        }
        notify(order.getShop().getOwner(), "New order #" + order.getId(),
                order.getItems().size() + " item(s), Rs " + order.getTotalAmount().toPlainString()
                        + (order.getFulfilment() == com.suryaprasanth.grocery.model.Fulfilment.PICKUP ? ", pickup" : ", delivery"),
                "/seller.html#orders");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> latest(User user) {
        List<Map<String, Object>> items = repository.latestFor(user.getId(), PageRequest.of(0, SHOWN)).stream()
                .map(n -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", n.getId());
                    m.put("title", n.getTitle());
                    m.put("body", n.getBody());
                    m.put("link", n.getLink());
                    m.put("read", n.isRead());
                    m.put("createdAt", n.getCreatedAt());
                    return m;
                }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unread", repository.countUnread(user.getId()));
        result.put("items", items);
        return result;
    }

    @Transactional
    public void markRead(User user, Long id) {
        // Someone else's notification answers 404, the same as one that doesn't exist.
        Notification n = repository.findOwned(id, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
    }

    @Transactional
    public int markAllRead(User user) {
        return repository.markAllRead(user.getId());
    }
}
