package com.smartmealplanner.profile.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBodyMeasurementRepository
        extends JpaRepository<UserBodyMeasurement, Long> {

    Optional<UserBodyMeasurement> findByUserIdAndMeasuredOn(
            Long userId,
            LocalDate measuredOn);

    Optional<UserBodyMeasurement> findFirstByUserIdOrderByMeasuredOnDescIdDesc(
            Long userId);

    List<UserBodyMeasurement> findByUserIdOrderByMeasuredOnDesc(
            Long userId,
            Pageable pageable);

    List<UserBodyMeasurement> findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnDesc(
            Long userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);
}
