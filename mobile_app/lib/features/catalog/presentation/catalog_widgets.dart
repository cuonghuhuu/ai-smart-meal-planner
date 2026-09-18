import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class CatalogSearchBar extends StatelessWidget {
  const CatalogSearchBar({
    super.key,
    required this.controller,
    required this.fieldKey,
    required this.submitKey,
    required this.onSubmitted,
  });

  final TextEditingController controller;
  final Key fieldKey;
  final Key submitKey;
  final ValueChanged<String> onSubmitted;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final field = TextField(
        key: fieldKey,
        controller: controller,
        textInputAction: TextInputAction.search,
        onSubmitted: onSubmitted,
        decoration: InputDecoration(
          labelText: AppStrings.catalogSearchHint,
          prefixIcon: const Icon(Icons.search),
          border: const OutlineInputBorder(),
        ),
      );
      final button = FilledButton.icon(
        key: submitKey,
        onPressed: () => onSubmitted(controller.text),
        icon: const Icon(Icons.search),
        label: const Text(AppStrings.catalogSearchSubmit),
      );
      if (constraints.maxWidth < 520) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [field, const SizedBox(height: 8), button],
        );
      }
      return Row(
        children: [
          Expanded(child: field),
          const SizedBox(width: 12),
          button,
        ],
      );
    },
  );
}

class CatalogCategoryFilter extends StatelessWidget {
  const CatalogCategoryFilter({
    super.key,
    required this.categories,
    required this.selectedCategoryCode,
    required this.filterKey,
    required this.onChanged,
  });

  final List<CatalogCategory> categories;
  final String? selectedCategoryCode;
  final Key filterKey;
  final ValueChanged<String?> onChanged;

  @override
  Widget build(BuildContext context) => InputDecorator(
    key: filterKey,
    decoration: const InputDecoration(
      labelText: AppStrings.catalogAllCategories,
      border: OutlineInputBorder(),
    ),
    child: DropdownButtonHideUnderline(
      child: DropdownButton<String>(
        isExpanded: true,
        value: selectedCategoryCode ?? '',
        items: [
          const DropdownMenuItem<String>(
            value: '',
            child: Text(AppStrings.catalogAllCategories),
          ),
          for (final category in categories)
            DropdownMenuItem<String>(
              value: category.code,
              child: Text(CatalogLocalizations.categoryName(category)),
            ),
        ],
        onChanged: (value) => onChanged(value == null || value.isEmpty ? null : value),
      ),
    ),
  );
}

class CatalogErrorPanel extends StatelessWidget {
  const CatalogErrorPanel({
    super.key,
    required this.message,
    required this.retryKey,
    required this.onRetry,
  });

  final String message;
  final Key retryKey;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          const Icon(Icons.cloud_off, size: 32),
          const SizedBox(height: 8),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          OutlinedButton(
            key: retryKey,
            onPressed: onRetry,
            child: const Text(AppStrings.retry),
          ),
        ],
      ),
    ),
  );
}

class CatalogEmptyPanel extends StatelessWidget {
  const CatalogEmptyPanel({super.key, required this.message});

  final String message;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 48),
    child: Center(
      child: Text(message, textAlign: TextAlign.center),
    ),
  );
}

class FoodCatalogListItem extends StatelessWidget {
  const FoodCatalogListItem({super.key, required this.item});

  final FoodCatalogItem item;

  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => context.push('/catalog/foods/${item.publicId}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.restaurant, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.displayName,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(CatalogLocalizations.categoryName(item.category)),
                  const SizedBox(height: 4),
                  Text(
                    item.code,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const Icon(Icons.chevron_right),
          ],
        ),
      ),
    ),
  );
}

class IngredientCatalogListItem extends StatelessWidget {
  const IngredientCatalogListItem({super.key, required this.item});

  final IngredientCatalogItem item;

  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: () => context.push('/catalog/ingredients/${item.publicId}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.kitchen, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    item.displayName,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(CatalogLocalizations.categoryName(item.category)),
                  if (item.staple) ...[
                    const SizedBox(height: 4),
                    Text(
                      AppStrings.ingredientStaple,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ],
              ),
            ),
            const Icon(Icons.chevron_right),
          ],
        ),
      ),
    ),
  );
}

class CatalogLoadMoreFooter extends StatelessWidget {
  const CatalogLoadMoreFooter({
    super.key,
    required this.loading,
    required this.errorMessage,
    required this.loadMoreKey,
    required this.retryKey,
    required this.onLoadMore,
  });

  final bool loading;
  final String? errorMessage;
  final Key loadMoreKey;
  final Key retryKey;
  final VoidCallback onLoadMore;

  @override
  Widget build(BuildContext context) {
    if (loading) {
      return const Padding(
        padding: EdgeInsets.all(20),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (errorMessage != null) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          children: [
            Text(errorMessage!, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            OutlinedButton(
              key: retryKey,
              onPressed: onLoadMore,
              child: const Text(AppStrings.retry),
            ),
          ],
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 12),
      child: OutlinedButton(
        key: loadMoreKey,
        onPressed: onLoadMore,
        child: const Text(AppStrings.loadMore),
      ),
    );
  }
}

Widget catalogDetailLine(String label, String value) => Padding(
  padding: const EdgeInsets.symmetric(vertical: 4),
  child: Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      SizedBox(
        width: 150,
        child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
      ),
      Expanded(child: Text(value)),
    ],
  ),
);
