package com.smartmealplanner.pantry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface PantryItemEventRepository extends JpaRepository<PantryItemEvent, Long> {

    List<PantryItemEvent> findByPantryItemIdOrderByOccurredAtAscIdAsc(
            Long pantryItemId);
}
