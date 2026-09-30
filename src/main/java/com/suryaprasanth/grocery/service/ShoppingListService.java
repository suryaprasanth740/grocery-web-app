package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.ListLine;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.util.UnitUtil;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Paste your list, get a cart."
 *
 * Reads a shopping list typed the way people really write it, for example
 *   "2 kg tomato, 1 litre milk, atta 5kg, dozen eggs, thakkali, 2 packet doodh"
 * and matches every line to a product with the right number of packs.
 *
 * It understands quantities (2 kg, 500 g, 1 litre, half kg, dozen, 2 packets),
 * common Indian-language words (Hindi, Kannada, Tamil, Telugu, in English letters),
 * plurals and small spelling mistakes.
 */
@Service
public class ShoppingListService {

    public static final int MAX_LINES = 40;
    private static final int MAX_PACKS = 20;

    /** Everyday words (in English letters) -> product name. */
    private static final Map<String, String> SYNONYMS = new HashMap<>();

    static {
        synonym("Tomatoes", "tamatar", "tamato", "thakkali", "tameta", "tomato");
        synonym("Onions", "pyaz", "pyaaz", "piyaz", "eerulli", "ulli", "vengayam", "kanda", "ullipaya", "onion");
        synonym("Potatoes", "aloo", "alu", "batata", "aalugadde", "urulai", "urulaikizhangu", "bangaladumpa", "potato");
        synonym("Full Cream Milk", "doodh", "dudh", "haalu", "paal", "paalu", "milk");
        synonym("Farm Eggs", "anda", "ande", "motte", "muttai", "guddu", "egg");
        synonym("Sona Masoori Rice", "chawal", "chaval", "akki", "arisi", "biyyam", "rice");
        synonym("Toor Dal", "dal", "daal", "dhal", "bele", "paruppu", "pappu", "tuvar", "toor", "arhar");
        synonym("Whole Wheat Atta", "atta", "aata", "gehu", "godhi", "wheat");
        synonym("Iodized Salt", "namak", "uppu", "salt");
        synonym("Sunflower Cooking Oil", "tel", "enne", "ennai", "nune", "oil");
        synonym("Fresh Bananas", "kela", "kele", "balehannu", "vazhaipazham", "arati", "banana");
        synonym("Red Apples", "seb", "sebu", "apple");
        synonym("Fresh Oranges", "santra", "santre", "kittale", "orange");
        synonym("Sugar", "chini", "cheeni", "sakkare", "sakkarai", "panchadara");
        synonym("Jaggery", "gud", "gur", "bella", "vellam", "bellam");
        synonym("Fresh Curd", "dahi", "mosaru", "thayir", "perugu", "curd");
        synonym("Spinach Bunch", "palak", "keerai", "palakura", "spinach");
        synonym("Ghee", "ghi", "tuppa", "nei", "neyyi");
        synonym("Assam Tea Powder", "chai", "chaha", "teapowder");
        synonym("Filter Coffee Powder", "kaapi", "kapi", "coffee");
        synonym("Green Chillies", "mirchi", "menasinakai", "milagai", "mirapakaya", "chilli", "chili", "chilly");
        synonym("Coriander Leaves", "dhaniya", "dhania", "kothamalli", "kottambari", "kothimeera", "coriander");
        synonym("Ginger", "adrak", "adrakh", "shunti", "inji", "allam");
        synonym("Lemons", "nimbu", "limbu", "nimbe", "elumichai", "nimmakaya", "lemon");
        synonym("Fresh Coconut", "nariyal", "tengina", "thengai", "kobbari", "coconut");
        synonym("Carrots", "gajar", "carrot");
        synonym("Bombay Rava", "rava", "sooji", "suji", "semolina");
        synonym("Tamarind", "imli", "hunase", "puli", "chintapandu");
        synonym("Turmeric Powder", "haldi", "arishina", "manjal", "pasupu", "turmeric");
        synonym("Mustard Seeds", "rai", "sasive", "kadugu", "avalu", "mustard");
        synonym("Cardamom", "elaichi", "elakki", "elakkai", "yelakulu");
        synonym("Cashews", "kaju", "godambi", "mundhiri", "cashew");
        synonym("Whole Wheat Bread", "pav", "bread");
        synonym("Digestive Biscuits", "biscuit", "biscuits");
        synonym("Potato Chips", "chips");
        synonym("Paneer", "paneer", "panir");
        synonym("Moong Dal", "moong", "mung", "hesaru", "pasi", "pesara");
    }

