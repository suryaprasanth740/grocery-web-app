package com.suryaprasanth.grocery.model;

/** What kind of shop it is. Shown to buyers and used for filtering. */
public enum ShopCategory {
    KIRANA("Kirana / general store"),
    SUPERMARKET("Supermarket"),
    FRUITS_VEGETABLES("Fruits & vegetables"),
    DAIRY("Dairy & milk booth"),
    BAKERY("Bakery"),
    ORGANIC("Organic store");

    private final String label;

    ShopCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
