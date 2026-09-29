package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.LoginRequest;
import com.suryaprasanth.grocery.dto.RegisterRequest;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.LoginAttemptService;
import com.suryaprasanth.grocery.util.EmailUtil;
import com.suryaprasanth.grocery.util.PasswordUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ALREADY_REGISTERED =
            "An account with this email already exists. Please log in.";

    private final UserRepository userRepository;

    private final LoginAttemptService loginAttempts;

    public AuthController(UserRepository userRepository, LoginAttemptService loginAttempts) {
        this.userRepository = userRepository;
        this.loginAttempts = loginAttempts;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request,
                                                          HttpSession session) {
        String email = request.getEmail().trim().toLowerCase();
        String key = EmailUtil.canonical(email);
        if (!EmailUtil.hasName(key)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email");
        }
        // One email inbox = one account. For Gmail, dots and "+anything" are ignored,
        // so s.urya@gmail.com and surya+2@gmail.com count as surya@gmail.com.
        if (userRepository.existsByEmailKey(key) || userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
        }
        User user = new User(request.getName().trim(), email, PasswordUtil.hash(request.getPassword()));
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the database lets only one in.
            throw new ResponseStatusException(HttpStatus.CONFLICT, ALREADY_REGISTERED);
        }
        session.setAttribute("userId", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toUserView(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        String email = request.getEmail().trim();
        // Same key as sign-up, so "S.urya+x@gmail.com" logs in to surya@gmail.com's account,
        // and the wrong-password lock can't be dodged by adding dots.
        String key = EmailUtil.canonical(email);

        // Brute-force protection: too many wrong passwords -> locked for a while.
        long locked = loginAttempts.minutesLocked(key);
        if (locked > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Try again in " + locked + " minute(s).");
        }

        // Exact email first (also finds old accounts made before this rule), then the Gmail key.
        User user = userRepository.findByEmailIgnoreCase(email)
                .or(() -> userRepository.findByEmailKey(key))
                .orElse(null);
        if (user == null || !PasswordUtil.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttempts.loginFailed(key);
            // Same message for "no such email" and "wrong password", so attackers can't find valid emails.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        loginAttempts.loginSucceeded(key);

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
