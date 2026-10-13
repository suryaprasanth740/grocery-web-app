package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delivery and pickup time slots.
 *
 * Rules (all checked on the server):
 *  - A shop's day is cut into 2-hour slots from opening to closing time (the last one may be shorter,
 *    but at least 1 hour).
 *  - Buyers can book today or tomorrow only.
 *  - A slot closes 60 minutes before it starts, so the shop has time to pack.
 *  - Each slot takes at most the shop's slot capacity of orders; cancelled and failed orders free their place.
 */
@Service
public class SlotService {

    public static final int SLOT_HOURS = 2;
    public static final int CUT_OFF_MINUTES = 60;
    private static final Set<OrderStatus> NOT_COUNTED = EnumSet.of(OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED);
    private static final DateTimeFormatter VALUE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("h:mm a");

    /** One 2-hour window of a shop's day. */
    public record Window(LocalTime start, LocalTime end) {
        public String label() {
            return start.format(LABEL) + " – " + end.format(LABEL);
        }
    }

    /** A slot the buyer booked. */
    public record Chosen(LocalDate date, LocalTime start, LocalTime end) {
    }

    private final OrderRepository orderRepository;

    public SlotService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(Shop.SHOP_ZONE);
    }

    /** The windows of a shop's day, e.g. 7-9, 9-11, ..., 19-21, 21-22. */
    public static List<Window> windows(LocalTime open, LocalTime close) {
        List<Window> list = new ArrayList<>();
        LocalTime start = open;
        while (start.isBefore(close)) {
            LocalTime end = start.plusHours(SLOT_HOURS);
            // plusHours wraps after midnight (23:00 + 2h = 01:00); treat that as the closing time
            if (end.isAfter(close) || !end.isAfter(start)) {
                end = close;
            }
            if (java.time.Duration.between(start, end).toMinutes() >= 60) {
                list.add(new Window(start, end));
            }
            if (!end.isAfter(start) || end.equals(close)) {
                break;
            }
            start = end;
        }
        return list;
    }

    /** Is this window still bookable at "now" (pure rule, no database: easy to test with any time)? */
    public static boolean openForBooking(LocalDate date, Window w, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(1))) {
            return false;
        }
        return now.isBefore(date.atTime(w.start()).minusMinutes(CUT_OFF_MINUTES));
    }

    /** Today's and tomorrow's slots for the checkout page, each with how many places are left. */
    public Map<String, Object> availability(Shop shop) {
        LocalDateTime now = now();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shopId", shop.getId());
        result.put("enabled", shop.isSlotsEnabled());
        List<Map<String, Object>> days = new ArrayList<>();
        if (shop.isSlotsEnabled()) {
            for (int d = 0; d <= 1; d++) {
                LocalDate date = now.toLocalDate().plusDays(d);
                List<Map<String, Object>> slots = new ArrayList<>();
                for (Window w : windows(shop.getOpenTime(), shop.getCloseTime())) {
                    if (!openForBooking(date, w, now)) {
                        continue;
                    }
                    long booked = booked(shop, date, w.start());
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("value", date.atTime(w.start()).format(VALUE));
                    s.put("label", w.label());
                    s.put("placesLeft", Math.max(0, shop.getSlotCapacity() - booked));
                    s.put("full", booked >= shop.getSlotCapacity());
                    slots.add(s);
                }
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", date.toString());
                day.put("label", d == 0 ? "Today" : "Tomorrow");
                day.put("slots", slots);
                days.add(day);
            }
        }
        result.put("days", days);
        return result;
    }

    /**
     * Checks a slot the buyer sent ("2026-10-14T17:00"). Call it while holding the shop's row lock,
     * so the capacity count can't change between the check and the new order being saved.
     */
    public Chosen check(Shop shop, String value) {
        if (!shop.isSlotsEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    shop.getName() + " doesn't offer time slots. Leave the slot empty for the earliest time.");
        }
        LocalDateTime start;
        try {
            start = LocalDateTime.parse(value.trim(), VALUE);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid time slot");
        }
        Window window = windows(shop.getOpenTime(), shop.getCloseTime()).stream()
                .filter(w -> w.start().equals(start.toLocalTime()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That slot is not one of " + shop.getName() + "'s slots"));
        LocalDate date = start.toLocalDate();
        if (!openForBooking(date, window, now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The " + window.label()
                    + " slot can't be booked any more. Slots close " + CUT_OFF_MINUTES
                    + " minutes before they start, and only today and tomorrow can be booked.");
        }
        if (booked(shop, date, window.start()) >= shop.getSlotCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The " + window.label() + " slot at "
                    + shop.getName() + " just got full. Please choose another slot.");
        }
        return new Chosen(date, window.start(), window.end());
    }

    private long booked(Shop shop, LocalDate date, LocalTime start) {
        return orderRepository.countInSlot(shop.getId(), date, start, NOT_COUNTED);
    }
}
