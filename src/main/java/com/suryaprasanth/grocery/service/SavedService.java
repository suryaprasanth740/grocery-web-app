package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.SavedItem;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.SavedItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Saved for later": products a buyer wants to remember, and moving them to and from the cart.
 * Moving is all-or-nothing: if the cart refuses the item (out of stock, shop paused...), it stays saved.
 */
@Service
public class SavedService {

    /** A buyer can keep at most this many saved products. */
    public static final int MAX_SAVED = 100;

    private final SavedItemRepository savedRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;

    public SavedService(SavedItemRepository savedRepository, ProductRepository productRepository,
                        CartItemRepository cartItemRepository, CartService cartService) {
        this.savedRepository = savedRepository;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartService = cartService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(User user) {
        return savedRepository.forUser(user.getId()).stream().map(SavedService::view).toList();
    }

    /** Returns true when newly saved, false when it was already saved (saving is idempotent). */
    @Transactional
    public boolean save(User user, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(Product::isBuyable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        if (savedRepository.find(user.getId(), productId).isPresent()) {
            return false;
        }
        if (savedRepository.countForUser(user.getId()) >= MAX_SAVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You can save up to " + MAX_SAVED + " products. Remove some first.");
        }
        savedRepository.save(new SavedItem(user, product));
        return true;
    }

    @Transactional
    public void remove(User user, Long productId) {
        SavedItem item = savedRepository.find(user.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This product is not in your saved list"));
        savedRepository.delete(item);
    }

    /** Saved -> cart (quantity 1, or +1 if already in the cart). The cart's normal rules apply. */
    @Transactional
    public void moveToCart(User user, Long productId) {
        SavedItem item = savedRepository.find(user.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This product is not in your saved list"));
        cartService.addItem(user, item.getProduct(), 1); // throws 400 with the reason if not allowed
        savedRepository.delete(item);
    }

    /** Cart -> saved: the item leaves the cart and is remembered for later. */
    @Transactional
    public void saveFromCart(User user, Long productId) {
        CartItem cartItem = cartItemRepository.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        if (savedRepository.find(user.getId(), productId).isEmpty()) {
            if (savedRepository.countForUser(user.getId()) >= MAX_SAVED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "You can save up to " + MAX_SAVED + " products. Remove some first.");
            }
            savedRepository.save(new SavedItem(user, cartItem.getProduct()));
        }
        cartItemRepository.delete(cartItem);
    }

    private static Map<String, Object> view(SavedItem s) {
        Product p = s.getProduct();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("savedAt", s.getCreatedAt());
        m.put("product", p);
        boolean available = p.isBuyable() && p.isShopAcceptingOrders() && !p.isExpired() && p.getStock() > 0;
        m.put("available", available);
        m.put("reason", available ? null
                : !p.isBuyable() ? "No longer sold"
                : !p.isShopAcceptingOrders() ? p.getShopName() + " isn't taking orders right now"
                : p.isExpired() ? "Past its best-before date" : "Out of stock");
        return m;
    }
}
