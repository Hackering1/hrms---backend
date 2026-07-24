package com.technnext.hrms.config;

import com.technnext.hrms.document.entity.DocumentCategory;
import com.technnext.hrms.document.repository.DocumentCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * #9: Ensures the four fixed document categories always exist so the Add
 * Employee form and the My Documents page can group uploads consistently.
 *
 * Runs on every startup and is idempotent — it only inserts categories whose
 * name is not already present, so it is safe on an existing database (unlike
 * DataSeeder, which is guarded by an empty-user check and only runs once).
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class DocumentCategorySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentCategorySeeder.class);

    private final DocumentCategoryRepository repository;

    // The canonical categories, in display order.
    private static final List<String> CATEGORIES = List.of(
            "Personal Documents",
            "Educational Documents",
            "TechNext Documents",
            "Experience Documents"
    );

    @Override
    public void run(String... args) {
        Set<String> existing = repository.findAll().stream()
                .map(DocumentCategory::getName)
                .filter(n -> n != null)
                .map(String::trim)
                .collect(Collectors.toSet());

        int created = 0;
        for (String name : CATEGORIES) {
            boolean present = existing.stream().anyMatch(e -> e.equalsIgnoreCase(name));
            if (!present) {
                repository.save(DocumentCategory.builder()
                        .name(name)
                        .description(name)
                        .hasExpiry(false)
                        .isActive(true)
                        .build());
                created++;
            }
        }
        if (created > 0) {
            log.info("DocumentCategorySeeder: created {} missing document category(ies)", created);
        }
    }
}