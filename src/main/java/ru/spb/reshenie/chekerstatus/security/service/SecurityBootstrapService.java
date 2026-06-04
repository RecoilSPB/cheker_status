package ru.spb.reshenie.chekerstatus.security.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.security.AppSecurityProperties;

@Service
public class SecurityBootstrapService implements ApplicationRunner {

    private final AppSecurityProperties properties;
    private final UserManagementService userManagementService;

    public SecurityBootstrapService(AppSecurityProperties properties, UserManagementService userManagementService) {
        this.properties = properties;
        this.userManagementService = userManagementService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        userManagementService.bootstrapAdmin(properties.getUsername(), properties.getPassword());
    }
}
