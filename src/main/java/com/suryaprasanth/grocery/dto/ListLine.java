package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Product;

import java.util.List;

/**
 * One line of a pasted shopping list, as understood by the server.
 *
 * @param input      the line exactly as the customer typed it
 * @param product    best matching product, or null if nothing matched
 * @param quantity   number of packs to add (e.g. "1 litre milk" with 500 ml packs -> 2)
 * @param requested  the quantity that was read from the line, e.g. "2 kg" (null if none)
 * @param confidence "HIGH" when every word was understood, otherwise "LOW" (the page asks to check it)
 * @param options    other close matches the customer can pick instead
 * @param note       a warning such as "Out of stock right now"
 */
public record ListLine(String input, Product product, int quantity, String requested,
                       String confidence, List<Product> options, String note) {
}
