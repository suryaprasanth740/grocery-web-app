package com.suryaprasanth.grocery.util;

import com.suryaprasanth.grocery.model.Role;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Who is calling the API, and are they allowed to?
 *
 * Every protected endpoint starts with one of these calls, so the check happens on the
 * SERVER. Hiding a button in the browser is never treated as security.
 *
 *   401 = not logged in (or the session expired)
 *   403 = logged in, but this account may not do this (wrong role, or suspended)
 */
public final class SessionUtil {

    public static final String USER_ID = "userId";

    private SessionUtil() {
    }

    /** Any logged-in, non-suspended account. */
    public static User requireLoggedInUser(HttpSession session, UserRepository userRepository) {
        Object rawId = session.getAttribute(USER_ID);
        if (!(rawId instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first"));
        if (user.isSuspended()) {
            session.invalidate();
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account is suspended. Please contact NammaBasket support.");
        }
        return user;
    }

    /** Logged in AND has the given role, otherwise 403. */
    public static User requireRole(HttpSession session, UserRepository userRepository, Role role, String message) {
        User user = requireLoggedInUser(session, userRepository);
        if (!user.hasRole(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
        return user;
    }

    /** Shopping actions: cart, checkout, own orders, budget. */
    public static User requireBuyer(HttpSession session, UserRepository userRepository) {
        return requireRole(session, userRepository, Role.BUYER,
                "Shopping needs a buyer account. Seller and admin accounts can't place orders.");
    }

    /** Seller dashboard. Whether the shop is approved is checked separately, per action. */
    public static User requireSeller(HttpSession session, UserRepository userRepository) {
        return requireRole(session, userRepository, Role.SELLER, "Sellers only");
    }

    public static User requireAdmin(HttpSession session, UserRepository userRepository) {
        return requireRole(session, userRepository, Role.ADMIN, "Admins only");
    }
}
