package com.smartmealplanner.profile.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityLevelRepository
        extends JpaRepository<ActivityLevel, Long> {

    Optional<ActivityLevel> findByCode(
            String code);

    List<ActivityLevel> findAllByOrderByDisplayOrderAscCodeAsc();
}
