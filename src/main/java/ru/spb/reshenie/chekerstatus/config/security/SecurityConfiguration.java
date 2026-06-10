package ru.spb.reshenie.chekerstatus.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.spb.reshenie.chekerstatus.security.service.SecurityPermissions;

import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecurityProperties properties) throws Exception {
        if (!properties.isEnabled()) {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }

        validateCredentials(properties);

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/error").permitAll()
                        .requestMatchers(POST, "/dashboard/sync-runs").hasAuthority(SecurityPermissions.DASHBOARD_SYNC_MANAGE)
                        .requestMatchers(POST, "/api/dashboard/sync-runs").hasAuthority(SecurityPermissions.DASHBOARD_SYNC_MANAGE)
                        .requestMatchers(POST, "/api/file-diff/backfill").hasAuthority(SecurityPermissions.DASHBOARD_SYNC_MANAGE)
                        .requestMatchers("/dashboard/**", "/api/dashboard/**").hasAuthority(SecurityPermissions.DASHBOARD_VIEW)
                        .requestMatchers("/documents/**").hasAuthority(SecurityPermissions.DOCUMENTS_VIEW)
                        .requestMatchers("/commits/**").hasAuthority(SecurityPermissions.COMMITS_VIEW)
                        .requestMatchers("/file-changes/**").hasAuthority(SecurityPermissions.FILE_CHANGES_VIEW)
                        .requestMatchers("/admin/users/**").hasAuthority(SecurityPermissions.USERS_MANAGE)
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .httpBasic(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private void validateCredentials(AppSecurityProperties properties) {
        if (!hasText(properties.getUsername())) {
            throw new IllegalStateException("app.security.username must be configured");
        }
        if (!hasText(properties.getPassword())) {
            throw new IllegalStateException("app.security.password must be configured");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
