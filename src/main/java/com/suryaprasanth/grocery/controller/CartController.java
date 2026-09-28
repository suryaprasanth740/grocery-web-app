package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.config.ShopRules;
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
    private final ShopRules rules;

    public CartController(CartItemRepository cartItemRepository, ProductRepository productRepository,
                          UserRepository userRepository, ShopRules rules) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.rules = rules;
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
        int newQuantity = item.getQuantity() + request.getQuantity();
        checkCanBuy(product, newQuantity);
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);

        return cartItemRepository.findByUserId(user.getId());
    }

    @PutMapping("/update")
    public List<CartItem> updateQuantity(@Valid @RequestBody CartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        // Lowering the quantity is always allowed (helps the customer fix a stock problem).
        if (request.getQuantity() > item.getQuantity()) {
            checkCanBuy(item.getProduct(), request.getQuantity());
        }
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return cartItemRepository.findByUserId(user.getId());
    }

    /** The customer saw the new price and accepts it. */
    @PostMapping("/accept-prices")
    public List<CartItem> acceptNewPrices(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        List<CartItem> items = cartItemRepository.findByUserId(user.getId());
        for (CartItem item : items) {
            item.setPriceWhenAdded(item.getProduct().getPrice());
        }
        cartItemRepository.saveAll(items);
        return items;
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

    /** Stops expired items, out-of-stock items and silly quantities entering the cart. */
    private void checkCanBuy(Product product, int quantity) {
        if (product.isExpired()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    product.getName() + " is past its best-before date and cannot be sold.");
        }
        if (product.getStock() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, product.getName() + " is out of stock.");
        }
        if (quantity > product.getStock()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only " + product.getStock() + " " + product.getName() + " left in stock.");
        }
        if (quantity > rules.getMaxQtyPerItem()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can buy at most " + rules.getMaxQtyPerItem() + " of one item.");
        }
    }
}
