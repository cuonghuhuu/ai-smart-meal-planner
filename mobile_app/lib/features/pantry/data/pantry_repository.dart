import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';

abstract interface class PantryRepository {
  Future<List<PantryItem>> list({bool includeClosed = false});

  Future<PantryItem> getByPublicId(String publicId);

  Future<PantryItem> create(CreatePantryItemRequest request);

  Future<PantryItem> updateMetadata(
    String publicId,
    UpdatePantryMetadataRequest request,
  );

  Future<PantryItem> adjust(String publicId, AdjustPantryItemRequest request);

  Future<PantryItem> consume(String publicId, ConsumePantryItemRequest request);

  Future<PantryItem> discard(String publicId, DiscardPantryItemRequest request);
}

final class HttpPantryRepository implements PantryRepository {
  HttpPantryRepository(this._apiClient, {this.csrfTokenProvider});

  static const _basePath = '/api/v1/me/pantry';

  final ApiClient _apiClient;
  final Future<CsrfToken> Function()? csrfTokenProvider;

  @override
  Future<List<PantryItem>> list({bool includeClosed = false}) async {
    final response = await _apiClient.requestJson(
      includeClosed ? '$_basePath?includeClosed=true' : _basePath,
      authenticated: true,
    );
    if (response is! List) throw const ApiResponseFormatException();
    return [for (final value in response) PantryItem.fromJson(value)];
  }

  @override
  Future<PantryItem> getByPublicId(String publicId) async =>
      PantryItem.fromJson(
        await _apiClient.requestJson(_itemPath(publicId), authenticated: true),
      );

  @override
  Future<PantryItem> create(CreatePantryItemRequest request) =>
      _mutate(_basePath, method: 'POST', body: request.toJson());

  @override
  Future<PantryItem> updateMetadata(
    String publicId,
    UpdatePantryMetadataRequest request,
  ) => _mutate(_itemPath(publicId), method: 'PUT', body: request.toJson());

  @override
  Future<PantryItem> adjust(String publicId, AdjustPantryItemRequest request) =>
      _mutate(
        '${_itemPath(publicId)}/adjust',
        method: 'POST',
        body: request.toJson(),
      );

  @override
  Future<PantryItem> consume(
    String publicId,
    ConsumePantryItemRequest request,
  ) => _mutate(
    '${_itemPath(publicId)}/consume',
    method: 'POST',
    body: request.toJson(),
  );

  @override
  Future<PantryItem> discard(
    String publicId,
    DiscardPantryItemRequest request,
  ) => _mutate(
    '${_itemPath(publicId)}/discard',
    method: 'POST',
    body: request.toJson(),
  );

  Future<PantryItem> _mutate(
    String path, {
    required String method,
    Object? body,
  }) async {
    final csrf = await csrfTokenProvider?.call();
    final response = await _apiClient.requestJson(
      path,
      method: method,
      body: body,
      authenticated: true,
      headers: csrf == null ? const {} : {csrf.headerName: csrf.value},
    );
    return PantryItem.fromJson(response);
  }

  static String _itemPath(String publicId) =>
      '$_basePath/${Uri.encodeComponent(PantryPublicId.normalize(publicId))}';
}
