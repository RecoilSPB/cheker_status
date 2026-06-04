package ru.spb.reshenie.chekerstatus.security.service;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.security.model.SecurityUserAccount;
import ru.spb.reshenie.chekerstatus.security.repository.AccessControlRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AccessControlRepository repository;

    public DatabaseUserDetailsService(AccessControlRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SecurityUserAccount account = repository.findSecurityUserByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        List<String> authorityCodes = repository.findAuthorityCodesByUserId(account.getId());
        List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
        for (String authorityCode : authorityCodes) {
            authorities.add(new SimpleGrantedAuthority(authorityCode));
        }

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .disabled(!account.isEnabled())
                .authorities(authorities)
                .build();
    }
}
