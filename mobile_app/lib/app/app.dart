import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:smart_meal_planner/app/app_router.dart';
import 'package:smart_meal_planner/app/config/app_config.dart';
import 'package:smart_meal_planner/app/app_session_factory.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';

class SmartMealPlannerApp extends StatefulWidget {
  const SmartMealPlannerApp({
    super.key,
    this.sessionController,
    this.profileController,
    this.preferencesController,
  });

  final SessionController? sessionController;
  final ProfileController? profileController;
  final PreferencesController? preferencesController;

  @override
  State<SmartMealPlannerApp> createState() => _SmartMealPlannerAppState();
}

class _SmartMealPlannerAppState extends State<SmartMealPlannerApp> {
  late final SessionController _sessionController;
  late final ProfileController? _profileController;
  late final PreferencesController? _preferencesController;
  late final AppRouter _appRouter;

  @override
  void initState() {
    super.initState();
    if (widget.sessionController == null) {
      final dependencies = AppSessionFactory.create();
      _sessionController = dependencies.sessionController;
      _profileController = dependencies.profileController;
      _preferencesController = dependencies.preferencesController;
    } else {
      _sessionController = widget.sessionController!;
      _profileController = widget.profileController;
      _preferencesController = widget.preferencesController;
    }
    _appRouter = AppRouter(
      _sessionController,
      profileController: _profileController,
      preferencesController: _preferencesController,
    );
    _sessionController.bootstrap();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: AppConfig.appName,
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      locale: const Locale('vi', 'VN'),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('vi', 'VN')],
      routerConfig: _appRouter.router,
    );
  }
}
