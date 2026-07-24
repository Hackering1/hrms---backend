package com.technnext.hrms.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public, unauthenticated health check.
 *
 * Lives under /api/public/** which is already permitAll() in SecurityConfig, so
 * an external uptime pinger (UptimeRobot / cron-job.org) can hit it every ~10 min
 * to keep the Render free-tier service from spinning down. Deliberately does NO
 * database work — it must stay cheap and never fail.
 *
 * Place at: src/main/java/com/technnext/hrms/common/PublicController.java
 * Then ping: https://<your-backend>.onrender.com/api/public/health
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "hrms-backend",
                "timestamp", Instant.now().toString()
        );
    }
}