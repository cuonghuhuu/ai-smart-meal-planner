import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/foundation/data/backend_health_service.dart';
import 'package:smart_meal_planner/features/foundation/presentation/foundation_page.dart';

class SmartMealPlannerApp extends StatelessWidget {
  const SmartMealPlannerApp({super.key, this.backendHealthChecker});

  final BackendHealthChecker? backendHealthChecker;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      home: FoundationPage(backendHealthChecker: backendHealthChecker),
    );
  }
}
