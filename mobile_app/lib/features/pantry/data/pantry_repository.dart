import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';

abstract interface class PantryRepository {
  Future<List<PantryItem>> getPantry({bool includeClosed = false});

  Future<PantryItem> getPantryItem(String publicId);

  Future<PantryItem> createPantryItem(PantryCreateRequest request);

  Future<PantryItem> updateMetadata(
    String publicId,
    PantryMetadataUpdate request,
  );

  Future<PantryItem> adjust(
    String publicId,
    PantryQuantityAdjustment request,
  );

  Future<PantryItem> consume(
    String publicId,
    PantryQuantityConsumption request,
  );

  Future<PantryItem> discard(String publicId, {String? note});
}

final class HttpPantryRepository implements PantryRepository {
  HttpPantryRepository(this._apiClient);

  static const _path = '/api/v1/me/pantry';
  final ApiClient _apiClient;

  @override
  Future<List<PantryItem>> getPantry({bool includeClosed = false}) async {
    final path = includeClosed
        ? Uri(path: _path, queryParameters: {'includeClosed': 'true'}).toString()
        : _path;
    final response = await _apiClient.requestJson(
      path,
      authenticated: true,
    );
    return CatalogJson.list(response, PantryItem.fromJson);
  }

  @override
  Future<PantryItem> getPantryItem(String publicId) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      authenticated: true,
    );
    return PantryItem.fromJson(response);
  }

  @override
  Future<PantryItem> createPantryItem(PantryCreateRequest request) async {
    final response = await _apiClient.requestJson(
      _path,
      method: 'POST',
      body: request.toJson(),
      authenticated: true,
    );
    return PantryItem.fromJson(response);
  }

  @override
  Future<PantryItem> updateMetadata(
    String publicId,
    PantryMetadataUpdate request,
  ) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}',
      method: 'PUT',
      body: request.toJson(),
      authenticated: true,
    );
    return PantryItem.fromJson(response);
  }

  @override
  Future<PantryItem> adjust(
    String publicId,
    PantryQuantityAdjustment request,
  ) async => _postItem(
    publicId,
    'adjust',
    request.toJson(),
  );

  @override
  Future<PantryItem> consume(
    String publicId,
    PantryQuantityConsumption request,
  ) async => _postItem(
    publicId,
    'consume',
    request.toJson(),
  );

  @override
  Future<PantryItem> discard(String publicId, {String? note}) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}/discard',
      method: 'POST',
      body: note == null ? null : {'note': note},
      authenticated: true,
    );
    return PantryItem.fromJson(response);
  }

  Future<PantryItem> _postItem(
    String publicId,
    String operation,
    Map<String, Object?> body,
  ) async {
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(_requiredPublicId(publicId))}/$operation',
      method: 'POST',
      body: body,
      authenticated: true,
    );
    return PantryItem.fromJson(response);
  }

  static String _requiredPublicId(String publicId) {
    final value = publicId.trim();
    if (value.isEmpty || value.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    return value;
  }
}
