package com.smartmealplanner.profile.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DietaryPreferenceRepository
        extends JpaRepository<DietaryPreference, Long> {

    Optional<DietaryPreference> findByCode(
            String code);

    List<DietaryPreference> findAllByOrderByDisplayOrderAscCodeAsc();

    List<DietaryPreference> findByCodeIn(
            Collection<String> codes);
}
