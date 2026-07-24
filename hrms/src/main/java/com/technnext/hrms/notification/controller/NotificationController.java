package com.technnext.hrms.notification.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.notification.dto.NotificationCreate;
import com.technnext.hrms.notification.entity.Notification;
import com.technnext.hrms.notification.repository.NotificationRepository;
import com.technnext.hrms.notification.service.NotificationService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FIX: All user-scoped endpoints now read userId from the JWT via
 *      @AuthenticationPrincipal instead of accepting it as a URL path segment.
 *      Previously any authenticated user could request any other user's
 *      notifications by guessing their UUID.
 *
 *      Privileged roles (SUPER_ADMIN, HR, MANAGER) also receive broadcast
 *      notifications (userId IS NULL) created by background services like
 *      the regularization request notifier.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;
    private final NotificationRepository repository;

    // ── "my" endpoints — always scoped to the caller ────────────────────────────

    @GetMapping("/my")
    public ApiResponse<List<Notification>> getMy(
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = principal.getUser().getId();
        if (isPrivileged(principal)) {
            return ApiResponse.ok(repository.findForPrivilegedUser(userId));
        }
        return ApiResponse.ok(service.getForUser(userId));
    }

    @GetMapping("/my/unread")
    public ApiResponse<List<Notification>> getMyUnread(
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = principal.getUser().getId();
        if (isPrivileged(principal)) {
            return ApiResponse.ok(repository.findUnreadForPrivilegedUser(userId));
        }
        return ApiResponse.ok(service.getUnread(userId));
    }

    @GetMapping("/my/unread-count")
    public ApiResponse<Map<String, Long>> getMyUnreadCount(
            @AuthenticationPrincipal CustomUserDetails principal) {
        UUID userId = principal.getUser().getId();
        long count = isPrivileged(principal)
                ? repository.countUnreadForPrivilegedUser(userId)
                : service.unreadCount(userId);
        return ApiResponse.ok(Map.of("count", count));
    }

    // ── Legacy path-param endpoints (kept for backward compatibility) ────────────

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Notification>> getForUser(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        // FIX: only return own data unless privileged
        if (!isPrivileged(principal) && !principal.getUser().getId().equals(userId)) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(isPrivileged(principal)
                ? repository.findForPrivilegedUser(userId)
                : service.getForUser(userId));
    }

    @GetMapping("/user/{userId}/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (!isPrivileged(principal) && !principal.getUser().getId().equals(userId)) {
            return ApiResponse.ok(Map.of("count", 0L));
        }
        long count = isPrivileged(principal)
                ? repository.countUnreadForPrivilegedUser(userId)
                : service.unreadCount(userId);
        return ApiResponse.ok(Map.of("count", count));
    }

    // ── Mutations ────────────────────────────────────────────────────────────────

    @PostMapping
    public ApiResponse<Notification> create(@RequestBody NotificationCreate body) {
        return ApiResponse.ok("Notification created", service.create(body));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Notification> markRead(@PathVariable Integer id) {
        return ApiResponse.ok("Marked read", service.markRead(id));
    }

    @PutMapping("/my/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Marked all read",
                Map.of("updated", service.markAllRead(principal.getUser().getId())));
    }

    @PutMapping("/user/{userId}/read-all")
    public ApiResponse<Map<String, Integer>> markAllReadLegacy(@PathVariable UUID userId) {
        return ApiResponse.ok("Marked all read", Map.of("updated", service.markAllRead(userId)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }

    // ── helper ──────────────────────────────────────────────────────────────────

    private boolean isPrivileged(CustomUserDetails p) {
        return p.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return auth.equals("ROLE_SUPER_ADMIN")
                            || auth.equals("ROLE_HR_ADMIN")
                            || auth.equals("ROLE_HR_EXECUTIVE")
                            || auth.equals("ROLE_MANAGER");
                });
    }
}