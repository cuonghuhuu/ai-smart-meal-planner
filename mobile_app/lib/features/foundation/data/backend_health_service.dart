import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';

abstract interface class BackendHealthChecker {
  Future<BackendHealth> checkHealth();
}

class BackendHealthService implements BackendHealthChecker {
  BackendHealthService({ApiClient? apiClient})
    : _apiClient = apiClient ?? ApiClient();

  final ApiClient _apiClient;

  @override
  Future<BackendHealth> checkHealth() async {
    final response = await _apiClient.getJson('/actuator/health');
    final status = response['status'];

    if (status is! String || status.trim().isEmpty) {
      throw const ApiResponseFormatException();
    }

    return BackendHealth(status: status);
  }
}

class BackendHealth {
  const BackendHealth({required this.status});

  final String status;

  bool get isUp => status == 'UP';
}
