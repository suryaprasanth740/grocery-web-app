package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.dto.ReportRequest;
import com.suryaprasanth.grocery.dto.ResolveReportRequest;
import com.suryaprasanth.grocery.model.*;
import com.suryaprasanth.grocery.repository.ProductRepository;
import com.suryaprasanth.grocery.repository.ReportRepository;
import com.suryaprasanth.grocery.repository.ShopRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** Buyers report shops or products; admins investigate and close the report, optionally acting on it. */
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final ShopService shopService;
    private final AuditService auditService;

    public ReportService(ReportRepository reportRepository, ShopRepository shopRepository,
                         ProductRepository productRepository, ShopService shopService, AuditService auditService) {
        this.reportRepository = reportRepository;
        this.shopRepository = shopRepository;
        this.productRepository = productRepository;
        this.shopService = shopService;
        this.auditService = auditService;
    }

    @Transactional
    public Report create(User buyer, ReportRequest r) {
        Shop shop = shopRepository.findById(r.getShopId())
                .filter(Shop::isVisibleToBuyers)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        Product product = null;
        if (r.getProductId() != null) {
            product = productRepository.findById(r.getProductId())
                    .filter(p -> p.getShop() != null && p.getShop().getId().equals(shop.getId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That product isn't sold by this shop"));
        }
        // One open report per buyer per shop/product: stops the same complaint being sent many times.
        if (reportRepository.countOpenReports(buyer.getId(), shop.getId(), r.getProductId(), ReportStatus.OPEN) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You've already reported this. Our team will look into it.");
        }
        Report report = new Report();
        report.setReporter(buyer);
        report.setShop(shop);
        report.setProduct(product);
        report.setReason(r.getReason());
        report.setDetails(r.getDetails() == null || r.getDetails().isBlank() ? null : r.getDetails().trim());
        return reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<Report> list(ReportStatus status) {
        return status == null ? reportRepository.findAllByOrderByCreatedAtDesc()
                : reportRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    /** Closes an open report. HIDE_PRODUCT switches the product off; SUSPEND_SHOP suspends the shop. */
    @Transactional
    public Report resolve(User admin, Long reportId, ResolveReportRequest r) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This report is already closed");
        }
        if (r.getStatus() == ReportStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Close the report as RESOLVED or DISMISSED");
        }
        if (r.getStatus() == ReportStatus.DISMISSED && r.getAction() != ResolveReportRequest.Action.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A dismissed report can't come with an action");
        }
        String note = r.getNote().trim();
        switch (r.getAction()) {
            case HIDE_PRODUCT -> {
                Product product = report.getProduct();
                if (product == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This report is about a shop, not a product");
                }
                product.setActive(false);
                productRepository.save(product);
                auditService.record(admin, "PRODUCT_HIDDEN", "PRODUCT", product.getId(),
                        product.getName() + " (report #" + report.getId() + "): " + note);
            }
            case SUSPEND_SHOP -> shopService.suspend(admin, report.getShop().getId(), "Report #" + report.getId() + ": " + note);
            default -> { }
        }
        report.setStatus(r.getStatus());
        report.setResolutionNote(note);
        report.setResolvedBy(admin.getEmail());
        report.setResolvedAt(LocalDateTime.now());
        auditService.record(admin, "REPORT_" + r.getStatus().name(), "REPORT", report.getId(),
                report.shopName() + (report.getProduct() != null ? " / " + report.productName() : "") + ": " + note);
        return reportRepository.save(report);
    }
}
