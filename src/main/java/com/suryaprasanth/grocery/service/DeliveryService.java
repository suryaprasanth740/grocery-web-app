package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.config.ShopRules;
import com.suryaprasanth.grocery.dto.PincodeCheck;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class DeliveryService {

    /** Indian PIN code: 6 digits, cannot start with 0. */
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");

    private final ShopRules rules;

    public DeliveryService(ShopRules rules) {
        this.rules = rules;
    }

    public PincodeCheck check(String rawPincode) {
        String pincode = rawPincode == null ? "" : rawPincode.trim();
        if (!PINCODE.matcher(pincode).matches()) {
            return new PincodeCheck(pincode, false, false,
                    "Enter a valid 6-digit PIN code (it cannot start with 0).", null);
        }
        boolean serviceable = rules.getPincodePrefixes().stream().anyMatch(pincode::startsWith);
        if (!serviceable) {
            return new PincodeCheck(pincode, true, false,
                    "Sorry, we don't deliver to " + pincode + " yet. We currently deliver only to PIN codes starting with "
                            + String.join(", ", rules.getPincodePrefixes()) + " (Bengaluru).", null);
        }
        return new PincodeCheck(pincode, true, true,
                "Great! We deliver to " + pincode + ".", rules.getDeliveryEta());
    }
}
