package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.CartRequest;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartController(CartItemRepository cartItemRepository, ProductRepository productRepository,
                           UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<CartItem> viewCart(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return cartItemRepository.findByUserId(user.getId());
    }

    @PostMapping("/add")
    public List<CartItem> addToCart(@Valid @RequestBody CartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElse(new CartItem(user, product, 0));
        item.setQuantity(item.getQuantity() + request.getQuantity());
        cartItemRepository.save(item);

        return cartItemRepository.findByUserId(user.getId());
    }

    @PutMapping("/update")
    public List<CartItem> updateQuantity(@Valid @RequestBody CartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return cartItemRepository.findByUserId(user.getId());
    }

    @DeleteMapping("/remove/{productId}")
    public List<CartItem> removeItem(@PathVariable Long productId, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        cartItemRepository.delete(item);
        return cartItemRepository.findByUserId(user.getId());
    }

    @DeleteMapping("/clear")
    public List<CartItem> clearCart(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        cartItemRepository.deleteByUserId(user.getId());
        return List.of();
    }
}
