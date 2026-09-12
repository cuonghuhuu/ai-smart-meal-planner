package com.smartmealplanner.profile.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBodyMeasurementServiceTest {

    @Mock
    private UserBodyMeasurementRepository measurementRepository;

    @Mock
    private CurrentUserService currentUserService;

    private Clock clock;
    private UserBodyMeasurementService service;

    private final UUID publicId = UUID.randomUUID();
    private final Long internalId = 42L;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2026-09-12T12:00:00Z"),
                ZoneOffset.UTC);

        service = new UserBodyMeasurementService(
                measurementRepository,
                currentUserService,
                clock);
    }

    @Test
    void getLatestReturnsLatestMeasurement() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        UserBodyMeasurement measurement = new UserBodyMeasurement(
                internalId,
                LocalDate.of(2026, 9, 10),
                BigDecimal.valueOf(70.5),
                BigDecimal.valueOf(18.5),
                BigDecimal.valueOf(82.0),
                MeasurementSource.USER_ENTERED,
                "Morning weigh-in");

        when(measurementRepository.findFirstByUserIdOrderByMeasuredOnDescIdDesc(internalId))
                .thenReturn(Optional.of(measurement));

        MeasurementResponse response = service.getLatestMeasurement(publicId);

        assertThat(response).isNotNull();
        assertThat(response.measuredOn()).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(response.weightKg()).isEqualTo(BigDecimal.valueOf(70.5));
        assertThat(response.bodyFatPercent()).isEqualTo(BigDecimal.valueOf(18.5));
        assertThat(response.waistCm()).isEqualTo(BigDecimal.valueOf(82.0));
        assertThat(response.source()).isEqualTo(MeasurementSource.USER_ENTERED);
    }

    @Test
    void getLatestThrowsEntityNotFoundWhenNoneExist() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        when(measurementRepository.findFirstByUserIdOrderByMeasuredOnDescIdDesc(internalId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLatestMeasurement(publicId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("No measurement found for user");
    }

    @Test
    void getHistoryValidatesPaginationAndDateRange() {
        // negative page
        assertThatThrownBy(() -> service.getMeasurementHistory(publicId, -1, 20, null, null))
                .isInstanceOf(InvalidRequestException.class);

        // invalid size
        assertThatThrownBy(() -> service.getMeasurementHistory(publicId, 0, 0, null, null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.getMeasurementHistory(publicId, 0, 101, null, null))
                .isInstanceOf(InvalidRequestException.class);

        // from after to
        LocalDate from = LocalDate.of(2026, 9, 10);
        LocalDate to = LocalDate.of(2026, 9, 5);
        assertThatThrownBy(() -> service.getMeasurementHistory(publicId, 0, 20, from, to))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getHistoryReturnsPagedMeasurements() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        UserBodyMeasurement m1 = new UserBodyMeasurement(
                internalId,
                LocalDate.of(2026, 9, 10),
                BigDecimal.valueOf(71.0),
                null,
                null,
                MeasurementSource.USER_ENTERED,
                null);

        when(measurementRepository.findByUserIdOrderByMeasuredOnDesc(eq(internalId), any(PageRequest.class)))
                .thenReturn(List.of(m1));

        List<MeasurementResponse> results = service.getMeasurementHistory(publicId, 0, 10, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).weightKg()).isEqualTo(BigDecimal.valueOf(71.0));
    }

    @Test
    void recordMeasurementCreatesNewMeasurementWhenNoneOnDate() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        LocalDate date = LocalDate.of(2026, 9, 11);
        when(measurementRepository.findByUserIdAndMeasuredOn(internalId, date))
                .thenReturn(Optional.empty());

        when(measurementRepository.saveAndFlush(any(UserBodyMeasurement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecordMeasurementRequest request = new RecordMeasurementRequest(
                date,
                BigDecimal.valueOf(72.0),
                BigDecimal.valueOf(19.0),
                BigDecimal.valueOf(83.5),
                null,
                "After workout");

        MeasurementResponse response = service.recordMeasurement(publicId, request);

        assertThat(response.measuredOn()).isEqualTo(date);
        assertThat(response.weightKg()).isEqualTo(BigDecimal.valueOf(72.0));
        assertThat(response.source()).isEqualTo(MeasurementSource.USER_ENTERED);

        ArgumentCaptor<UserBodyMeasurement> captor = ArgumentCaptor.forClass(UserBodyMeasurement.class);
        verify(measurementRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().source()).isEqualTo(MeasurementSource.USER_ENTERED);
    }

    @Test
    void recordMeasurementCorrectsExistingMeasurementOnSameDate() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        LocalDate date = LocalDate.of(2026, 9, 11);
        UserBodyMeasurement existing = new UserBodyMeasurement(
                internalId,
                date,
                BigDecimal.valueOf(72.0),
                null,
                null,
                MeasurementSource.USER_ENTERED,
                null);

        when(measurementRepository.findByUserIdAndMeasuredOn(internalId, date))
                .thenReturn(Optional.of(existing));

        when(measurementRepository.saveAndFlush(any(UserBodyMeasurement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecordMeasurementRequest request = new RecordMeasurementRequest(
                date,
                BigDecimal.valueOf(71.5),
                BigDecimal.valueOf(18.0),
                null,
                null,
                "Scale was recalibrated");

        MeasurementResponse response = service.recordMeasurement(publicId, request);

        assertThat(response.weightKg()).isEqualTo(BigDecimal.valueOf(71.5));
        assertThat(response.source()).isEqualTo(MeasurementSource.CORRECTED);
        assertThat(existing.note()).isEqualTo("Scale was recalibrated");
    }

    @Test
    void correctMeasurementUpdatesOrCreatesWithCorrectedSource() {
        when(currentUserService.getIdentity(publicId))
                .thenReturn(new CurrentUserIdentity(internalId, publicId));

        LocalDate date = LocalDate.of(2026, 9, 10);
        when(measurementRepository.findByUserIdAndMeasuredOn(internalId, date))
                .thenReturn(Optional.empty());

        when(measurementRepository.saveAndFlush(any(UserBodyMeasurement.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UpdateMeasurementRequest request = new UpdateMeasurementRequest(
                BigDecimal.valueOf(70.2),
                null,
                null,
                null,
                "Correction");

        MeasurementResponse response = service.correctMeasurement(publicId, date, request);

        assertThat(response.source()).isEqualTo(MeasurementSource.CORRECTED);
        assertThat(response.weightKg()).isEqualTo(BigDecimal.valueOf(70.2));
    }

    @Test
    void measurementValidationRejectsOutOfBoundValues() {
        // Future date
        RecordMeasurementRequest futureReq = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 15), BigDecimal.valueOf(70.0), null, null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, futureReq))
                .isInstanceOf(InvalidRequestException.class);

        // Missing date
        RecordMeasurementRequest nullDate = new RecordMeasurementRequest(
                null, BigDecimal.valueOf(70.0), null, null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, nullDate))
                .isInstanceOf(InvalidRequestException.class);

        // Weight <= 2
        RecordMeasurementRequest tooLight = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(2.0), null, null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, tooLight))
                .isInstanceOf(InvalidRequestException.class);

        // Weight >= 700
        RecordMeasurementRequest tooHeavy = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(700.0), null, null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, tooHeavy))
                .isInstanceOf(InvalidRequestException.class);

        // Body fat < 0
        RecordMeasurementRequest negFat = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(70.0), BigDecimal.valueOf(-0.1), null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, negFat))
                .isInstanceOf(InvalidRequestException.class);

        // Body fat > 100
        RecordMeasurementRequest overFat = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(70.0), BigDecimal.valueOf(100.1), null, null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, overFat))
                .isInstanceOf(InvalidRequestException.class);

        // Waist <= 10
        RecordMeasurementRequest smallWaist = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(70.0), null, BigDecimal.valueOf(10.0), null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, smallWaist))
                .isInstanceOf(InvalidRequestException.class);

        // Waist >= 400
        RecordMeasurementRequest hugeWaist = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(70.0), null, BigDecimal.valueOf(400.0), null, null);
        assertThatThrownBy(() -> service.recordMeasurement(publicId, hugeWaist))
                .isInstanceOf(InvalidRequestException.class);

        // Note > 255
        RecordMeasurementRequest longNote = new RecordMeasurementRequest(
                LocalDate.of(2026, 9, 10), BigDecimal.valueOf(70.0), null, null, null, "a".repeat(256));
        assertThatThrownBy(() -> service.recordMeasurement(publicId, longNote))
                .isInstanceOf(InvalidRequestException.class);
    }
}
