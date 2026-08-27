package com.medops.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medops.auth.entity.Role;

/**
 * Repository for Role entity.
 * Provides CRUD operations and custom query methods for role lookup.
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Find a role by name.
     *
     * @param name the role name
     * @return Optional containing the role if found
     */
    Optional<Role> findByName(String name);

    /**
     * Check if a role with the given name exists.
     *
     * @param name the role name to check
     * @return true if role exists, false otherwise
     */
    boolean existsByName(String name);
}