    private static void synonym(String productName, String... words) {
        for (String word : words) {
            SYNONYMS.put(word, productName);
        }
    }

    /** Words that don't help find the product. */
    private static final Set<String> STOPWORDS = Set.of(
            "of", "the", "a", "an", "some", "fresh", "for", "and", "i", "need", "buy", "get", "please",
            "want", "x", "pack", "packs", "packet", "packets", "pkt", "pkts", "bottle", "bottles",
            "loaf", "loaves", "nos", "pc", "pcs", "piece", "pieces", "bunch", "bunches", "kg", "kgs",
            "g", "gm", "gms", "gram", "grams", "l", "ltr", "litre", "litres", "liter", "liters", "ml",
            "kilo", "kilos", "dozen", "more", "also", "little", "big", "small");

    /** Units that mean "this many packs" rather than a weight or volume. */
    private static final Set<String> PACK_WORDS = Set.of(
            "pack", "packs", "packet", "packets", "pkt", "pkts", "bottle", "bottles", "loaf", "loaves",
            "box", "boxes", "tin", "tins", "bag");

    private static final Map<String, Double> NUMBER_WORDS = Map.ofEntries(
            Map.entry("half", 0.5), Map.entry("quarter", 0.25), Map.entry("one", 1.0),
            Map.entry("two", 2.0), Map.entry("three", 3.0), Map.entry("four", 4.0),
            Map.entry("five", 5.0), Map.entry("six", 6.0), Map.entry("seven", 7.0),
            Map.entry("eight", 8.0), Map.entry("nine", 9.0), Map.entry("ten", 10.0));

