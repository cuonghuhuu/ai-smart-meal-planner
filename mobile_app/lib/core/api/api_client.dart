import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/core/api/http_client_factory.dart';

class ApiClient {
  ApiClient({
    http.Client? httpClient,
    String? baseUrl,
    this.timeout = const Duration(seconds: 10),
  }) : _httpClient = httpClient ?? createPlatformHttpClient(),
       _baseUri = Uri.parse(baseUrl ?? AppConfig.apiBaseUrl);

  final http.Client _httpClient;
  final Uri _baseUri;
  final Duration timeout;

  String? Function()? _accessTokenProvider;
  Future<bool> Function()? _refreshAccessToken;

  void configureAuthentication({
    required String? Function() accessTokenProvider,
    required Future<bool> Function() refreshAccessToken,
  }) {
    _accessTokenProvider = accessTokenProvider;
    _refreshAccessToken = refreshAccessToken;
  }

  Future<Map<String, dynamic>> getJson(String path) async {
    final response = await requestJson(path);
    if (response is! Map<String, dynamic>) {
      throw const ApiResponseFormatException();
    }
    return response;
  }

  Future<Object?> requestJson(
    String path, {
    String method = 'GET',
    Object? body,
    bool authenticated = false,
    bool allowAuthenticationRetry = true,
    bool expectJson = true,
    Map<String, String> headers = const {},
  }) => _requestJson(
    path,
    method: method,
    body: body,
    authenticated: authenticated,
    allowAuthenticationRetry: allowAuthenticationRetry,
    expectJson: expectJson,
    headers: headers,
    retried: false,
  );

  Future<Object?> _requestJson(
    String path, {
    required String method,
    required Object? body,
    required bool authenticated,
    required bool allowAuthenticationRetry,
    required bool expectJson,
    required Map<String, String> headers,
    required bool retried,
  }) async {
    final requestHeaders = <String, String>{
      'Accept': 'application/json, application/problem+json',
      ...headers,
    };

    if (body != null) {
      requestHeaders['Content-Type'] = 'application/json';
    }

    if (authenticated) {
      final token = _accessTokenProvider?.call();
      if (token != null && token.isNotEmpty) {
        requestHeaders['Authorization'] = 'Bearer $token';
      }
    }

    final http.Response response;

    try {
      final request = http.Request(method, _resolve(path))
        ..headers.addAll(requestHeaders);
      if (body != null) {
        request.body = jsonEncode(body);
      }
      final streamed = await _httpClient.send(request).timeout(timeout);
      response = await http.Response.fromStream(streamed);
    } on TimeoutException {
      throw const ApiTransportException(ApiTransportFailureKind.timeout);
    } on http.ClientException {
      throw const ApiTransportException(ApiTransportFailureKind.network);
    } catch (_) {
      throw const ApiTransportException(ApiTransportFailureKind.transport);
    }

    if (response.statusCode == 401 &&
        authenticated &&
        allowAuthenticationRetry &&
        !retried &&
        await (_refreshAccessToken?.call() ?? Future.value(false))) {
      return _requestJson(
        path,
        method: method,
        body: body,
        authenticated: authenticated,
        allowAuthenticationRetry: false,
        expectJson: expectJson,
        headers: headers,
        retried: true,
      );
    }

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiHttpException(response.statusCode, problem: _problem(response));
    }

    if (!expectJson || response.statusCode == 204 || response.body.isEmpty) {
      return null;
    }

    try {
      final decoded = jsonDecode(response.body);
      return decoded;
    } on FormatException {
      throw const ApiResponseFormatException();
    }
  }

  ApiProblem? _problem(http.Response response) {
    try {
      final decoded = jsonDecode(response.body);
      return decoded is Map<String, dynamic>
          ? ApiProblem.fromJson(decoded)
          : null;
    } on FormatException {
      return null;
    }
  }

  Uri _resolve(String path) {
    return _baseUri.resolve(path.startsWith('/') ? path : '/$path');
  }
}
