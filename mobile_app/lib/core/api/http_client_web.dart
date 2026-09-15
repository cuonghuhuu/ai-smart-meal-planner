import 'package:http/browser_client.dart';
import 'package:http/http.dart' as http;

/// Enables browser credential handling for the backend's HttpOnly Web cookie.
http.Client createPlatformHttpClient() =>
    BrowserClient()..withCredentials = true;
