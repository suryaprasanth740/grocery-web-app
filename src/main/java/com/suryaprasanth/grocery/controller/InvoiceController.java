package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.InvoiceService;
import com.suryaprasanth.grocery.service.OrderService;
import com.suryaprasanth.grocery.service.ShopService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * The invoice of a delivered order, for the three people who may see it: the buyer who ordered,
 * the shop that sold it, and admins. Everyone else gets the same answer as when opening the order itself
 * (another buyer 403, another shop 404).
 */
@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final OrderService orderService;
    private final ShopService shopService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public InvoiceController(InvoiceService invoiceService, OrderService orderService, ShopService shopService,
                             OrderRepository orderRepository, UserRepository userRepository) {
        this.invoiceService = invoiceService;
        this.orderService = orderService;
        this.shopService = shopService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/orders/{id}/invoice")
    public Map<String, Object> forBuyer(@PathVariable Long id, HttpSession session) {
        return invoiceService.build(orderService.getOwnedOrder(SessionUtil.requireBuyer(session, userRepository), id));
    }

    @GetMapping("/api/seller/orders/{id}/invoice")
    public Map<String, Object> forSeller(@PathVariable Long id, HttpSession session) {
        var shop = shopService.shopOf(SessionUtil.requireSeller(session, userRepository));
        return invoiceService.build(orderService.getShopOrder(shop, id));
    }

    @GetMapping("/api/admin/orders/{id}/invoice")
    public Map<String, Object> forAdmin(@PathVariable Long id, HttpSession session) {
        SessionUtil.requireAdmin(session, userRepository);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        return invoiceService.build(order);
    }
}
