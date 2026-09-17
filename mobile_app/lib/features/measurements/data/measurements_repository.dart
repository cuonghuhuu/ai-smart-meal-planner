import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';

abstract interface class MeasurementsRepository {
  Future<BodyMeasurement?> getLatestMeasurement();

  Future<List<BodyMeasurement>> getMeasurementHistory({
    int page = 0,
    int size = 20,
    DateTime? from,
    DateTime? to,
  });

  Future<BodyMeasurement> recordMeasurement(MeasurementDraft draft);

  Future<BodyMeasurement> updateMeasurement(MeasurementDraft draft);
}

final class HttpMeasurementsRepository implements MeasurementsRepository {
  HttpMeasurementsRepository(this._apiClient);

  static const _basePath = '/api/v1/me/measurements';

  final ApiClient _apiClient;

  @override
  Future<BodyMeasurement?> getLatestMeasurement() async {
    try {
      final response = await _apiClient.requestJson(
        '$_basePath/latest',
        authenticated: true,
      );
      return _measurement(response);
    } on ApiHttpException catch (error) {
      if (error.statusCode == 404) {
        return null;
      }
      rethrow;
    }
  }

  @override
  Future<List<BodyMeasurement>> getMeasurementHistory({
    int page = 0,
    int size = 20,
    DateTime? from,
    DateTime? to,
  }) async {
    if (page < 0) {
      throw ArgumentError.value(page, 'page', 'must be non-negative');
    }
    if (size < 1 || size > 100) {
      throw ArgumentError.value(size, 'size', 'must be between 1 and 100');
    }
    if ((from == null) != (to == null)) {
      throw ArgumentError('from and to must be supplied together');
    }
    if (from != null && to != null && from.isAfter(to)) {
      throw ArgumentError('from must not be after to');
    }

    final query = <String, String>{'page': '$page', 'size': '$size'};
    if (from != null && to != null) {
      query['from'] = formatMeasurementDate(from);
      query['to'] = formatMeasurementDate(to);
    }
    final path = Uri(path: _basePath, queryParameters: query).toString();
    final response = await _apiClient.requestJson(path, authenticated: true);
    if (response is! List) {
      throw const ApiResponseFormatException();
    }
    return response.map(_measurement).toList(growable: false);
  }

  @override
  Future<BodyMeasurement> recordMeasurement(MeasurementDraft draft) async {
    final response = await _apiClient.requestJson(
      _basePath,
      method: 'POST',
      body: draft.toRecordJson(),
      authenticated: true,
    );
    return _measurement(response);
  }

  @override
  Future<BodyMeasurement> updateMeasurement(MeasurementDraft draft) async {
    final response = await _apiClient.requestJson(
      '$_basePath/${formatMeasurementDate(draft.measuredOn)}',
      method: 'PUT',
      body: draft.toUpdateJson(),
      authenticated: true,
    );
    return _measurement(response);
  }
}

BodyMeasurement _measurement(Object? response) {
  if (response is! Map<String, dynamic>) {
    throw const ApiResponseFormatException();
  }
  return BodyMeasurement.fromJson(response);
}
