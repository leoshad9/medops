package com.medops.auth;

import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medops.auth.entity.Role;
import com.medops.auth.repository.RoleRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ensures the active roles (DOCTOR, PATIENT - see {@link Role}) exist on startup. Idempotent:
 * safe to run on every boot since it only inserts a role row when one by that name is missing.
 */
@Component
@RequiredArgsConstructor
public class RoleBootstrapRunner implements ApplicationRunner {

    private static final String[] ACTIVE_ROLES = {"DOCTOR", "PATIENT"};

    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String roleName : ACTIVE_ROLES) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(Objects.requireNonNull(Role.builder().name(roleName).build()));
            }
        }
    }
}
