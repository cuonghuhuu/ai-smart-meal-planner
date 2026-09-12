package com.smartmealplanner.profile.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergenRepository
        extends JpaRepository<Allergen, Long> {

    Optional<Allergen> findByCode(
            String code);

    List<Allergen> findAllByOrderByDisplayOrderAscCodeAsc();

    List<Allergen> findByCodeIn(
            Collection<String> codes);
}
