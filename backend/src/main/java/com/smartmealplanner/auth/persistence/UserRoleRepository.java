package com.smartmealplanner.auth.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findAllByUser_Id(Long userId);

    boolean existsByUserAndRole(UserAccount user, Role role);
}