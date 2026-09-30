package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.BulkAddResult;
import com.suryaprasanth.grocery.dto.CartRequest;
import com.suryaprasanth.grocery.model.CartItem;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.CartItemRepository;
import com.suryaprasanth.grocery.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart rules in one place, so "Add" on a product card, "Add all" from a shopping list,
 * a recipe or a festival kit, and "Swap to cheaper" all follow exactly the same checks.
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ShopRules rules;

    public CartService(CartItemRepository cartItemRepository, ProductRepository productRepository,
                       ShopRules rules) {
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.rules = rules;
    }

    public List<CartItem> cartOf(User user) {
        return cartItemRepository.findByUserIdOrderByIdAsc(user.getId());
    }

    public Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    /** Adds to the quantity already in the cart. Throws a 400 with a clear message if not allowed. */
    public CartItem addItem(User user, Product product, int quantity) {
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElse(new CartItem(user, product, 0));
        int newQuantity = item.getQuantity() + quantity;
        checkCanBuy(product, newQuantity);
        item.setQuantity(newQuantity);
        return cartItemRepository.save(item);
    }

    /**
     * Adds many items at once. Items that can't be added (out of stock, expired, too many)
     * are skipped with the reason, and the rest are still added.
     */
    public BulkAddResult addMany(User user, List<CartRequest> requests) {
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (CartRequest request : requests) {
            Product product = productRepository.findById(request.getProductId()).orElse(null);
            if (product == null) {
                skipped.add("Product #" + request.getProductId() + " no longer exists");
                continue;
            }
            try {
                addItem(user, product, request.getQuantity());
                added.add(request.getQuantity() + " x " + product.getName());
            } catch (ResponseStatusException e) {
                skipped.add(e.getReason());
            }
        }
        return new BulkAddResult(added, skipped, cartOf(user));
    }

    /** Replaces one product in the cart with another, keeping the same quantity. */
    public List<CartItem> swap(User user, Long fromProductId, Long toProductId) {
        if (fromProductId.equals(toProductId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick a different product to swap to.");
        }
        CartItem from = cartItemRepository.findByUserIdAndProductId(user.getId(), fromProductId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart"));
        Product to = findProduct(toProductId);
        int quantity = from.getQuantity();
        // Check first, so a failed swap leaves the cart exactly as it was.
        int alreadyInCart = cartItemRepository.findByUserIdAndProductId(user.getId(), to.getId())
                .map(CartItem::getQuantity).orElse(0);
        checkCanBuy(to, alreadyInCart + quantity);
        cartItemRepository.delete(from);
        addItem(user, to, quantity);
        return cartOf(user);
    }

    /** Stops expired items, out-of-stock items and silly quantities entering the cart. */
    public void checkCanBuy(Product product, int quantity) {
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
