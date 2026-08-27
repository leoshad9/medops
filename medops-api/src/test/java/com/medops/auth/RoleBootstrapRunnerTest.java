package com.medops.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.medops.auth.entity.Role;
import com.medops.auth.repository.RoleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class RoleBootstrapRunnerTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleBootstrapRunner runner;

    @Test
    void run_insertsMissingRoles_andSkipsExistingOnes() {
        when(roleRepository.existsByName("DOCTOR")).thenReturn(false);
        when(roleRepository.existsByName("PATIENT")).thenReturn(true);

        runner.run(null);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("DOCTOR");
    }

    @Test
    void run_insertsNothing_whenAllRolesExist() {
        when(roleRepository.existsByName("DOCTOR")).thenReturn(true);
        when(roleRepository.existsByName("PATIENT")).thenReturn(true);

        runner.run(null);

        verify(roleRepository, never()).save(any());
    }
}
