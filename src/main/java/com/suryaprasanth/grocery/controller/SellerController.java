package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.ShopDetailsRequest;
import com.suryaprasanth.grocery.dto.ShopView;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.ShopService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * The seller dashboard API. Everything here needs a SELLER login, and always works on
 * the logged-in seller's OWN shop: there is no shop id in these URLs, so one seller can
 * never reach another seller's shop by changing a number.
 */
@RestController
@RequestMapping("/api/seller")
public class SellerController {

    private final UserRepository userRepository;
    private final ShopService shopService;

    public SellerController(UserRepository userRepository, ShopService shopService) {
        this.userRepository = userRepository;
        this.shopService = shopService;
    }

    @GetMapping("/shop")
    public ShopView myShop(HttpSession session) {
        User seller = SessionUtil.requireSeller(session, userRepository);
        return ShopView.of(shopService.shopOf(seller), true);
    }

    @PutMapping("/shop")
    public ShopView updateShop(@Valid @RequestBody ShopDetailsRequest request, HttpSession session) {
        User seller = SessionUtil.requireSeller(session, userRepository);
        return ShopView.of(shopService.updateDetails(seller, request), true);
    }

    /** After a rejection: send the (fixed) application to the admins again. */
    @PostMapping("/shop/resubmit")
    public ShopView resubmit(HttpSession session) {
        User seller = SessionUtil.requireSeller(session, userRepository);
        return ShopView.of(shopService.resubmit(seller), true);
    }

    /** Body: {"acceptingOrders": false} to pause new orders, true to resume. */
    @PutMapping("/shop/accepting")
    public ShopView setAccepting(@RequestBody Map<String, Boolean> body, HttpSession session) {
        User seller = SessionUtil.requireSeller(session, userRepository);
        Boolean accepting = body.get("acceptingOrders");
        if (accepting == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send acceptingOrders: true or false");
        }
        return ShopView.of(shopService.setAcceptingOrders(seller, accepting), true);
    }
}
