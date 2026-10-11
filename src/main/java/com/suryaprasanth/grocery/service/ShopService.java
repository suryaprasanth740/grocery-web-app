package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.SellerRegisterRequest;
import com.suryaprasanth.grocery.dto.ShopDetailsRequest;
import com.suryaprasanth.grocery.model.Role;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.ShopStatus;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Shop applications, shop settings, and the admin decisions about shops.
 *
 * Status changes allowed (anything else is refused with 409 Conflict):
 *   PENDING   -> APPROVED  (admin approves)
 *   PENDING   -> REJECTED  (admin rejects, with a reason)
 *   REJECTED  -> PENDING   (owner fixes the details and resubmits)
 *   APPROVED  -> SUSPENDED (admin suspends, with a reason)
 *   SUSPENDED -> APPROVED  (admin reactivates)
 */
@Service
public class ShopService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShopRepository shopRepository;
    private final AccountService accountService;
    private final AuditService auditService;

    public ShopService(ShopRepository shopRepository, AccountService accountService, AuditService auditService) {
        this.shopRepository = shopRepository;
        this.accountService = accountService;
        this.auditService = auditService;
    }

    // ---------------- Owner side ----------------

    /** Creates the SELLER account and its PENDING shop together: both are saved, or neither. */
    @Transactional
    public Shop registerSeller(SellerRegisterRequest request) {
        Shop shop = new Shop();
        applyDetails(shop, request); // check the shop first, so bad shop details create no account
        User owner = accountService.createAccount(request.getOwnerName(), request.getEmail(),
                request.getPassword(), Role.SELLER, request.getPhone());
        shop.setOwner(owner);
        shop.setSlug(uniqueSlug(request.getShopName()));
        shop.setStatus(ShopStatus.PENDING);
        return shopRepository.save(shop);
    }

    @Transactional(readOnly = true)
    public Shop shopOf(User owner) {
        return shopRepository.findByOwnerId(owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You don't have a shop yet"));
    }

    @Transactional
    public Shop updateDetails(User owner, ShopDetailsRequest request) {
        Shop shop = shopOf(owner);
        if (shop.getStatus() == ShopStatus.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your shop is suspended, so its details can't be changed. Contact NammaBasket support.");
        }
        applyDetails(shop, request);
        return shopRepository.save(shop);
    }

    @Transactional
    public Shop resubmit(User owner) {
        Shop shop = shopOf(owner);
        if (shop.getStatus() != ShopStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a rejected application can be sent again");
        }
        shop.setStatus(ShopStatus.PENDING);
        shop.setStatusReason(null);
        return shopRepository.save(shop);
    }

    /** Pause or resume new orders. Only an approved shop can take orders, so only it can toggle. */
    @Transactional
    public Shop setAcceptingOrders(User owner, boolean accepting) {
        Shop shop = shopOf(owner);
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your shop can take orders once an admin approves it");
        }
        shop.setAcceptingOrders(accepting);
        return shopRepository.save(shop);
    }

    // ---------------- Admin side ----------------

    @Transactional(readOnly = true)
    public List<Shop> list(ShopStatus status) {
        return status == null ? shopRepository.findAllByOrderByCreatedAtDesc()
                : shopRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    @Transactional(readOnly = true)
    public Shop get(Long id) {
        return shopRepository.findWithOwnerById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
    }

    @Transactional
    public Shop approve(User admin, Long id) {
        Shop shop = get(id);
        requireStatus(shop, ShopStatus.PENDING, "Only a pending application can be approved");
        shop.setStatus(ShopStatus.APPROVED);
        shop.setStatusReason(null);
        shop.setApprovedAt(LocalDateTime.now());
        auditService.record(admin, "SHOP_APPROVED", "SHOP", shop.getId(), shop.getName());
        return shopRepository.save(shop);
    }

    @Transactional
    public Shop reject(User admin, Long id, String reason) {
        Shop shop = get(id);
        requireStatus(shop, ShopStatus.PENDING, "Only a pending application can be rejected");
        shop.setStatus(ShopStatus.REJECTED);
        shop.setStatusReason(reason.trim());
        auditService.record(admin, "SHOP_REJECTED", "SHOP", shop.getId(), shop.getName() + ": " + reason.trim());
        return shopRepository.save(shop);
    }

    @Transactional
    public Shop suspend(User admin, Long id, String reason) {
        Shop shop = get(id);
        requireStatus(shop, ShopStatus.APPROVED, "Only an approved shop can be suspended");
        shop.setStatus(ShopStatus.SUSPENDED);
        shop.setStatusReason(reason.trim());
        auditService.record(admin, "SHOP_SUSPENDED", "SHOP", shop.getId(), shop.getName() + ": " + reason.trim());
        return shopRepository.save(shop);
    }

    @Transactional
    public Shop reactivate(User admin, Long id) {
        Shop shop = get(id);
        requireStatus(shop, ShopStatus.SUSPENDED, "Only a suspended shop can be reactivated");
        shop.setStatus(ShopStatus.APPROVED);
        shop.setStatusReason(null);
        auditService.record(admin, "SHOP_REACTIVATED", "SHOP", shop.getId(), shop.getName());
        return shopRepository.save(shop);
    }

    // ---------------- Helpers ----------------

    private static void requireStatus(Shop shop, ShopStatus wanted, String message) {
        if (shop.getStatus() != wanted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    message + " (this shop is " + shop.getStatus().name().toLowerCase(Locale.ROOT) + ")");
        }
    }

    /** Copies and checks the owner's input. Rules that span two fields are checked here. */
    void applyDetails(Shop shop, ShopDetailsRequest r) {
        if (!r.getCloseTime().isAfter(r.getOpenTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Closing time must be after opening time");
        }
        if (!r.isOffersDelivery() && !r.isOffersPickup()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer delivery, pickup, or both");
        }
        String pincodes = Shop.normalisePincodes(r.getPincode(), r.getServicePincodes());
        if (pincodes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Delivery PIN codes must be 6 digits (560073) or an area like 560xxx, separated by commas");
        }
        if (pincodes.length() > 400) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many PIN codes. Use areas like 560xxx instead.");
        }
        shop.setName(r.getShopName().trim());
        shop.setCategory(r.getCategory());
        shop.setPhone(r.getPhone());
        shop.setAddressLine(r.getAddressLine().trim());
        shop.setPincode(r.getPincode());
        shop.setServicePincodes(pincodes);
        shop.setOpenTime(r.getOpenTime());
        shop.setCloseTime(r.getCloseTime());
        shop.setOffersDelivery(r.isOffersDelivery());
        shop.setOffersPickup(r.isOffersPickup());
        shop.setDeliveryFee(money(r.getDeliveryFee()));
        shop.setFreeDeliveryAbove(r.getFreeDeliveryAbove() == null ? null : money(r.getFreeDeliveryAbove()));
        shop.setMinOrderAmount(money(r.getMinOrderAmount()));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** "Sri Lakshmi Stores" -> "sri-lakshmi-stores", or "sri-lakshmi-stores-4k2p" if that is taken. */
    String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = "shop";
        if (base.length() > 80) base = base.substring(0, 80);
        String slug = base;
        while (shopRepository.existsBySlug(slug)) {
            slug = base + "-" + Integer.toString(1000 + RANDOM.nextInt(46000), 36);
        }
        return slug;
    }
}
