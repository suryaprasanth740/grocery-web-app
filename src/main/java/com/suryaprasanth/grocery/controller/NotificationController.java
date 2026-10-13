package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.NotificationService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** The bell in the header: any logged-in account (buyer, seller or admin) sees only its own messages. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(NotificationService notificationService, UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Map<String, Object> latest(HttpSession session) {
        return notificationService.latest(SessionUtil.requireLoggedInUser(session, userRepository));
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> read(@PathVariable Long id, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        notificationService.markRead(user, id);
        return notificationService.latest(user);
    }

    @PostMapping("/read-all")
    public Map<String, Object> readAll(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        notificationService.markAllRead(user);
        return notificationService.latest(user);
    }
}
