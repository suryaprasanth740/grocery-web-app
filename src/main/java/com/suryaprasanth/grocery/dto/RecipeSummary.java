package com.suryaprasanth.grocery.dto;

import java.math.BigDecimal;

/** A recipe or festival kit card on the Recipes & Kits page. */
public record RecipeSummary(String id, String type, String name, String emoji, String description,
                            String time, int baseServings, int ingredientCount,
                            BigDecimal estimatedTotal, PhotoInfo photo) {
}
