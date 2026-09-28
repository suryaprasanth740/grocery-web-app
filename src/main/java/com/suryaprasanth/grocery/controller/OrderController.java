package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.Bill;
import com.suryaprasanth.grocery.dto.CheckoutRequest;
import com.suryaprasanth.grocery.dto.IssueRequest;
import com.suryaprasanth.grocery.dto.PaymentRequest;
import com.suryaprasanth.grocery.dto.QuoteRequest;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.OrderService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService, OrderRepository orderRepository, UserRepository userRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /** The bill for the current cart, calculated on the server (used by cart + checkout pages). */
    @PostMapping("/quote")
    public Bill quote(@Valid @RequestBody(required = false) QuoteRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderService.quote(user, request == null ? null : request.getCouponCode());
    }

    @PostMapping("/checkout")
    public Order checkout(@Valid @RequestBody CheckoutRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        try {
            return orderService.placeOrder(user, request);
        } catch (RuntimeException ex) {
            // Double click: the first request created the order while this one was waiting.
            // Give back the same order instead of an error.
            Optional<Order> alreadyPlaced = orderRepository.findByUserIdAndRequestId(user.getId(), request.getRequestId());
            if (alreadyPlaced.isPresent()) {
                return alreadyPlaced.get();
            }
            throw ex;
        }
    }

    @GetMapping
    public List<Order> myOrders(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderService.getOwnedOrder(user, id);
    }

    /** Result from the demo UPI screen (SUCCESS or FAILED). */
    @PostMapping("/{id}/pay")
    public Order pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderService.pay(user, id, "SUCCESS".equals(request.getOutcome()));
    }

    @PostMapping("/{id}/cancel")
    public Order cancel(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderService.cancelByCustomer(user, id);
    }

    /** Report a missing / damaged / expired item after delivery. */
    @PostMapping("/{id}/issues")
    public Order reportIssue(@PathVariable Long id, @Valid @RequestBody IssueRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderService.reportIssue(user, id, request);
    }
}
