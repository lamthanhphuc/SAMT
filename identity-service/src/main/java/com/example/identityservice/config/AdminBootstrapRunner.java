package com.example.identityservice.config;

import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.enabled:true}")
    private boolean enabled;

    @Value("${app.bootstrap-admin.email:admin@samt.local}")
    private String email;

    @Value("${app.bootstrap-admin.password:Str0ng@Pass!}")
    private String password;

    @Value("${app.bootstrap-admin.full-name:System Admin}")
    private String fullName;

    @Value("${app.bootstrap-admin.force-reset-password:true}")
    private boolean forceResetPassword;

    public AdminBootstrapRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (forceResetPassword) {
                existing.setPasswordHash(passwordEncoder.encode(password));
                existing.setStatus(User.Status.ACTIVE);
                existing.setRole(User.Role.ADMIN);
                if (existing.getFullName() == null || existing.getFullName().isBlank()) {
                    existing.setFullName(fullName);
                }
                userRepository.save(existing);
                log.warn("Bootstrap admin account password/status reset for local debugging: {}", email);
            }
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFullName(fullName);
        admin.setRole(User.Role.ADMIN);
        admin.setStatus(User.Status.ACTIVE);

        userRepository.save(admin);
        log.warn("Bootstrap admin account created for local debugging: {}", email);
    }
}
