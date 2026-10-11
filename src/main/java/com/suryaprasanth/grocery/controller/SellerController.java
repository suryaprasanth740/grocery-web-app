package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.SellerProductRequest;
import com.suryaprasanth.grocery.dto.ShopDetailsRequest;
import com.suryaprasanth.grocery.model.Product;
import com.suryaprasanth.grocery.model.Shop;
import com.suryaprasanth.grocery.model.StoredImage;
import com.suryaprasanth.grocery.service.ImageService;
import com.suryaprasanth.grocery.service.SellerProductService;
import com.suryaprasanth.grocery.service.SellerDashboardService;
import com.suryaprasanth.grocery.service.OrderService;
import com.suryaprasanth.grocery.dto.ReasonRequest;
import com.suryaprasanth.grocery.dto.StatusUpdateRequest;
import com.suryaprasanth.grocery.model.Order;
import com.suryaprasanth.grocery.model.OrderStatus;
import com.suryaprasanth.grocery.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.util.LinkedHashMap;
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
    private final SellerProductService productService;
    private final ImageService imageService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final SellerDashboardService dashboardService;

    public SellerController(UserRepository userRepository, ShopService shopService,
                            SellerProductService productService, ImageService imageService,
                            OrderService orderService, OrderRepository orderRepository,
                            SellerDashboardService dashboardService) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.dashboardService = dashboardService;
        this.userRepository = userRepository;
        this.shopService = shopService;
        this.productService = productService;
        this.imageService = imageService;
    }

    private Shop myShopEntity(HttpSession session) {
        return shopService.shopOf(SessionUtil.requireSeller(session, userRepository));
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

    // ---------------- Products ----------------

    /** ?q=atta&categoryId=6&sort=name|price|stock|newest&page=0&size=25 */
    @GetMapping("/products")
    public Map<String, Object> products(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) Long categoryId,
                                        @RequestParam(defaultValue = "name") String sort,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "25") int size,
                                        HttpSession session) {
        Page<Product> result = productService.list(myShopEntity(session), q, categoryId, sort, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent());
        body.put("page", result.getNumber());
        body.put("totalPages", result.getTotalPages());
        body.put("totalItems", result.getTotalElements());
        return body;
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody SellerProductRequest request, HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(myShopEntity(session), request));
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody SellerProductRequest request,
                                 HttpSession session) {
        return productService.update(myShopEntity(session), id, request);
    }

    /** Body: {"stock": 25} */
    @PutMapping("/products/{id}/stock")
    public Product setStock(@PathVariable Long id, @RequestBody Map<String, Integer> body, HttpSession session) {
        return productService.setStock(myShopEntity(session), id, body.get("stock"));
    }

    /** Body: {"active": false} hides the product from buyers. */
    @PutMapping("/products/{id}/active")
    public Product setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body, HttpSession session) {
        Boolean active = body.get("active");
        if (active == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Send active: true or false");
        }
        return productService.setActive(myShopEntity(session), id, active);
    }

    /** Upload a product photo or shop logo (form field "file"). Returns {"id": 42, "url": "/api/images/42"}. */
    @PostMapping("/images")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file, HttpSession session) {
        StoredImage image = imageService.store(myShopEntity(session).getId(), file);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", image.getId());
        body.put("url", image.getUrl());
        body.put("sizeBytes", image.getSizeBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------------- Orders (only this shop's) ----------------

    /** ?status=new|active|done|cancelled|all, newest first, 20 per page. */
    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestParam(defaultValue = "all") String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      HttpSession session) {
        Shop shop = myShopEntity(session);
        Set<OrderStatus> statuses = switch (status.toLowerCase()) {
            case "new" -> SellerDashboardService.NEW;
            case "active" -> SellerDashboardService.IN_PROGRESS;
            case "done" -> SellerDashboardService.DONE;
            case "cancelled" -> SellerDashboardService.CANCELLED;
            case "all" -> SellerDashboardService.VISIBLE_TO_SHOP;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be new, active, done, cancelled or all");
        };
        Page<Order> result = orderRepository.findForShop(shop.getId(), statuses,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.getContent());
        body.put("page", result.getNumber());
        body.put("totalPages", result.getTotalPages());
        body.put("totalItems", result.getTotalElements());
        return body;
    }

    @GetMapping("/orders/{id}")
    public Order order(@PathVariable Long id, HttpSession session) {
        Order order = orderService.getShopOrder(myShopEntity(session), id);
        if (!SellerDashboardService.VISIBLE_TO_SHOP.contains(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found in your shop");
        }
        return order;
    }

    /** Body: {"status": "ACCEPTED" | "PREPARING" | "OUT_FOR_DELIVERY" | "READY_FOR_PICKUP" | "DELIVERED"} */
    @PutMapping("/orders/{id}/status")
    public Order setOrderStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request, HttpSession session) {
        return orderService.sellerUpdateStatus(myShopEntity(session), id, request.getStatus());
    }

    /** Turn an order down before preparing it. The buyer sees the reason and their stock/money comes back. */
    @PostMapping("/orders/{id}/reject")
    public Order rejectOrder(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, HttpSession session) {
        return orderService.sellerReject(myShopEntity(session), id, request.getReason());
    }

    /** Dashboard numbers: order counts, sales today/this week, last 7 days, best sellers, low stock. */
    @GetMapping("/summary")
    public Map<String, Object> summary(HttpSession session) {
        return dashboardService.summary(myShopEntity(session));
    }
}
