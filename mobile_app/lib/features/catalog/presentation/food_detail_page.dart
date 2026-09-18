import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_localizations.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_widgets.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class FoodDetailPage extends StatefulWidget {
  const FoodDetailPage({
    super.key,
    required this.sessionController,
    required this.controller,
    required this.publicId,
  });

  final SessionController sessionController;
  final FoodCatalogController controller;
  final String publicId;

  @override
  State<FoodDetailPage> createState() => _FoodDetailPageState();
}

class _FoodDetailPageState extends State<FoodDetailPage> {
  FoodCatalogController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onControllerChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _controller.loadDetail(widget.publicId);
    });
  }

  @override
  void didUpdateWidget(covariant FoodDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.publicId != widget.publicId) {
      _controller.loadDetail(widget.publicId);
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 0,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.detailState;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: ValueKey('food-detail-${widget.publicId}'),
          children: [
            _backButton(context),
            if (state.status == CatalogDetailStatus.loading)
              const Padding(
                padding: EdgeInsets.all(32),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (state.status == CatalogDetailStatus.error)
              CatalogErrorPanel(
                message: state.errorMessage ?? AppStrings.catalogDetailLoadFailed,
                retryKey: const ValueKey('food-detail-retry'),
                onRetry: _controller.reloadDetail,
              )
            else if (state.item != null)
              ..._detailChildren(context, state.item!)
            else
              const CatalogEmptyPanel(message: AppStrings.catalogDetailLoadFailed),
          ],
        ),
      ),
    );
  }

  Widget _backButton(BuildContext context) => Align(
    alignment: Alignment.centerLeft,
    child: TextButton.icon(
      key: const ValueKey('food-detail-back'),
      onPressed: () => context.canPop()
          ? context.pop()
          : context.go('/catalog/foods'),
      icon: const Icon(Icons.arrow_back),
      label: const Text(AppStrings.catalogBack),
    ),
  );

  List<Widget> _detailChildren(
    BuildContext context,
    FoodCatalogDetail food,
  ) => [
    Text(
      food.displayName,
      style: Theme.of(context).textTheme.headlineMedium,
    ),
    const SizedBox(height: 8),
    Text(CatalogLocalizations.categoryName(food.category)),
    const SizedBox(height: 4),
    Text(food.code, style: Theme.of(context).textTheme.bodySmall),
    if (food.brand != null && food.brand!.isNotEmpty)
      catalogDetailLine(AppStrings.catalogBrand, food.brand!),
    if (food.description != null && food.description!.isNotEmpty) ...[
      const SizedBox(height: 16),
      Text(
        AppStrings.catalogDescription,
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 4),
      Text(food.description!),
    ],
    const SizedBox(height: 16),
    Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.catalogNutritionBasis,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text(
              food.nutritionBasis == 'PER_100_G'
                  ? AppStrings.catalogNutritionPer100g
                  : CatalogLocalizations.enumName(food.nutritionBasis),
            ),
            if (food.densityGPerMl != null)
              catalogDetailLine(
                AppStrings.catalogUnit,
                '${CatalogLocalizations.formatNumber(food.densityGPerMl!)} g/ml',
              ),
            if (food.revision != null)
              catalogDetailLine(
                AppStrings.catalogRevision,
                '${food.revision}',
              ),
          ],
        ),
      ),
    ),
    const SizedBox(height: 16),
    _nutrientSection(context, food.nutrients),
    const SizedBox(height: 16),
    _servingSection(context, food.servings),
    const SizedBox(height: 16),
    _sourceSection(context, food),
  ];

  Widget _nutrientSection(BuildContext context, List<FoodNutrient> nutrients) {
    if (nutrients.isEmpty) {
      return const CatalogEmptyPanel(message: AppStrings.catalogNoNutrients);
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.catalogNutrition,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            for (final nutrient in nutrients)
              catalogDetailLine(
                CatalogLocalizations.nutrientName(nutrient),
                '${CatalogLocalizations.formatNumber(nutrient.amount)} '
                '${nutrient.unitDisplayName} (${nutrient.unitCode})'
                '${nutrient.dataQuality == null ? '' : ' · ${CatalogLocalizations.enumName(nutrient.dataQuality)}'}',
              ),
          ],
        ),
      ),
    );
  }

  Widget _servingSection(BuildContext context, List<FoodServing> servings) {
    if (servings.isEmpty) {
      return const CatalogEmptyPanel(message: AppStrings.catalogNoServings);
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.catalogServings,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            for (final serving in servings)
              catalogDetailLine(
                serving.displayName,
                _servingValue(serving),
              ),
          ],
        ),
      ),
    );
  }

  Widget _sourceSection(BuildContext context, FoodCatalogDetail food) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.catalogSource,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          catalogDetailLine(
            AppStrings.catalogSource,
            CatalogLocalizations.enumName(food.source),
          ),
          if (food.sourceReference != null && food.sourceReference!.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                food.sourceReference!,
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ),
        ],
      ),
    ),
  );

  String _servingValue(FoodServing serving) {
    final parts = <String>[];
    if (serving.quantity != null) {
      parts.add(
        '${CatalogLocalizations.formatNumber(serving.quantity!)} ${serving.unitDisplayName}',
      );
    }
    if (serving.gramWeight != null) {
      parts.add('${CatalogLocalizations.formatNumber(serving.gramWeight!)} g');
    }
    if (serving.milliliters != null) {
      parts.add('${CatalogLocalizations.formatNumber(serving.milliliters!)} ml');
    }
    if (serving.defaultServing) {
      parts.add(AppStrings.catalogDefault);
    }
    return parts.isEmpty ? serving.unitCode : parts.join(' · ');
  }
}
