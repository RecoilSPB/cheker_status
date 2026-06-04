package ru.spb.reshenie.chekerstatus.security.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.security.AppSecurityProperties;

@Service
public class SecurityAccessService {

    private final AppSecurityProperties properties;

    public SecurityAccessService(AppSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isSecurityEnabled() {
        return properties.isEnabled();
    }

    public boolean isAuthenticated() {
        Authentication authentication = currentAuthentication();
        return authentication != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.isAuthenticated();
    }

    public boolean canViewDashboard() {
        return hasAuthority(SecurityPermissions.DASHBOARD_VIEW);
    }

    public boolean canViewDashboardMetrics() {
        return hasAuthority(SecurityPermissions.DASHBOARD_METRICS_VIEW);
    }

    public boolean canManageSync() {
        return hasAuthority(SecurityPermissions.DASHBOARD_SYNC_MANAGE);
    }

    public boolean canViewDocuments() {
        return hasAuthority(SecurityPermissions.DOCUMENTS_VIEW);
    }

    public boolean canViewCommits() {
        return hasAuthority(SecurityPermissions.COMMITS_VIEW);
    }

    public boolean canViewFileChanges() {
        return hasAuthority(SecurityPermissions.FILE_CHANGES_VIEW);
    }

    public boolean canManageUsers() {
        return hasAuthority(SecurityPermissions.USERS_MANAGE);
    }

    public String currentUsername() {
        if (!properties.isEnabled()) {
            return null;
        }
        Authentication authentication = currentAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }

    public String firstAccessiblePath() {
        if (!properties.isEnabled()) {
            return "/documents";
        }
        if (canViewDocuments()) {
            return "/documents";
        }
        if (canViewDashboard()) {
            return "/dashboard";
        }
        if (canViewCommits()) {
            return "/commits";
        }
        if (canViewFileChanges()) {
            return "/file-changes";
        }
        if (canManageUsers()) {
            return "/admin/users";
        }
        throw new AccessDeniedException("No accessible sections configured for the current user");
    }

    public boolean hasAuthority(String authority) {
        if (!properties.isEnabled()) {
            return true;
        }
        Authentication authentication = currentAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
            if (authority.equals(grantedAuthority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
