package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.CartItem;

import java.util.List;

/** Result of adding many items at once: what went in, what was skipped (with the reason), and the new cart. */
public record BulkAddResult(List<String> added, List<String> skipped, List<CartItem> cart) {
}
