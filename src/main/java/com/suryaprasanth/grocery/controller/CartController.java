package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.BulkAddResult;
import com.suryaprasanth.grocery.dto.BulkCartRequest;
import com.suryaprasanth.grocery.dto.CartRequest;
import com.suryaprasanth.grocery.dto.SwapRequest;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.CartService;
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
    private final UserRepository userRepository;
    private final CartService cartService;

    public CartController(CartItemRepository cartItemRepository, UserRepository userRepository,
                          CartService cartService) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
    }

    @GetMapping
    public List<CartItem> viewCart(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return cartService.cartOf(user);
    }

    @PostMapping("/add")
    public List<CartItem> addToCart(@Valid @RequestBody CartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        Product product = cartService.findProduct(request.getProductId());
        cartService.addItem(user, product, request.getQuantity());
        return cartService.cartOf(user);
    }

    /** "Add all to cart" from a pasted shopping list, a recipe or a festival kit. */
    @PostMapping("/add-many")
    public BulkAddResult addMany(@Valid @RequestBody BulkCartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return cartService.addMany(user, request.getItems());
    }

    /** Budget mode: replace an item with a cheaper one, same quantity. */
    @PostMapping("/swap")
    public List<CartItem> swap(@Valid @RequestBody SwapRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return cartService.swap(user, request.getFromProductId(), request.getToProductId());
    }

    @PutMapping("/update")
    public List<CartItem> updateQuantity(@Valid @RequestBody CartRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        // Lowering the quantity is always allowed (helps the customer fix a stock problem).
        if (request.getQuantity() > item.getQuantity()) {
            cartService.checkCanBuy(item.getProduct(), request.getQuantity());
        }
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return cartService.cartOf(user);
    }

    /** The customer saw the new price and accepts it. */
    @PostMapping("/accept-prices")
    public List<CartItem> acceptNewPrices(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        List<CartItem> items = cartService.cartOf(user);
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
        return cartService.cartOf(user);
    }

    @DeleteMapping("/clear")
    public List<CartItem> clearCart(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        cartItemRepository.deleteByUserId(user.getId());
        return List.of();
    }
}
