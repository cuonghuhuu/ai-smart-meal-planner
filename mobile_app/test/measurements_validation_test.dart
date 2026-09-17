import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_validation.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';

void main() {
  test(
    'localized decimal parsing accepts comma and dot and rejects ambiguity',
    () {
      expect(
        MeasurementsValidation.parseLocalizedMeasurementNumber('70,5'),
        70.5,
      );
      expect(
        MeasurementsValidation.parseLocalizedMeasurementNumber('70.5'),
        70.5,
      );
      for (final value in ['70,5.2', '70..5', '70,,5', '70.', 'abc']) {
        expect(
          MeasurementsValidation.parseLocalizedMeasurementNumber(value),
          isNull,
          reason: value,
        );
      }
    },
  );

  test('weight follows exclusive backend boundaries and precision', () {
    expect(MeasurementsValidation.validateWeightText('2'), isNotNull);
    expect(MeasurementsValidation.validateWeightText('2.01'), isNull);
    expect(MeasurementsValidation.validateWeightText('2.1'), isNull);
    expect(MeasurementsValidation.validateWeightText('70,50'), isNull);
    expect(MeasurementsValidation.validateWeightText('70.123'), isNotNull);
    expect(MeasurementsValidation.validateWeightText('700'), isNotNull);
    expect(MeasurementsValidation.validateWeightText(''), isNotNull);
  });

  test('body fat allows null and inclusive zero-to-one-hundred range', () {
    expect(MeasurementsValidation.validateOptionalBodyFatText(''), isNull);
    expect(MeasurementsValidation.validateOptionalBodyFatText('0'), isNull);
    expect(MeasurementsValidation.validateOptionalBodyFatText('100'), isNull);
    expect(
      MeasurementsValidation.validateOptionalBodyFatText('-0.1'),
      isNotNull,
    );
    expect(
      MeasurementsValidation.validateOptionalBodyFatText('100.1'),
      isNotNull,
    );
    expect(
      MeasurementsValidation.validateOptionalBodyFatText('18.55'),
      isNotNull,
    );
  });

  test('waist allows null and uses exclusive backend boundaries', () {
    expect(MeasurementsValidation.validateOptionalWaistText(''), isNull);
    expect(MeasurementsValidation.validateOptionalWaistText('10'), isNotNull);
    expect(MeasurementsValidation.validateOptionalWaistText('10.1'), isNull);
    expect(MeasurementsValidation.validateOptionalWaistText('399.9'), isNull);
    expect(MeasurementsValidation.validateOptionalWaistText('400'), isNotNull);
    expect(
      MeasurementsValidation.validateOptionalWaistText('82.55'),
      isNotNull,
    );
  });

  test('note length and measured date validation mirror backend', () {
    expect(MeasurementsValidation.validateNote('x' * 255), isNull);
    expect(MeasurementsValidation.validateNote('x' * 256), isNotNull);
    expect(
      MeasurementsValidation.validateMeasuredOn(
        DateTime(2026, 9, 18),
        today: DateTime(2026, 9, 17),
      ),
      isNotNull,
    );
    expect(
      MeasurementsValidation.validateMeasuredOn(
        DateTime(2026, 9, 17),
        today: DateTime(2026, 9, 17),
      ),
      isNull,
    );
  });

  test('measured date uses backend UTC day at the Vietnam boundary', () {
    final instant = DateTime.parse('2026-09-17T18:30:00Z');
    final today = backendUtcToday(now: instant);

    expect(
      formatMeasurementDate(instant.toUtc().add(const Duration(hours: 7))),
      '2026-09-18',
    );
    expect(today, DateTime.utc(2026, 9, 17));
    expect(
      MeasurementsValidation.validateMeasuredOn(
        DateTime(2026, 9, 17),
        today: today,
      ),
      isNull,
    );
    expect(
      MeasurementsValidation.validateMeasuredOn(
        DateTime(2026, 9, 18),
        today: today,
      ),
      isNotNull,
    );
  });
}
