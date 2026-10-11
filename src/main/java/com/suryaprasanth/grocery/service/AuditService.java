package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.AuditLog;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** Writes the audit trail for sensitive admin actions. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Joins the caller's transaction, so if the action rolls back, its audit row does too. */
    public void record(User actor, String action, String targetType, Long targetId, String details) {
        repository.save(new AuditLog(actor, action, targetType, targetId, details));
    }

    public List<AuditLog> latest(int limit) {
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, Math.max(1, Math.min(limit, 500))));
    }

    public List<AuditLog> forTarget(String targetType, Long targetId) {
        return repository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }
}
