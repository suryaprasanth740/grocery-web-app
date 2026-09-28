package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.PincodeCheck;
import com.suryaprasanth.grocery.service.DeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** "Do you deliver to my PIN code?" - asked BEFORE checkout, not at the last step. */
    @GetMapping("/check")
    public PincodeCheck check(@RequestParam(defaultValue = "") String pincode) {
        return deliveryService.check(pincode);
    }
}