    // A quantity: "2", "1.5", "1/2", "half" or "two", optionally followed by a unit ("2kg", "2 packets").
    private static final Pattern QUANTITY = Pattern.compile(
            "(?<![a-z])(\\d+/\\d+|\\d+(?:\\.\\d+)?|half|quarter|one|two|three|four|five|six|seven|eight|nine|ten)"
                    + "\\s*(kgs?|kilos?|grams?|gms?|gm|g|ltrs?|litres?|liters?|l|ml|pcs|pc|pieces?|"
                    + "packets?|packs?|pkts?|bottles?|loaf|loaves|box(?:es)?|tins?|bag|bunch(?:es)?|dozen|nos)?(?![a-z])");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•]+|\\d+[.)])\\s+");

    private final ProductRepository productRepository;

    public ShoppingListService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Reads the whole list. Each line of the result says what was understood. */
    public List<ListLine> parse(String text) {
        List<ListLine> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        List<Product> catalog = productRepository.findAll(Sort.by("id"));
        for (String raw : text.split("[\\n,;]+")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (result.size() >= MAX_LINES) {
                break;
            }
            result.add(parseLine(line, catalog));
        }
        return result;
    }

    ListLine parseLine(String original, List<Product> catalog) {
        String line = BULLET.matcher(original.toLowerCase(Locale.ROOT)).replaceFirst("");

        // 1. Find the quantity ("2 kg"), then remove it so only the product words are left.
        Double number = null;
        String unit = null;
        Matcher m = QUANTITY.matcher(line);
        if (m.find()) {
            number = toNumber(m.group(1));
            unit = m.group(2);
            line = (line.substring(0, m.start()) + " " + line.substring(m.end())).trim();
        } else if (line.matches(".*\\bdozen\\b.*")) {
            number = 1.0;
            unit = "dozen";
        }

        // 2. Score every product against the remaining words.
        List<String> words = new ArrayList<>();
        for (String w : line.split("[^a-z]+")) {
            if (w.length() >= 2 && !STOPWORDS.contains(w)) {
                words.add(w);
            }
        }
        List<Scored> scored = new ArrayList<>();
        if (!words.isEmpty()) {
            String wantedBase = unit == null ? null : UnitUtil.baseOf(unit);
            for (Product p : catalog) {
                double score = score(words, p);
                if (score > 0) {
                    // "2 kg oranges": prefer a product sold by weight over a juice sold by the litre.
                    UnitUtil.Amount pack = UnitUtil.parsePackSize(p.getUnit());
                    if (wantedBase != null && pack != null && wantedBase.equals(pack.base())) {
                        score += 0.2;
                    }
                    scored.add(new Scored(p, score));
                }
            }
        }
        // Best score first; on a tie, a product you can actually buy, then the older product.
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(s -> !isBuyable(s.product()))
                .thenComparing(s -> s.product().getId()));

        if (scored.isEmpty()) {
            return new ListLine(original.trim(), null, 0, null, "LOW", List.of(),
                    "Couldn't find this item. Try a simpler name, like \"milk\" or \"rice\".");
        }

        Product best = scored.get(0).product();
        int packs = packsFor(number, unit, best);
        String requested = describeRequest(number, unit);
        // Confident when every word was understood (score is at least 1 per word).
        String confidence = scored.get(0).score() >= words.size() ? "HIGH" : "LOW";
        List<Product> options = scored.stream().limit(4).map(Scored::product).toList();
        String note = null;
        if (!isBuyable(best)) {
            note = best.getStock() <= 0 ? "Out of stock right now" : "Not available (expiry)";
        }
        return new ListLine(original.trim(), best, packs, requested, confidence, options, note);
    }

    /** How well the words match a product. Each word counts once: synonym 2, name word 1.5, typo 1. */
    private double score(List<String> words, Product p) {
        List<String> nameWords = new ArrayList<>();
        for (String w : p.getName().toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (!w.isEmpty()) {
                nameWords.add(singular(w));
            }
        }
        double total = 0;
        Set<String> nameWordsMatched = new HashSet<>();
        for (String word : words) {
            String single = singular(word);
            double best = 0;
            String synonymFor = SYNONYMS.getOrDefault(word, SYNONYMS.get(single));
            if (p.getName().equalsIgnoreCase(synonymFor)) {
                best = 2;
            }
            for (String nameWord : nameWords) {
                if (nameWord.equals(single)) {
                    best = Math.max(best, 1.5);
                    nameWordsMatched.add(nameWord);
                } else if (single.length() >= 5 && nameWord.length() >= 5 && editDistance(single, nameWord) <= 1) {
                    best = Math.max(best, 1.0);
                    nameWordsMatched.add(nameWord);
                }
            }
            total += best;
        }
        if (total == 0) {
            return 0;
        }
        // Small tie-breaker: "potatoes" should pick Potatoes (1 of 1 name words matched),
        // not Potato Chips (1 of 2).
        return total + 0.1 * nameWordsMatched.size() / nameWords.size();
    }

    /** Turns "2 kg" of a "1 kg" product into 2 packs, "1 litre" of "500 ml" into 2, "12 eggs" into 1. */
    static int packsFor(Double number, String unit, Product product) {
        if (number == null || number <= 0) {
            return 1;
        }
        UnitUtil.Amount pack = UnitUtil.parsePackSize(product.getUnit());
        int packs;
        if (unit != null && PACK_WORDS.contains(unit)) {
            packs = (int) Math.ceil(number);
        } else if (unit != null && UnitUtil.baseOf(unit) != null) {
            int fromUnits = UnitUtil.packsNeeded(UnitUtil.toBase(number, unit), pack);
            packs = fromUnits > 0 ? fromUnits : (int) Math.ceil(number);
        } else if (pack != null && "pcs".equals(pack.base()) && pack.value() > 1) {
            // "12 eggs" when eggs come in a box of 12 -> 1 box
            packs = UnitUtil.packsNeeded(new UnitUtil.Amount(number, "pcs"), pack);
        } else {
            packs = (int) Math.ceil(number);
        }
        return Math.min(MAX_PACKS, Math.max(1, packs));
    }

    private static String describeRequest(Double number, String unit) {
        if (number == null) {
            return null;
        }
        String n = number == Math.rint(number) ? String.valueOf(number.longValue()) : String.valueOf(number);
        return unit == null ? n : n + " " + unit;
    }

    private static Double toNumber(String text) {
        if (NUMBER_WORDS.containsKey(text)) {
            return NUMBER_WORDS.get(text);
        }
        if (text.contains("/")) {
            String[] parts = text.split("/");
            double bottom = Double.parseDouble(parts[1]);
            return bottom == 0 ? 1.0 : Double.parseDouble(parts[0]) / bottom;
        }
        return Double.parseDouble(text);
    }

    private static boolean isBuyable(Product p) {
        return p.getStock() != null && p.getStock() > 0 && !p.isExpired();
    }

    /** "tomatoes" -> "tomato", "chillies" -> "chilli", "eggs" -> "egg". */
    static String singular(String w) {
        if (w.length() > 4 && w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "i";
        }
        if (w.length() > 4 && w.endsWith("oes")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** Number of single-letter changes between two words (for spelling mistakes). */
    static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private record Scored(Product product, double score) {
    }
}
