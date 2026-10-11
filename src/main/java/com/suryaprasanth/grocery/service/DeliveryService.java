package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.PincodeCheck;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * "Do you deliver to my PIN code?" Each shop chooses its own delivery PIN codes, so the answer
 * is yes when at least one approved shop that offers delivery covers it.
 */
@Service
public class DeliveryService {

    /** Indian PIN code: 6 digits, cannot start with 0. */
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");

    private final ShopRepository shopRepository;

    public DeliveryService(ShopRepository shopRepository) {
        this.shopRepository = shopRepository;
    }

    @Transactional(readOnly = true)
    public PincodeCheck check(String rawPincode) {
        String pincode = rawPincode == null ? "" : rawPincode.trim();
        if (!PINCODE.matcher(pincode).matches()) {
            return new PincodeCheck(pincode, false, false,
                    "Enter a valid 6-digit PIN code (it cannot start with 0).", null);
        }
        long shops = shopRepository.findByStatusOrderByCreatedAtAsc(ShopStatus.APPROVED).stream()
                .filter(Shop::isVisibleToBuyers)
                .filter(Shop::isOffersDelivery)
                .filter(s -> s.servesPincode(pincode))
                .count();
        if (shops == 0) {
            return new PincodeCheck(pincode, true, false,
                    "Sorry, shops on NammaBasket don't deliver to " + pincode + " yet. You can still pick up from a shop.", null);
        }
        return new PincodeCheck(pincode, true, true,
                shops == 1 ? "1 shop delivers to " + pincode + "." : shops + " shops deliver to " + pincode + ".",
                null);
    }
}
