package com.technnext.hrms.audit.service;

import com.technnext.hrms.audit.entity.AuditLog;
import com.technnext.hrms.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

/**
 * Records audit entries for important actions (e.g. HR leave overrides).
 *
 * Design choices:
 *  - jsonb columns are written as small JSON strings that we build by hand
 *    (no Jackson/ObjectMapper dependency needed — the audit payloads are flat
 *    key/value maps, so hand-serialization is safe and dependency-free).
 *  - logging is NON-FATAL and runs in its own transaction (REQUIRES_NEW). If the
 *    audit write fails, we flush inside the try so the error surfaces HERE, then
 *    mark this inner transaction rollback-only so its commit can't throw upward
 *    and 500 the real business action.
 *  - ip/user-agent are best-effort from the current HTTP request if available.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository repository;

    /** Full record. Prefer the Map-based overload below for common cases. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, String action, String module, String recordId,
                       Map<String, ?> oldValues, Map<String, ?> newValues) {
        try {
            String ip = null, ua = null;
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                ip = clientIp(req);
                ua = req.getHeader("User-Agent");
            }

            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .module(module)
                    .recordId(recordId)
                    .oldValues(toJson(oldValues))
                    .newValues(toJson(newValues))
                    .ipAddress(ip)
                    .userAgent(ua)
                    .build();

            // Flush now so any DB error is thrown inside this try (not later on
            // commit, where the catch below could no longer handle it).
            repository.saveAndFlush(entry);
        } catch (Exception e) {
            // Roll THIS inner transaction back cleanly so its commit can't throw
            // upward and break the real action. Auditing must never 500 a user op.
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ignore) {
                /* no active transaction — nothing to roll back */
            }
            log.warn("Audit log write failed for action={} module={} record={}: {}",
                    action, module, recordId, e.getMessage());
        }
    }

    /** Alias kept for readability at call sites. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(UUID userId, String action, String module, String recordId,
                    Map<String, ?> oldValues, Map<String, ?> newValues) {
        record(userId, action, module, recordId, oldValues, newValues);
    }

    // ── minimal JSON builder (no external library) ───────────────────────────
    private String toJson(Map<String, ?> map) {
        if (map == null) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                sb.append(v.toString());
            } else {
                sb.append("\"").append(escape(v.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private String clientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) return xf.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}