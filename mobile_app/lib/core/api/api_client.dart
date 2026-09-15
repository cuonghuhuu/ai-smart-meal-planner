import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';

class ApiClient {
  ApiClient({
    http.Client? httpClient,
    String? baseUrl,
    this.timeout = const Duration(seconds: 10),
  }) : _httpClient = httpClient ?? http.Client(),
       _baseUri = Uri.parse(baseUrl ?? AppConfig.apiBaseUrl);

  final http.Client _httpClient;
  final Uri _baseUri;
  final Duration timeout;

  Future<Map<String, dynamic>> getJson(String path) async {
    final http.Response response;

    try {
      response = await _httpClient
          .get(_resolve(path), headers: const {'Accept': 'application/json'})
          .timeout(timeout);
    } on TimeoutException {
      throw const ApiTransportException(ApiTransportFailureKind.timeout);
    } on http.ClientException {
      throw const ApiTransportException(ApiTransportFailureKind.network);
    } catch (_) {
      throw const ApiTransportException(ApiTransportFailureKind.transport);
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiHttpException(response.statusCode);
    }

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const ApiResponseFormatException();
      }
      return decoded;
    } on FormatException {
      throw const ApiResponseFormatException();
    }
  }

  Uri _resolve(String path) {
    return _baseUri.resolve(path.startsWith('/') ? path : '/$path');
  }
}
