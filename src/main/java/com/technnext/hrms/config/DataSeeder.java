package com.technnext.hrms.config;

import com.technnext.hrms.auth.entity.Role;
import com.technnext.hrms.auth.entity.User;
import com.technnext.hrms.auth.repository.RoleRepository;
import com.technnext.hrms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Runs once at startup. If there are no users yet, it creates a default
 * SUPER_ADMIN so you can log in for the first time.
 * (Roles themselves are already inserted by your schema .sql file.)
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-email}")
    private String adminEmail;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return; // already seeded
        }

        Role superAdmin = roleRepository.findByName("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "SUPER_ADMIN role missing. Did you run the schema .sql (it seeds roles)?"));

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .isActive(true)
                .roles(Set.of(superAdmin))
                .build();

        userRepository.save(admin);
        System.out.println(">>> Seeded default SUPER_ADMIN: " + adminEmail);
    }
}