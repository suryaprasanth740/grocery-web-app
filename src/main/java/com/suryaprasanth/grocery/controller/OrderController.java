package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.CheckoutRequest;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderItem;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.OrderRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderController(OrderRepository orderRepository, CartItemRepository cartItemRepository,
                            ProductRepository productRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/checkout")
    @Transactional
    public Order checkout(@Valid @RequestBody CheckoutRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        List<CartItem> cartItems = cartItemRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty");
        }

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(request.getShippingAddress().trim());
        order.setStatus("PLACED");

        BigDecimal total = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            if (product.getStock() < cartItem.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Not enough stock for " + product.getName() + ". Only " + product.getStock() + " left.");
            }
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);

            OrderItem orderItem = new OrderItem(order, product.getName(), product.getPrice(), cartItem.getQuantity());
            order.getItems().add(orderItem);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        cartItemRepository.deleteByUserId(user.getId());
        return saved;
    }

    @GetMapping
    public List<Order> myOrders(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This order does not belong to you");
        }
        return order;
    }
}
