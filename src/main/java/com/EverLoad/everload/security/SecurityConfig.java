package com.EverLoad.everload.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final RateLimitFilter rateLimitFilter;
    private final MaintenanceFilter maintenanceFilter;

    /** Comma-separated allowed CORS origins. Set via CORS_ALLOWED_ORIGINS env var. */
    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:8080,http://localhost,https://localhost,capacitor://localhost}")
    private String corsAllowedOrigins;

    @Value("${app.security.swagger-public:false}")
    private boolean swaggerPublic;

    @Bean
    @SuppressWarnings("java:S4502") // Stateless bearer-token API: no cookie-authenticated session exists to forge.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(request -> {
                org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();
                java.util.List<String> origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
                config.setAllowedOriginPatterns(origins);
                config.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Range"));
                config.setExposedHeaders(java.util.List.of("Content-Disposition", "Accept-Ranges", "Content-Range", "Content-Length"));
                config.setAllowCredentials(false);
                return config;
            }))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/maintenance/status").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/public/auth-hero-images").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/app-release/android", "/api/app-release/android/download").permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/api/user/avatar/img/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/artists/image/**").permitAll();

                if (swaggerPublic) {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                } else {
                    auth.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").denyAll();
                }

                auth
                    .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                    .requestMatchers("/**/*.js", "/**/*.css", "/**/*.map").permitAll()
                    .requestMatchers("/**/*.png", "/**/*.jpg", "/**/*.jpeg", "/**/*.svg", "/**/*.ico", "/**/*.webp").permitAll()
                    .requestMatchers("/assets/**", "/media/**").permitAll()
                    .requestMatchers("/manifest.webmanifest", "/ngsw.json", "/ngsw-worker.js",
                                     "/safety-worker.js", "/worker-basic.min.js", "/icons/**").permitAll()
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll();
            })
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(maintenanceFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
