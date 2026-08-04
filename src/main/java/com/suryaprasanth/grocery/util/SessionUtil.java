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
}
