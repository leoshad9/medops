package com.medops.auth.security.principal;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.domain.User;
import com.medops.auth.domain.UserStatus;
import com.medops.auth.infrastructure.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MedOpsUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return buildUserDetails(user);
    }

    /**
     * Builds a {@link MedOpsUser} view from an already-loaded {@link User}, avoiding a
     * redundant lookup for callers (e.g. registration, token refresh) that already hold
     * the entity within an active transaction.
     */
    public UserDetails buildUserDetails(User user) {
        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName())
                .map(SimpleGrantedAuthority::new)
                .toList();

        String primaryRole = user.getRoles().stream()
                .findFirst()
                .map(role -> role.getName())
                .orElse("PATIENT");

        return new MedOpsUser(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                primaryRole,
                authorities,
                user.getStatus() == UserStatus.ACTIVE,
                user.getStatus() != UserStatus.LOCKED);
    }
}
