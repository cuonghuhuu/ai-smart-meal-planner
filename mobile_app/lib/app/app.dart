import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/app_session_factory.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';

class SmartMealPlannerApp extends StatefulWidget {
  const SmartMealPlannerApp({super.key, this.sessionController});

  final SessionController? sessionController;

  @override
  State<SmartMealPlannerApp> createState() => _SmartMealPlannerAppState();
}

class _SmartMealPlannerAppState extends State<SmartMealPlannerApp> {
  late final SessionController _sessionController;
  late final AppRouter _appRouter;

  @override
  void initState() {
    super.initState();
    _sessionController = widget.sessionController ?? AppSessionFactory.create();
    _appRouter = AppRouter(_sessionController);
    _sessionController.bootstrap();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      routerConfig: _appRouter.router,
    );
  }
}
