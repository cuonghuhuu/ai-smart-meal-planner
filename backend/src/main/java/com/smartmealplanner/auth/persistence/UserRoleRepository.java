package com.smartmealplanner.auth.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleRepository
        extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findAllByUser_Id(
            Long userId);

    boolean existsByUserAndRole(
            UserAccount user,
            Role role);

    @Query("""
            select role.code
            from UserRole userRole
            join userRole.role role
            where userRole.user.id = :userId
            order by role.code
            """)
    List<String> findRoleCodesByUserId(
            @Param("userId")
            Long userId);
}