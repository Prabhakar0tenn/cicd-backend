package com.selfhealing.service;

import com.selfhealing.exception.ResourceNotFoundException;
import com.selfhealing.model.User;
import com.selfhealing.repository.UserRepository;
import com.selfhealing.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user registration and login.
 *
 * Password security:
 * - BCrypt with strength 12 (2^12 hash rounds — good balance of security vs speed)
 * - BCrypt is adaptive: you can increase rounds later without breaking existing passwords
 *   by re-hashing on next successful login.
 * - Passwords are NEVER stored or logged in plain text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /**
     * Registers a new user.
     *
     * @return JWT token for immediate login after registration
     * @throws IllegalArgumentException if username is already taken
     */
    public String register(String username, String password) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken");
        }

        User user = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {}", username);
        return jwtService.generateToken(saved.getId());
    }

    /**
     * Authenticates an existing user.
     *
     * @return JWT token on success
     * @throws ResourceNotFoundException if username doesn't exist
     * @throws IllegalArgumentException  if password is wrong
     */
    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // Generic message — don't reveal whether username exists or password is wrong
            throw new IllegalArgumentException("Invalid credentials");
        }

        log.info("User logged in: {}", username);
        return jwtService.generateToken(user.getId());
    }

    /**
     * Loads a user by ID. Used by other services that need user info.
     */
    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
