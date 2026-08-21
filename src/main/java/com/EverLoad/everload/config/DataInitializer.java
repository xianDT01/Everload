package com.everload.everload.config;

import com.everload.everload.model.Role;
import com.everload.everload.model.User;
import com.everload.everload.model.UserStatus;
import com.everload.everload.repository.UserRepository;
import com.everload.everload.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ChatService chatService;

    /** Contraseña inicial del admin en el primer arranque; si falta, se genera una aleatoria. */
    @Value("${app.admin.initial-password:}")
    private String initialAdminPassword;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            // Antes era "admin123" fija: cualquiera que conociera la app podía entrar
            // en una instalación recién desplegada.
            String password = initialAdminPassword;
            boolean generated = password == null || password.isBlank();
            if (generated) {
                password = UUID.randomUUID().toString();
            }
            User admin = User.builder()
                    .username("admin")
                    .email("admin@everload.local")
                    .password(passwordEncoder.encode(password))
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build();
            userRepository.save(admin);
            if (generated) {
                log.warn("Usuario 'admin' creado con contraseña aleatoria: {} — cámbiala tras el primer login "
                        + "(o define APP_ADMIN_INITIAL_PASSWORD antes del primer arranque).", password);
            } else {
                log.info("Usuario 'admin' creado con la contraseña de APP_ADMIN_INITIAL_PASSWORD.");
            }
        }

        try {
            chatService.ensureAnnouncementChannel();
            log.info("Canal de anuncios verificado/creado correctamente.");
        } catch (Exception e) {
            log.warn("No se pudo inicializar el canal de anuncios: {}", e.getMessage());
        }
    }
}