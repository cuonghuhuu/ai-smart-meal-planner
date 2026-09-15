import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/app_session_factory.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/session_gate.dart';

class SmartMealPlannerApp extends StatefulWidget {
  const SmartMealPlannerApp({super.key, this.sessionController});

  final SessionController? sessionController;

  @override
  State<SmartMealPlannerApp> createState() => _SmartMealPlannerAppState();
}

class _SmartMealPlannerAppState extends State<SmartMealPlannerApp> {
  late final SessionController _sessionController;

  @override
  void initState() {
    super.initState();
    _sessionController = widget.sessionController ?? AppSessionFactory.create();
    _sessionController.bootstrap();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      home: SessionGate(sessionController: _sessionController),
    );
  }
}
