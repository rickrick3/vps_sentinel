package com.expertiz.sentinel.core.identity.internal.model;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Utilisateurs non supprimés ({@code @SQLRestriction} de {@link User}). */
public interface UserRepository extends JpaRepository<User, UUID> {}
