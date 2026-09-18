import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_localizations.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_widgets.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class IngredientDetailPage extends StatefulWidget {
  const IngredientDetailPage({
    super.key,
    required this.sessionController,
    required this.controller,
    required this.publicId,
  });

  final SessionController sessionController;
  final IngredientCatalogController controller;
  final String publicId;

  @override
  State<IngredientDetailPage> createState() => _IngredientDetailPageState();
}

class _IngredientDetailPageState extends State<IngredientDetailPage> {
  IngredientCatalogController get _controller => widget.controller;

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
  void didUpdateWidget(covariant IngredientDetailPage oldWidget) {
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
    selectedIndex: 1,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.detailState;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: ValueKey('ingredient-detail-${widget.publicId}'),
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
                retryKey: const ValueKey('ingredient-detail-retry'),
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
      key: const ValueKey('ingredient-detail-back'),
      onPressed: () => context.canPop()
          ? context.pop()
          : context.go('/catalog/ingredients'),
      icon: const Icon(Icons.arrow_back),
      label: const Text(AppStrings.catalogBack),
    ),
  );

  List<Widget> _detailChildren(
    BuildContext context,
    IngredientCatalogDetail ingredient,
  ) => [
    Text(
      ingredient.displayName,
      key: const ValueKey('ingredient-detail-name'),
      style: Theme.of(context).textTheme.headlineMedium,
    ),
    const SizedBox(height: 8),
    Text(CatalogLocalizations.categoryName(ingredient.category)),
    const SizedBox(height: 4),
    Text(ingredient.code, style: Theme.of(context).textTheme.bodySmall),
    if (ingredient.staple) ...[
      const SizedBox(height: 8),
      const Chip(label: Text(AppStrings.ingredientStaple)),
    ],
    if (ingredient.defaultUnitCode != null) ...[
      const SizedBox(height: 16),
      catalogDetailLine(
        AppStrings.ingredientDefaultUnit,
        ingredient.defaultUnitDisplayName == null
            ? ingredient.defaultUnitCode!
            : '${ingredient.defaultUnitDisplayName} (${ingredient.defaultUnitCode})',
      ),
    ],
    if (ingredient.pieceGramWeight != null)
      catalogDetailLine(
        AppStrings.ingredientPieceWeight,
        '${CatalogLocalizations.formatNumber(ingredient.pieceGramWeight!)} g',
      ),
    if (ingredient.typicalShelfLifeDays != null)
      catalogDetailLine(
        AppStrings.ingredientShelfLife,
        '${ingredient.typicalShelfLifeDays} ${AppStrings.catalogDays}',
      ),
    if (ingredient.defaultFoodPublicId != null)
      _defaultFoodCard(context, ingredient),
    const SizedBox(height: 16),
    _aliasesSection(context, ingredient.aliases),
    const SizedBox(height: 16),
    _foodMappingsSection(context, ingredient.foodMappings),
    if (ingredient.allergens.isNotEmpty) ...[
      const SizedBox(height: 16),
      _allergensSection(context, ingredient.allergens),
    ],
    if (ingredient.unitConversions.isNotEmpty) ...[
      const SizedBox(height: 16),
      _conversionsSection(context, ingredient.unitConversions),
    ],
  ];

  Widget _defaultFoodCard(
    BuildContext context,
    IngredientCatalogDetail ingredient,
  ) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.ingredientViewFoodNutrition,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (ingredient.defaultFoodDisplayName != null) ...[
            const SizedBox(height: 8),
            Text(ingredient.defaultFoodDisplayName!),
          ],
          const SizedBox(height: 8),
          OutlinedButton.icon(
            key: ValueKey(
              'ingredient-default-food-${ingredient.defaultFoodPublicId}',
            ),
            onPressed: () => context.push(
              '/catalog/foods/${ingredient.defaultFoodPublicId}',
            ),
            icon: const Icon(Icons.restaurant),
            label: const Text(AppStrings.ingredientViewFoodNutrition),
          ),
        ],
      ),
    ),
  );

  Widget _aliasesSection(BuildContext context, List<String> aliases) {
    if (aliases.isEmpty) {
      return const SizedBox.shrink();
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.ingredientAliases,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [for (final alias in aliases) Chip(label: Text(alias))],
            ),
          ],
        ),
      ),
    );
  }

  Widget _foodMappingsSection(
    BuildContext context,
    List<IngredientFoodMapping> mappings,
  ) {
    if (mappings.isEmpty) {
      return const CatalogEmptyPanel(
        message: AppStrings.ingredientNoFoodMappings,
      );
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              AppStrings.ingredientFoodMappings,
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            for (final mapping in mappings)
              Card(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        mapping.foodDisplayName,
                        style: Theme.of(context).textTheme.titleSmall,
                      ),
                      catalogDetailLine(
                        AppStrings.ingredientPreparation,
                        CatalogLocalizations.enumName(mapping.preparationState),
                      ),
                      catalogDetailLine(
                        AppStrings.ingredientYield,
                        CatalogLocalizations.formatNumber(mapping.yieldFactor),
                      ),
                      if (mapping.primary)
                        const Chip(label: Text(AppStrings.ingredientPrimary)),
                      const SizedBox(height: 4),
                      OutlinedButton(
                        key: ValueKey(
                          'ingredient-food-${mapping.foodPublicId}',
                        ),
                        onPressed: () => context.push(
                          '/catalog/foods/${mapping.foodPublicId}',
                        ),
                        child: const Text(
                          AppStrings.ingredientViewFoodNutrition,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _allergensSection(
    BuildContext context,
    List<IngredientAllergen> allergens,
  ) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.ingredientAllergens,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          for (final allergen in allergens)
            catalogDetailLine(
              allergen.allergenDisplayName,
              '${CatalogLocalizations.enumName(allergen.presence)}'
              '${allergen.note == null ? '' : ' · ${allergen.note}'}',
            ),
        ],
      ),
    ),
  );

  Widget _conversionsSection(
    BuildContext context,
    List<IngredientUnitConversion> conversions,
  ) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppStrings.ingredientConversions,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          for (final conversion in conversions)
            catalogDetailLine(
              '${CatalogLocalizations.formatNumber(conversion.fromQuantity)} '
              '${conversion.fromUnitDisplayName}',
              '${CatalogLocalizations.formatNumber(conversion.toQuantity)} '
              '${conversion.toUnitDisplayName} · '
              '${CatalogLocalizations.enumName(conversion.confidence)}',
            ),
        ],
      ),
    ),
  );
}
