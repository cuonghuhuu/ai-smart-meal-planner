package com.smartmealplanner.mealplanning;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AiScoreComponentRepository extends JpaRepository<AiScoreComponent, Long> {
    List<AiScoreComponent> findAllByCodeIn(Collection<String> codes);
}
