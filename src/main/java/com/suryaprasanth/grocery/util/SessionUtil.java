package com.suryaprasanth.grocery.util;

import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class SessionUtil {

    private SessionUtil() {
    }

    public static User requireLoggedInUser(HttpSession session, UserRepository userRepository) {
        Object rawId = session.getAttribute("userId");
        if (rawId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first");
        }
        Long userId = (Long) rawId;
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first"));
    }

    /** Like requireLoggedInUser, but also requires the ADMIN role (403 for normal customers). */
    public static User requireAdmin(HttpSession session, UserRepository userRepository) {
        User user = requireLoggedInUser(session, userRepository);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admins only");
        }
        return user;
    }
}
