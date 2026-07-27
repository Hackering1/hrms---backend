package com.technnext.hrms.notification.controller;

import com.technnext.hrms.common.ApiResponse;
import com.technnext.hrms.notification.dto.NotificationCreate;
import com.technnext.hrms.notification.entity.Notification;
import com.technnext.hrms.notification.repository.NotificationRepository;
import com.technnext.hrms.notification.service.NotificationService;
import com.technnext.hrms.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FIX (round 1): All "my"/user-scoped READ endpoints read userId from the JWT
 *      via @AuthenticationPrincipal instead of accepting it as a URL path
 *      segment. Privileged roles (SUPER_ADMIN, HR, MANAGER) also receive
 *      broadcast notifications (userId IS NULL).
 *
 * FIX (round 2 — this change): the mutation endpoints were missed the first
 *      time round and had NO ownership check at all:
 *        - create() let any authenticated caller target any other user.
 *        - markRead()/delete() let any authenticated caller act on ANY
 *          notification by id, regardless of who it belonged to.
 *        - markAllReadLegacy(userId) let any authenticated caller mark all
 *          of another user's notifications read.
 *      All four are now locked down: create() is privileged-only, and
 *      markRead/delete/markAllReadLegacy require the caller to either own the
 *      notification (or the target userId) or hold a privileged role — the
 *      same "privileged sees/acts on everyone" rule already used by the
 *      /user/{userId} GET endpoints below.
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

    /**
     * SECURITY FIX: creating a notification targeted at an arbitrary userId is
     * an administrative/system action, not something any signed-in user should
     * be able to do to any other user. Restricted to privileged roles.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','HR_ADMIN','HR_EXECUTIVE','MANAGER')")
    public ApiResponse<Notification> create(@RequestBody NotificationCreate body) {
        return ApiResponse.ok("Notification created", service.create(body));
    }

    /**
     * SECURITY FIX: previously anyone could mark ANY notification read by id.
     * Now the caller must own the notification, or hold a privileged role.
     */
    @PutMapping("/{id}/read")
    public ApiResponse<Notification> markRead(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        assertCanAct(principal, service.getById(id));
        return ApiResponse.ok("Marked read", service.markRead(id));
    }

    @PutMapping("/my/read-all")
    public ApiResponse<Map<String, Integer>> markAllRead(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.ok("Marked all read",
                Map.of("updated", service.markAllRead(principal.getUser().getId())));
    }

    /**
     * SECURITY FIX: previously any authenticated user could mark ALL of any
     * other user's notifications read just by supplying their userId. Now
     * restricted to the caller's own id, or a privileged role acting on
     * someone else's behalf (mirrors the /user/{userId} GET endpoints above).
     */
    @PutMapping("/user/{userId}/read-all")
    public ApiResponse<Map<String, Integer>> markAllReadLegacy(
            @PathVariable UUID userId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (!isPrivileged(principal) && !principal.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to modify this user's notifications.");
        }
        return ApiResponse.ok("Marked all read", Map.of("updated", service.markAllRead(userId)));
    }

    /**
     * SECURITY FIX: previously anyone could delete ANY notification by id.
     * Now the caller must own the notification, or hold a privileged role.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        assertCanAct(principal, service.getById(id));
        service.delete(id);
        return ApiResponse.ok("Deleted", null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    /** A privileged role may act on any notification (including broadcasts);
     *  everyone else may only act on a notification that targets them. */
    private void assertCanAct(CustomUserDetails principal, Notification notification) {
        boolean owns = notification.getUserId() != null
                && notification.getUserId().equals(principal.getUser().getId());
        if (!owns && !isPrivileged(principal)) {
            throw new AccessDeniedException("You do not have permission to modify this notification.");
        }
    }

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