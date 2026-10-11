package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.ReportRequest;
import com.suryaprasanth.grocery.model.Report;
import com.suryaprasanth.grocery.model.ReportReason;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.ReportService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Buyers reporting a problem with a shop or one of its products. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final UserRepository userRepository;

    public ReportController(ReportService reportService, UserRepository userRepository) {
        this.reportService = reportService;
        this.userRepository = userRepository;
    }

    /** The reasons a buyer can pick, with labels for the form. */
    @GetMapping("/reasons")
    public List<Map<String, String>> reasons() {
        return Arrays.stream(ReportReason.values()).map(r -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("value", r.name());
            m.put("label", r.getLabel());
            return m;
        }).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> report(@Valid @RequestBody ReportRequest request, HttpSession session) {
        User buyer = SessionUtil.requireBuyer(session, userRepository);
        Report report = reportService.create(buyer, request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", report.getId());
        body.put("status", report.getStatus().name());
        body.put("message", "Thanks. Our team will look into it.");
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
