package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the invoice for a delivered order. All the maths is done here on the server, so the
 * printable page only lays it out.
 *
 * Prices already include GST. For each line: the line's share of the coupon discount is taken off,
 * then taxable value = amount x 100 / (100 + GST rate), and GST = amount - taxable value. Inside a state
 * GST is split equally into CGST and SGST. The delivery fee is shown separately without GST.
 */
@Service
public class InvoiceService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Map<String, Object> build(Order order) {
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The invoice is ready once the order is delivered or collected.");
        }
        Shop shop = order.getShop();
        Map<String, Object> inv = new LinkedHashMap<>();
        inv.put("invoiceNumber", "NB-" + (shop == null ? 0 : shop.getId()) + "-" + order.getId());
        inv.put("invoiceDate", (order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getUpdatedAt()).toLocalDate());
        inv.put("orderId", order.getId());
        inv.put("orderDate", order.getCreatedAt().toLocalDate());
        boolean registered = shop != null && shop.getGstin() != null;
        inv.put("title", registered ? "Tax invoice" : "Bill of supply");

        Map<String, Object> seller = new LinkedHashMap<>();
        seller.put("name", shop == null ? "NammaBasket" : shop.getName());
        seller.put("address", shop == null ? null : shop.getAddressLine());
        seller.put("pincode", shop == null ? null : shop.getPincode());
        seller.put("phone", shop == null ? null : shop.getPhone());
        seller.put("gstin", registered ? shop.getGstin() : null);
        inv.put("seller", seller);

        Map<String, Object> buyer = new LinkedHashMap<>();
        buyer.put("name", order.getUser().getName());
        buyer.put("address", order.getShippingAddress());
        buyer.put("pincode", order.getPincode());
        inv.put("buyer", buyer);
        inv.put("fulfilment", order.getFulfilment().name());
        inv.put("paymentMethod", order.getPaymentMethod().name());
        inv.put("paymentStatus", order.getPaymentStatus().name());

        // ---- lines ----
        BigDecimal subtotal = order.getSubtotal();
        BigDecimal discount = order.getDiscount() == null ? BigDecimal.ZERO : order.getDiscount();
        List<OrderItem> items = order.getItems();
        List<Map<String, Object>> lines = new ArrayList<>();
        Map<Integer, BigDecimal[]> byRate = new TreeMap<>(); // rate -> [taxable, gst]
        BigDecimal discountLeft = discount;
        for (int i = 0; i < items.size(); i++) {
            OrderItem it = items.get(i);
            int rate = it.getGstPercent() == null ? 0 : it.getGstPercent();
            BigDecimal amount = it.getPrice().multiply(BigDecimal.valueOf(it.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            // The last line takes what is left, so the shares always add up to the discount exactly.
            BigDecimal share = i == items.size() - 1 || subtotal.signum() == 0 ? discountLeft
                    : discount.multiply(amount).divide(subtotal, 2, RoundingMode.HALF_UP);
            discountLeft = discountLeft.subtract(share);
            BigDecimal net = amount.subtract(share);
            BigDecimal taxable = net.multiply(HUNDRED).divide(HUNDRED.add(BigDecimal.valueOf(rate)), 2, RoundingMode.HALF_UP);
            BigDecimal gst = net.subtract(taxable);

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("name", it.getProductName());
            line.put("quantity", it.getQuantity());
            line.put("unitPrice", it.getPrice());
            line.put("amount", amount);
            line.put("discount", share);
            line.put("gstPercent", rate);
            line.put("taxableValue", taxable);
            line.put("gst", gst);
            lines.add(line);
            BigDecimal[] sums = byRate.computeIfAbsent(rate, r -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(taxable);
            sums[1] = sums[1].add(gst);
        }
        inv.put("lines", lines);

        List<Map<String, Object>> rates = new ArrayList<>();
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        for (var e : byRate.entrySet()) {
            BigDecimal gst = e.getValue()[1];
            BigDecimal cgst = gst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("gstPercent", e.getKey());
            r.put("taxableValue", e.getValue()[0]);
            r.put("cgst", cgst);
            r.put("sgst", gst.subtract(cgst));
            rates.add(r);
            gstTotal = gstTotal.add(gst);
            cgstTotal = cgstTotal.add(cgst);
        }
        inv.put("gstByRate", rates);

        // Totals are the sums of the rows above, so the printed table always adds up.
        BigDecimal cgst = cgstTotal;
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("itemsTotal", subtotal);
        totals.put("discount", discount);
        totals.put("deliveryFee", order.getDeliveryFee());
        totals.put("gstIncluded", gstTotal);
        totals.put("cgst", cgst);
        totals.put("sgst", gstTotal.subtract(cgst));
        totals.put("grandTotal", order.getTotalAmount());
        inv.put("totals", totals);
        inv.put("note", "Prices include GST. " + (registered ? "" : "This shop is not GST-registered, so this is a bill of supply. ")
                + "Generated by NammaBasket, a portfolio project: not a real tax document.");
        return inv;
    }
}
