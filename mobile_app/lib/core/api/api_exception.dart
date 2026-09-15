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
  const ApiHttpException(this.statusCode);

  final int statusCode;
}

final class ApiResponseFormatException extends ApiException {
  const ApiResponseFormatException();
}
