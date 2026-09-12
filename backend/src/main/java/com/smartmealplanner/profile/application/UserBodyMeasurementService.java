package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.smartmealplanner.auth.application.CurrentUserIdentity;
import com.smartmealplanner.auth.application.CurrentUserService;
import com.smartmealplanner.profile.persistence.MeasurementSource;
import com.smartmealplanner.profile.persistence.UserBodyMeasurement;
import com.smartmealplanner.profile.persistence.UserBodyMeasurementRepository;
import com.smartmealplanner.profile.web.MeasurementResponse;
import com.smartmealplanner.profile.web.RecordMeasurementRequest;
import com.smartmealplanner.profile.web.UpdateMeasurementRequest;
import com.smartmealplanner.shared.web.InvalidRequestException;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBodyMeasurementService {

    private static final BigDecimal MIN_WEIGHT_KG =
            BigDecimal.valueOf(2);

    private static final BigDecimal MAX_WEIGHT_KG =
            BigDecimal.valueOf(700);

    private static final BigDecimal MIN_BODY_FAT_PERCENT =
            BigDecimal.ZERO;

    private static final BigDecimal MAX_BODY_FAT_PERCENT =
            BigDecimal.valueOf(100);

    private static final BigDecimal MIN_WAIST_CM =
            BigDecimal.valueOf(10);

    private static final BigDecimal MAX_WAIST_CM =
            BigDecimal.valueOf(400);

    private static final int MAX_NOTE_LENGTH =
            255;

    private static final int MAX_PAGE_SIZE =
            100;

    private final UserBodyMeasurementRepository measurementRepository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    @Autowired
    public UserBodyMeasurementService(
            UserBodyMeasurementRepository measurementRepository,
            CurrentUserService currentUserService) {

        this(
                measurementRepository,
                currentUserService,
                Clock.systemUTC());
    }

    public UserBodyMeasurementService(
            UserBodyMeasurementRepository measurementRepository,
            CurrentUserService currentUserService,
            Clock clock) {

        this.measurementRepository =
                measurementRepository;

        this.currentUserService =
                currentUserService;

        this.clock =
                clock;
    }

    @Transactional(readOnly = true)
    public MeasurementResponse getLatestMeasurement(
            UUID publicId) {

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        UserBodyMeasurement measurement =
                measurementRepository.findFirstByUserIdOrderByMeasuredOnDescIdDesc(
                                identity.internalId())
                        .orElseThrow(
                                () -> new EntityNotFoundException(
                                        "No measurement found for user"));

        return toResponse(
                measurement);
    }

    @Transactional(readOnly = true)
    public List<MeasurementResponse> getMeasurementHistory(
            UUID publicId,
            int page,
            int size,
            LocalDate from,
            LocalDate to) {

        if (page < 0) {
            throw new InvalidRequestException(
                    "Page index must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE);
        }

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException(
                    "From date must not be after to date");
        }

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        PageRequest pageRequest =
                PageRequest.of(
                        page,
                        size);

        List<UserBodyMeasurement> measurements;

        if (from != null && to != null) {

            measurements =
                    measurementRepository.findByUserIdAndMeasuredOnBetweenOrderByMeasuredOnDesc(
                            identity.internalId(),
                            from,
                            to,
                            pageRequest);

        } else {

            measurements =
                    measurementRepository.findByUserIdOrderByMeasuredOnDesc(
                            identity.internalId(),
                            pageRequest);
        }

        return measurements.stream()
                .map(UserBodyMeasurementService::toResponse)
                .toList();
    }

    @Transactional
    public MeasurementResponse recordMeasurement(
            UUID publicId,
            RecordMeasurementRequest request) {

        if (request == null) {
            throw new InvalidRequestException(
                    "Measurement request is required");
        }

        if (request.measuredOn() == null) {
            throw new InvalidRequestException(
                    "Measured date is required");
        }

        validateMeasurementValues(
                request.measuredOn(),
                request.weightKg(),
                request.bodyFatPercent(),
                request.waistCm(),
                request.note());

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        UserBodyMeasurement measurement =
                measurementRepository.findByUserIdAndMeasuredOn(
                                identity.internalId(),
                                request.measuredOn())
                        .orElse(null);

        if (measurement != null) {

            MeasurementSource source =
                    request.source() != null
                            ? request.source()
                            : MeasurementSource.CORRECTED;

            measurement.update(
                    request.weightKg(),
                    request.bodyFatPercent(),
                    request.waistCm(),
                    source,
                    request.note() == null
                            ? null
                            : request.note().trim());

        } else {

            MeasurementSource source =
                    request.source() != null
                            ? request.source()
                            : MeasurementSource.USER_ENTERED;

            measurement = new UserBodyMeasurement(
                    identity.internalId(),
                    request.measuredOn(),
                    request.weightKg(),
                    request.bodyFatPercent(),
                    request.waistCm(),
                    source,
                    request.note() == null
                            ? null
                            : request.note().trim());
        }

        UserBodyMeasurement saved =
                measurementRepository.saveAndFlush(
                        measurement);

        return toResponse(
                saved);
    }

    @Transactional
    public MeasurementResponse correctMeasurement(
            UUID publicId,
            LocalDate measuredOn,
            UpdateMeasurementRequest request) {

        if (measuredOn == null) {
            throw new InvalidRequestException(
                    "Measured date is required");
        }

        if (request == null) {
            throw new InvalidRequestException(
                    "Measurement request is required");
        }

        validateMeasurementValues(
                measuredOn,
                request.weightKg(),
                request.bodyFatPercent(),
                request.waistCm(),
                request.note());

        CurrentUserIdentity identity =
                currentUserService.getIdentity(
                        publicId);

        UserBodyMeasurement measurement =
                measurementRepository.findByUserIdAndMeasuredOn(
                                identity.internalId(),
                                measuredOn)
                        .orElse(null);

        MeasurementSource source =
                request.source() != null
                        ? request.source()
                        : MeasurementSource.CORRECTED;

        if (measurement != null) {

            measurement.update(
                    request.weightKg(),
                    request.bodyFatPercent(),
                    request.waistCm(),
                    source,
                    request.note() == null
                            ? null
                            : request.note().trim());

        } else {

            measurement = new UserBodyMeasurement(
                    identity.internalId(),
                    measuredOn,
                    request.weightKg(),
                    request.bodyFatPercent(),
                    request.waistCm(),
                    source,
                    request.note() == null
                            ? null
                            : request.note().trim());
        }

        UserBodyMeasurement saved =
                measurementRepository.saveAndFlush(
                        measurement);

        return toResponse(
                saved);
    }

    private void validateMeasurementValues(
            LocalDate measuredOn,
            BigDecimal weightKg,
            BigDecimal bodyFatPercent,
            BigDecimal waistCm,
            String note) {

        LocalDate today =
                LocalDate.now(clock);

        if (measuredOn.isAfter(today)) {
            throw new InvalidRequestException(
                    "Measurement date cannot be in the future");
        }

        if (weightKg == null) {
            throw new InvalidRequestException(
                    "Weight is required");
        }

        if (weightKg.compareTo(MIN_WEIGHT_KG) <= 0
                || weightKg.compareTo(MAX_WEIGHT_KG) >= 0) {

            throw new InvalidRequestException(
                    "Weight must be between 2 and 700 kg");
        }

        if (bodyFatPercent != null) {

            if (bodyFatPercent.compareTo(MIN_BODY_FAT_PERCENT) < 0
                    || bodyFatPercent.compareTo(MAX_BODY_FAT_PERCENT) > 0) {

                throw new InvalidRequestException(
                        "Body fat percentage must be between 0 and 100");
            }
        }

        if (waistCm != null) {

            if (waistCm.compareTo(MIN_WAIST_CM) <= 0
                    || waistCm.compareTo(MAX_WAIST_CM) >= 0) {

                throw new InvalidRequestException(
                        "Waist must be between 10 and 400 cm");
            }
        }

        if (note != null
                && note.length() > MAX_NOTE_LENGTH) {

            throw new InvalidRequestException(
                    "Note must not exceed 255 characters");
        }
    }

    private static MeasurementResponse toResponse(
            UserBodyMeasurement measurement) {

        return new MeasurementResponse(
                measurement.measuredOn(),
                measurement.weightKg(),
                measurement.bodyFatPercent(),
                measurement.waistCm(),
                measurement.source(),
                measurement.note(),
                measurement.createdAt());
    }
}
