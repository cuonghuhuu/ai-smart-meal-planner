/// Safe, typed failures raised by [ApiClient] transport and JSON handling.
sealed class ApiException implements Exception {
  const ApiException();
}

enum ApiTransportFailureKind { timeout, network, transport }

final class ApiTransportException extends ApiException {
  const ApiTransportException(this.kind);

  final ApiTransportFailureKind kind;
}

final class ApiHttpException extends ApiException {
  const ApiHttpException(this.statusCode, {this.problem});

  final int statusCode;
  final ApiProblem? problem;
}

final class ApiResponseFormatException extends ApiException {
  const ApiResponseFormatException();
}

/// Safe subset of the backend's RFC 9457 problem response.
final class ApiProblem {
  const ApiProblem({
    required this.status,
    required this.code,
    required this.detail,
    this.requestId,
  });

  final int? status;
  final String? code;
  final String? detail;
  final String? requestId;

  factory ApiProblem.fromJson(Map<String, dynamic> json) => ApiProblem(
    status: json['status'] as int?,
    code: json['code'] as String?,
    detail: json['detail'] as String?,
    requestId: json['requestId'] as String?,
  );
}
