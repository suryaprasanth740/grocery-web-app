package com.suryaprasanth.grocery.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Understands pack sizes such as "1 kg", "500 ml", "1 dozen", "pack of 4" or "1 bunch",
 * and turns them into a common base unit (grams, millilitres, pieces or bunches).
 *
 * Used by the shopping-list reader ("2 kg tomatoes") and by recipes ("200 g toor dal"),
 * so both can work out how many packs to put in the cart.
 */
public final class UnitUtil {

    /** An amount in a base unit: "g", "ml", "pcs" or "bunch". */
    public record Amount(double value, String base) {
    }

    private static final Pattern NUMBER_THEN_UNIT =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([a-z]+)");
    private static final Pattern PACK_OF = Pattern.compile("pack of\\s*(\\d+)");

    private UnitUtil() {
    }

    /** Parses a product's unit text, e.g. "1 kg" -> 1000 g. Returns null if not understood. */
    public static Amount parsePackSize(String unitText) {
        if (unitText == null || unitText.isBlank()) {
            return null;
        }
        String text = unitText.toLowerCase(Locale.ROOT).trim();
        Matcher packOf = PACK_OF.matcher(text);
        if (packOf.find()) {
            return new Amount(Double.parseDouble(packOf.group(1)), "pcs");
        }
        Matcher m = NUMBER_THEN_UNIT.matcher(text);
        if (!m.find()) {
            return null;
        }
        return toBase(Double.parseDouble(m.group(1)), m.group(2));
    }

    /** Converts "2" + "kg" into 2000 g. Returns null for an unknown unit. */
    public static Amount toBase(double value, String unit) {
        if (unit == null) {
            return null;
        }
        String base = baseOf(unit);
        if (base == null) {
            return null;
        }
        return new Amount(value * factorOf(unit), base);
    }

    /** The base unit for a written unit, e.g. "kgs" -> "g", "litre" -> "ml". Null if unknown. */
    public static String baseOf(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "kg", "kgs", "kilo", "kilos", "g", "gm", "gms", "gram", "grams" -> "g";
            case "l", "ltr", "ltrs", "litre", "litres", "liter", "liters", "ml" -> "ml";
            case "pc", "pcs", "piece", "pieces", "bags", "bag", "dozen", "nos", "box", "pack", "packet" -> "pcs";
            case "bunch", "bunches" -> "bunch";
            default -> null;
        };
    }

    private static double factorOf(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "kg", "kgs", "kilo", "kilos", "l", "ltr", "ltrs", "litre", "litres", "liter", "liters" -> 1000;
            case "dozen" -> 12;
            default -> 1;
        };
    }

    /**
     * How many packs cover the amount needed. 1.2 kg of a 1 kg pack -> 2 packs.
     * Returns -1 when the units don't match (e.g. grams vs pieces).
     */
    public static int packsNeeded(Amount needed, Amount packSize) {
        if (needed == null || packSize == null || packSize.value() <= 0
                || !needed.base().equals(packSize.base())) {
            return -1;
        }
        // The small tolerance stops 0.5 + 0.5 rounding errors from asking for an extra pack.
        int packs = (int) Math.ceil(needed.value() / packSize.value() - 1e-9);
        return Math.max(1, packs);
    }

    /** "1500 g" -> "1.5 kg", "250 g" -> "250 g", "3 pcs" -> "3 pcs". For friendly labels. */
    public static String format(Amount amount) {
        if (amount == null) {
            return "";
        }
        double v = amount.value();
        return switch (amount.base()) {
            case "g" -> v >= 1000 ? trim(v / 1000) + " kg" : trim(v) + " g";
            case "ml" -> v >= 1000 ? trim(v / 1000) + " L" : trim(v) + " ml";
            case "bunch" -> trim(v) + (v == 1 ? " bunch" : " bunches");
            default -> trim(v) + " pcs";
        };
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 100) / 100.0);
    }
}
