package com.suryaprasanth.grocery.service;

import com.suryaprasanth.grocery.model.AccountStatus;
import com.suryaprasanth.grocery.model.Role;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.util.EmailUtil;
import com.suryaprasanth.grocery.util.PasswordUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Creates accounts. Used by both the buyer sign-up and the seller application. */
@Service
public class AccountService {

    public static final String ALREADY_REGISTERED = "An account with this email already exists. Please log in.";

    private final UserRepository userRepository;
    private final AuditService auditService;

    public AccountService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Checks the email and password and saves a new account. ADMIN accounts can never be made here.
     * Throws 400 for bad input and 409 if the email (or the same Gmail inbox) already has an account.
     */
    public User createAccount(String name, String rawEmail, String password, Role role, String phone) {
        if (role == Role.ADMIN) {
            throw new IllegalArgumentException("Admin accounts are only created from environment settings");
        }
        String email = rawEmail.trim().toLowerCase();
        String key = EmailUtil.canonical(email);
        if (!EmailUtil.hasName(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email");
        }
        if (PasswordUtil.tooLong(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too long. Use 72 characters or fewer.");
        }
        // One email inbox = one account. For Gmail, dots and "+anything" are ignored,
        // so s.urya@gmail.com and surya+2@gmail.com count as surya@gmail.com.
        if (userRepository.existsByEmailKey(key) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
        }
        User user = new User(name.trim(), email, PasswordUtil.hash(password));
        user.setRole(role);
        user.setPhone(phone);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the database lets only one in.
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
        }
    }

    /** Admin suspends a buyer or seller. Admin accounts (including your own) can't be suspended here. */
    @Transactional
    public User suspend(User admin, Long userId, String reason) {
        User user = findForAdmin(userId);
        if (user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin accounts can't be suspended");
        }
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is already suspended");
        }
        user.setAccountStatus(AccountStatus.SUSPENDED);
        auditService.record(admin, "ACCOUNT_SUSPENDED", "USER", user.getId(), user.getEmail() + ": " + reason.trim());
        return userRepository.save(user);
    }

    @Transactional
    public User reactivate(User admin, Long userId) {
        User user = findForAdmin(userId);
        if (!user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This account is not suspended");
        }
        user.setAccountStatus(AccountStatus.ACTIVE);
        auditService.record(admin, "ACCOUNT_REACTIVATED", "USER", user.getId(), user.getEmail());
        return userRepository.save(user);
    }

    private User findForAdmin(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}
