import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/app_session_factory.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';

class SmartMealPlannerApp extends StatefulWidget {
  const SmartMealPlannerApp({
    super.key,
    this.sessionController,
    this.profileController,
  });

  final SessionController? sessionController;
  final ProfileController? profileController;

  @override
  State<SmartMealPlannerApp> createState() => _SmartMealPlannerAppState();
}

class _SmartMealPlannerAppState extends State<SmartMealPlannerApp> {
  late final SessionController _sessionController;
  late final ProfileController? _profileController;
  late final AppRouter _appRouter;

  @override
  void initState() {
    super.initState();
    if (widget.sessionController == null) {
      final dependencies = AppSessionFactory.create();
      _sessionController = dependencies.sessionController;
      _profileController = dependencies.profileController;
    } else {
      _sessionController = widget.sessionController!;
      _profileController = widget.profileController;
    }
    _appRouter = AppRouter(
      _sessionController,
      profileController: _profileController,
    );
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
