package com.suryaprasanth.grocery.dto;

import com.suryaprasanth.grocery.model.Product;

import java.math.BigDecimal;
import java.util.List;

/**
 * A recipe or festival kit, worked out for a number of people.
 *
 * @param lines          one line per ingredient: how much is needed and how many packs that is
 * @param estimatedTotal price of all the packs that can be bought right now
 * @param missingCount   ingredients that are out of stock / not sold
 */
public record RecipeView(String id, String type, String name, String emoji, String description,
                         String time, int baseServings, int servings, List<Line> lines,
                         BigDecimal estimatedTotal, int missingCount) {

    /**
     * @param needed   e.g. "300 g" (already scaled for the number of people)
     * @param product  the product to buy, or null if the shop doesn't sell it right now
     * @param packs    how many packs of that product cover what is needed
     * @param lineTotal price x packs
     */
    public record Line(String ingredient, String needed, Product product, int packs,
                       BigDecimal lineTotal, boolean available, boolean pantryStaple) {
    }
}
