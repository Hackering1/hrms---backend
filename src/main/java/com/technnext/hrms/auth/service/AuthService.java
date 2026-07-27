package com.technnext.hrms.auth.service;

import com.technnext.hrms.auth.dto.*;
import com.technnext.hrms.auth.entity.RefreshToken;
import com.technnext.hrms.auth.entity.Role;
import com.technnext.hrms.auth.entity.User;
import com.technnext.hrms.auth.repository.RefreshTokenRepository;
import com.technnext.hrms.auth.repository.RoleRepository;
import com.technnext.hrms.auth.repository.UserRepository;
import com.technnext.hrms.common.exception.BadRequestException;
import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.security.CustomUserDetails;
import com.technnext.hrms.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshExpMs;

    /** Create a new user account and assign a role. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email already registered: " + req.email());
        }
        Role role = roleRepository.findByName(req.roleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role", req.roleName()));

        User user = User.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .isActive(true)
                .roles(Set.of(role))
                .build();
        userRepository.save(user);

        return issueTokens(user);
    }

    /**
     * Verify credentials and return access + refresh tokens.
     * FIX: Wrap AuthenticationManager.authenticate() to translate Spring Security
     *      exceptions into our BadRequestException (400) instead of leaking a 500.
     *      DisabledException is returned as 403 via the GlobalExceptionHandler's
     *      AccessDeniedException path — here we surface a clear message.
     */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (DisabledException ex) {
            throw new BadRequestException("This account has been deactivated. Contact your HR administrator.");
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid email or password.");
        }

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = principal.getUser();

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    /**
     * Exchange a valid refresh token for a fresh access token (rotates the refresh token).
     * FIX: The original code deleted the token before fetching the user, which meant a
     *      crash during user-lookup left a dangling deleted token with no new token issued.
     *      Now the rotation only happens after we've confirmed the user exists.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken stored = refreshTokenRepository.findByToken(req.refreshToken())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteByToken(stored.getToken());
            throw new BadRequestException("Refresh token expired, please log in again");
        }

        // FIX: Load user BEFORE deleting the old token so a crash here doesn't
        //      leave the user with no valid refresh token.
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", stored.getUserId()));

        // FIX: Also reject tokens for deactivated users
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            refreshTokenRepository.deleteByToken(stored.getToken());
            throw new BadRequestException("This account has been deactivated.");
        }

        refreshTokenRepository.deleteByToken(stored.getToken()); // rotate
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshRequest req) {
        // FIX: guard against null/blank token to avoid a NullPointerException in the repo
        if (req.refreshToken() != null && !req.refreshToken().isBlank()) {
            refreshTokenRepository.deleteByToken(req.refreshToken());
        }
    }

    // ---- helpers ----

    private AuthResponse issueTokens(User user) {
        CustomUserDetails details = new CustomUserDetails(user);
        List<String> authorities = details.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        String accessToken = jwtService.generateAccessToken(user.getEmail(), authorities);
        String refreshToken = createAndStoreRefreshToken(user.getId());

        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        boolean mustChange = Boolean.TRUE.equals(user.getMustChangePassword());
        return new AuthResponse(
                user.getId().toString(),
                accessToken, refreshToken, "Bearer", user.getEmail(), roleNames, mustChange);
    }

    private String createAndStoreRefreshToken(UUID userId) {
        String token = UUID.randomUUID().toString();
        RefreshToken rt = RefreshToken.builder()
                .userId(userId)
                .token(token)
                // FIX: multiply before converting to nanos to avoid long overflow for large ms values
                .expiresAt(LocalDateTime.now().plusNanos(refreshExpMs * 1_000_000L))
                .build();
        refreshTokenRepository.save(rt);
        return token;
    }

    public static final String DEFAULT_PASSWORD = "User@0412";

    /**
     * Admin-side: create a login account for another person (e.g. a new employee).
     * Returns a map with "userId" (UUID) and "tempPassword" (String).
     */
    @Transactional
    public Map<String, Object> createUserAccount(String email, String roleName, String password) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required to create a login");
        }
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email already registered: " + email);
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleName));

        String initialPassword = (password == null || password.isBlank()) ? DEFAULT_PASSWORD : password;
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(initialPassword))
                .isActive(true)
                .mustChangePassword(true)
                .roles(Set.of(role))
                .build();
        userRepository.save(user);

        Map<String, Object> out = new HashMap<>();
        out.put("userId", user.getId());
        out.put("tempPassword", initialPassword);
        return out;
    }

    /**
     * Change the password for the given user (identified by email from the JWT).
     * FIX: Validate that newPassword meets a minimum length to prevent empty passwords.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        // FIX: Prevent setting a blank or trivially short new password
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    /** Super-admin: reset another user's password directly (no current-password needed). */
    @Transactional
    public void adminResetPassword(UUID userId, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
    }

    /** Super-admin: reset another user's password, identified by login email. */
    @Transactional
    public void adminResetPasswordByEmail(String email, String newPassword) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password is required");
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
    }

    // kept for potential future use (not called by any endpoint currently)
    @SuppressWarnings("unused")
    private String generateTempPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("Tn");
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}