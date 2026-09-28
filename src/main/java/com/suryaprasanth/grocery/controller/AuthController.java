package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.LoginRequest;
import com.suryaprasanth.grocery.dto.RegisterRequest;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.LoginAttemptService;
import com.suryaprasanth.grocery.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    private final LoginAttemptService loginAttempts;

    public AuthController(UserRepository userRepository, LoginAttemptService loginAttempts) {
        this.userRepository = userRepository;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                          HttpSession session) {
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User(request.getName().trim(), request.getEmail().trim().toLowerCase(),
                PasswordUtil.hash(request.getPassword()));
        user = userRepository.save(user);
        session.setAttribute("userId", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserView(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        String email = request.getEmail().trim();

        // Brute-force protection: too many wrong passwords -> locked for a while.
        long locked = loginAttempts.minutesLocked(email);
        if (locked > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Try again in " + locked + " minute(s).");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttempts.loginFailed(email);
            // Same message for "no such email" and "wrong password", so attackers can't find valid emails.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        loginAttempts.loginSucceeded(email);

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId(); // new session id after login (stops session fixation)
        session.setAttribute("userId", user.getId());
        return ResponseEntity.ok(toUserView(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in"));
        return ResponseEntity.ok(toUserView(user));
    }

    private Map<String, Object> toUserView(User user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", user.getId());
        view.put("name", user.getName());
        view.put("email", user.getEmail());
        view.put("role", user.getRole());
        return view;
    }
}
