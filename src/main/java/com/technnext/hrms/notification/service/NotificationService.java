package com.technnext.hrms.notification.service;

import com.technnext.hrms.common.exception.ResourceNotFoundException;
import com.technnext.hrms.notification.dto.NotificationCreate;
import com.technnext.hrms.notification.entity.Notification;
import com.technnext.hrms.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;

    public List<Notification> getForUser(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnread(UUID userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * SECURITY FIX: fetch a single notification so the controller can check
     * ownership before allowing markRead/delete. Previously those endpoints
     * acted on any id with no ownership check at all.
     */
    public Notification getById(Integer id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    /**
     * Create a notification targeted at a single specific user.
     *
     * FIX: Runs in its OWN transaction (REQUIRES_NEW). Notifications are a
     * side-effect of business actions (raising/approving a regularization, etc.).
     * If writing the notification fails, that failure must NOT roll back — or
     * even mark rollback-only — the caller's main transaction. REQUIRES_NEW
     * suspends the caller's transaction and runs this in a separate one, so a
     * failure here is fully contained and the caller's try/catch can safely
     * swallow it without triggering an UnexpectedRollbackException at commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(NotificationCreate req) {
        Notification n = Notification.builder()
                .userId(req.userId())
                .title(req.title())
                .message(req.message())
                .type(req.type())
                .module(req.module())
                .referenceId(req.referenceId())
                .isRead(false)
                .build();
        return repository.save(n);
    }

    /**
     * Broadcast notification (userId = null) — shown to all privileged users
     * (HR / managers / super admins) via the notification controller / frontend.
     *
     * Requires notifications.user_id to be NULLABLE (see V2 migration and the
     * updated Notification entity). Also runs in its own transaction for the
     * same isolation reason as create() above.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createBroadcast(String title, String message,
                                        String type, String module, String referenceId) {
        Notification n = Notification.builder()
                .userId(null)          // null = broadcast; shown to privileged roles only
                .title(title)
                .message(message)
                .type(type)
                .module(module)
                .referenceId(referenceId)
                .isRead(false)
                .build();
        return repository.save(n);
    }

    @Transactional
    public Notification markRead(Integer id) {
        Notification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.setIsRead(true);
        n.setReadAt(LocalDateTime.now());
        return repository.save(n);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        List<Notification> unread =
                repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> { n.setIsRead(true); n.setReadAt(now); });
        repository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void delete(Integer id) {
        if (!repository.existsById(id))
            throw new ResourceNotFoundException("Notification", id);
        repository.deleteById(id);
    }
}