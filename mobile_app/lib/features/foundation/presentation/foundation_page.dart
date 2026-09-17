import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/foundation/data/backend_health_service.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class FoundationPage extends StatefulWidget {
  const FoundationPage({super.key, this.backendHealthChecker});

  final BackendHealthChecker? backendHealthChecker;

  @override
  State<FoundationPage> createState() => _FoundationPageState();
}

class _FoundationPageState extends State<FoundationPage> {
  late final BackendHealthChecker _backendHealthChecker;
  late Future<BackendHealth> _healthCheck;

  @override
  void initState() {
    super.initState();
    _backendHealthChecker =
        widget.backendHealthChecker ?? BackendHealthService();
    _healthCheck = _backendHealthChecker.checkHealth();
  }

  void _retryHealthCheck() {
    setState(() {
      _healthCheck = _backendHealthChecker.checkHealth();
    });
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text(AppStrings.productName)),
      body: ResponsiveContent(
        child: Align(
          alignment: Alignment.topLeft,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(AppStrings.productName, style: textTheme.headlineMedium),
              const SizedBox(height: 12),
              Text(AppStrings.foundationReady, style: textTheme.titleMedium),
              const SizedBox(height: 32),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        AppStrings.runtimeConfiguration,
                        style: textTheme.titleLarge,
                      ),
                      const SizedBox(height: 16),
                      _ConfigurationValue(
                        label: AppStrings.environment,
                        value: AppConfig.environment,
                        valueColor: colorScheme.primary,
                      ),
                      const SizedBox(height: 12),
                      _ConfigurationValue(
                        label: AppStrings.apiUrl,
                        value: AppConfig.apiBaseUrl,
                        valueColor: colorScheme.primary,
                      ),
                      const SizedBox(height: 24),
                      Text(
                        AppStrings.backendConnection,
                        style: textTheme.titleLarge,
                      ),
                      const SizedBox(height: 12),
                      FutureBuilder<BackendHealth>(
                        future: _healthCheck,
                        builder: (context, snapshot) {
                          if (snapshot.connectionState !=
                              ConnectionState.done) {
                            return const _CheckingConnection();
                          }

                          final health = snapshot.data;
                          if (snapshot.hasError ||
                              health == null ||
                              !health.isUp) {
                            return _UnavailableConnection(
                              onRetry: _retryHealthCheck,
                            );
                          }

                          return const _ConnectedConnection();
                        },
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CheckingConnection extends StatelessWidget {
  const _CheckingConnection();

  @override
  Widget build(BuildContext context) {
    return const Row(
      children: [
        SizedBox(
          width: 18,
          height: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        SizedBox(width: 12),
        Text(AppStrings.checking),
      ],
    );
  }
}

class _ConnectedConnection extends StatelessWidget {
  const _ConnectedConnection();

  @override
  Widget build(BuildContext context) {
    return const Text(AppStrings.connectedUp);
  }
}

class _UnavailableConnection extends StatelessWidget {
  const _UnavailableConnection({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        const Expanded(child: Text(AppStrings.backendUnavailable)),
        TextButton(
          key: const Key('retry-backend-health'),
          onPressed: onRetry,
          child: const Text(AppStrings.retry),
        ),
      ],
    );
  }
}

class _ConfigurationValue extends StatelessWidget {
  const _ConfigurationValue({
    required this.label,
    required this.value,
    required this.valueColor,
  });

  final String label;
  final String value;
  final Color valueColor;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: textTheme.labelLarge),
        const SizedBox(height: 4),
        SelectableText(
          value,
          style: textTheme.bodyLarge?.copyWith(color: valueColor),
        ),
      ],
    );
  }
}
