import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';

void main() {
  test('parses a complete measurement response', () {
    final measurement = BodyMeasurement.fromJson({
      'measuredOn': '2026-09-17',
      'weightKg': 70.5,
      'bodyFatPercent': 18.5,
      'waistCm': 82.0,
      'source': 'USER_ENTERED',
      'note': 'Morning measurement',
      'createdAt': '2026-09-17T06:30:00Z',
    });

    expect(measurement.measuredOn, DateTime(2026, 9, 17));
    expect(measurement.weightKg, 70.5);
    expect(measurement.bodyFatPercent, 18.5);
    expect(measurement.waistCm, 82);
    expect(measurement.source.wireValue, 'USER_ENTERED');
    expect(measurement.source.displayName, 'Người dùng nhập');
    expect(measurement.note, 'Morning measurement');
    expect(measurement.createdAt, DateTime.parse('2026-09-17T06:30:00Z'));
  });

  test('keeps optional fields nullable and unknown source safe', () {
    final measurement = BodyMeasurement.fromJson({
      'measuredOn': '2026-09-16',
      'weightKg': 71,
      'bodyFatPercent': null,
      'waistCm': null,
      'source': 'DEVICE_X',
      'note': null,
      'createdAt': '2026-09-16T06:30:00Z',
    });

    expect(measurement.bodyFatPercent, isNull);
    expect(measurement.waistCm, isNull);
    expect(measurement.note, isNull);
    expect(measurement.source.wireValue, 'DEVICE_X');
    expect(measurement.source.displayName, 'DEVICE_X');
    expect(MeasurementSource.imported.wireValue, 'IMPORTED');
    expect(MeasurementSource.corrected.wireValue, 'CORRECTED');
  });

  test('record and update JSON preserve numeric values and API keys', () {
    final draft = MeasurementDraft(
      measuredOn: DateTime(2026, 9, 17),
      weightKg: 70.5,
      bodyFatPercent: null,
      waistCm: 82.5,
      note: '  note  ',
    );

    expect(draft.toRecordJson(), {
      'measuredOn': '2026-09-17',
      'weightKg': 70.5,
      'bodyFatPercent': null,
      'waistCm': 82.5,
      'note': 'note',
    });
    expect(draft.toUpdateJson(), {
      'weightKg': 70.5,
      'bodyFatPercent': null,
      'waistCm': 82.5,
      'note': 'note',
    });
    expect(draft.toUpdateJson().containsKey('measuredOn'), isFalse);
  });

  test('measurement dates use strict ISO calendar serialization', () {
    expect(formatMeasurementDate(DateTime(2026, 1, 2)), '2026-01-02');
    expect(parseMeasurementDate('2026-01-02'), DateTime(2026, 1, 2));
    expect(() => parseMeasurementDate('2026-2-2'), throwsA(isA<Exception>()));
    expect(() => parseMeasurementDate('2026-02-30'), throwsA(isA<Exception>()));
  });

  test('backend UTC today uses UTC calendar components', () {
    final instant = DateTime.parse('2026-09-17T18:30:00Z');

    expect(backendUtcToday(now: instant), DateTime.utc(2026, 9, 17));
  });
}
