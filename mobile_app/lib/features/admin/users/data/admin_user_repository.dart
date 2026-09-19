import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/features/admin/users/data/admin_user_models.dart';

abstract interface class AdminUserRepository {
  Future<AdminUserPage> getUsers({
    String? query,
    String? status,
    int page = 0,
    int size = 20,
  });

  Future<AdminUserItem> getUser(String publicId);

  Future<AdminUserItem> changeStatus(String publicId, String status);
}

final class HttpAdminUserRepository implements AdminUserRepository {
  HttpAdminUserRepository(this._apiClient);

  static const _path = '/api/v1/admin/users';
  final ApiClient _apiClient;

  @override
  Future<AdminUserPage> getUsers({
    String? query,
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    _validatePage(page, size);
    final parameters = <String, String>{'page': '$page', 'size': '$size'};
    final normalizedQuery = query?.trim() ?? '';
    final normalizedStatus = status?.trim() ?? '';
    if (normalizedQuery.isNotEmpty) parameters['q'] = normalizedQuery;
    if (normalizedStatus.isNotEmpty) parameters['status'] = normalizedStatus;
    final response = await _apiClient.requestJson(
      Uri(path: _path, queryParameters: parameters).toString(),
      authenticated: true,
    );
    return AdminUserPage.fromJson(response);
  }

  @override
  Future<AdminUserItem> getUser(String publicId) async {
    final value = _requiredPublicId(publicId);
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(value)}',
      authenticated: true,
    );
    return AdminUserItem.fromJson(response);
  }

  @override
  Future<AdminUserItem> changeStatus(String publicId, String status) async {
    final value = _requiredPublicId(publicId);
    final response = await _apiClient.requestJson(
      '$_path/${Uri.encodeComponent(value)}/status',
      method: 'PATCH',
      body: {'status': status},
      authenticated: true,
    );
    return AdminUserItem.fromJson(response);
  }

  static String _requiredPublicId(String publicId) {
    final value = publicId.trim();
    if (value.isEmpty || value.contains('/')) {
      throw ArgumentError.value(publicId, 'publicId');
    }
    return value;
  }

  static void _validatePage(int page, int size) {
    if (page < 0) {
      throw ArgumentError.value(page, 'page', 'must be non-negative');
    }
    if (size < 1 || size > 100) {
      throw ArgumentError.value(size, 'size', 'must be between 1 and 100');
    }
  }
}
