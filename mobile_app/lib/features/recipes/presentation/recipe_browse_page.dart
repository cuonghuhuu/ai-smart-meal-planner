import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_state.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_card.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

/// Navigation-neutral catalog page. The caller owns the controller and route.
class RecipeBrowsePage extends StatefulWidget {
  const RecipeBrowsePage({
    super.key,
    required this.controller,
    this.onRecipeSelected,
  });

  final RecipeController controller;
  final ValueChanged<RecipeItem>? onRecipeSelected;

  @override
  State<RecipeBrowsePage> createState() => _RecipeBrowsePageState();
}

class _RecipeBrowsePageState extends State<RecipeBrowsePage> {
  late final TextEditingController _searchController;
  late String _appliedQuery;

  RecipeController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _appliedQuery = _controller.catalogState.filters.query;
    _searchController = TextEditingController(text: _appliedQuery);
    _controller.addListener(_onControllerChanged);
    _load();
  }

  @override
  void didUpdateWidget(covariant RecipeBrowsePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == widget.controller) return;
    oldWidget.controller.removeListener(_onControllerChanged);
    _controller.addListener(_onControllerChanged);
    _appliedQuery = _controller.catalogState.filters.query;
    _searchController.text = _appliedQuery;
    _load();
  }

  void _load() {
    _controller.loadInitial();
    if (_controller.tagsState.status == RecipeReferenceStatus.initial) {
      _controller.loadRecipeTags();
    }
    if (_controller.mealSlotsState.status == RecipeReferenceStatus.initial) {
      _controller.loadMealSlotTypes();
    }
  }

  void _onControllerChanged() {
    if (!mounted) return;
    final query = _controller.catalogState.filters.query;
    if (query != _appliedQuery) {
      _appliedQuery = query;
      _searchController.text = query;
    }
    setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: ResponsiveContent(
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 700;
            final state = _controller.catalogState;
            return CustomScrollView(
              key: const ValueKey('recipe-browse-scroll'),
              slivers: [
                SliverToBoxAdapter(child: _header(context, wide)),
                ..._catalogSlivers(state, wide),
              ],
            );
          },
        ),
      ),
    ),
  );

  Widget _header(BuildContext context, bool wide) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        AppStrings.recipes,
        style: Theme.of(context).textTheme.headlineMedium,
      ),
      const SizedBox(height: 8),
      const Text(AppStrings.recipeBrowseSubtitle),
      const SizedBox(height: 20),
      _search(wide),
      const SizedBox(height: 12),
      _filters(wide),
      Align(
        alignment: Alignment.centerRight,
        child: TextButton.icon(
          key: const ValueKey('recipe-clear-filters'),
          onPressed: () => _controller.clearFilters(),
          icon: const Icon(Icons.filter_alt_off),
          label: const Text(AppStrings.recipeClearFilters),
        ),
      ),
      _referenceStatus(
        _controller.mealSlotsState,
        AppStrings.recipeMealSlotsLoading,
        const ValueKey('recipe-slots-retry'),
        _controller.retryMealSlotTypes,
      ),
      _referenceStatus(
        _controller.tagsState,
        AppStrings.recipeTagsLoading,
        const ValueKey('recipe-tags-retry'),
        _controller.retryRecipeTags,
      ),
      const SizedBox(height: 12),
    ],
  );

  Widget _search(bool wide) {
    final field = TextField(
      key: const ValueKey('recipe-search-field'),
      controller: _searchController,
      textInputAction: TextInputAction.search,
      onSubmitted: _controller.search,
      decoration: const InputDecoration(
        labelText: AppStrings.recipeSearchHint,
        prefixIcon: Icon(Icons.search),
        border: OutlineInputBorder(),
      ),
    );
    final button = FilledButton.icon(
      key: const ValueKey('recipe-search-submit'),
      onPressed: () => _controller.search(_searchController.text),
      icon: const Icon(Icons.search),
      label: const Text(AppStrings.catalogSearchSubmit),
    );
    if (!wide) {
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
  }

  Widget _filters(bool wide) {
    final filters = _controller.catalogState.filters;
    final slots = _controller.mealSlotsState.items;
    final tags = _controller.tagsState.items;
    final slotControl = _dropdown<String>(
      key: const ValueKey('recipe-meal-slot-filter'),
      label: AppStrings.recipeMealSlot,
      value: filters.mealSlotCode ?? '',
      entries: [
        const DropdownMenuItem(
          value: '',
          child: Text(AppStrings.recipeAllMealSlots),
        ),
        for (final slot in slots)
          DropdownMenuItem(value: slot.code, child: Text(slot.displayName)),
        if (filters.mealSlotCode != null &&
            !slots.any((slot) => slot.code == filters.mealSlotCode))
          DropdownMenuItem(
            value: filters.mealSlotCode!,
            child: Text(filters.mealSlotCode!),
          ),
      ],
      onChanged: (value) => _controller.setMealSlotCode(value),
    );
    final tagControl = _dropdown<String>(
      key: const ValueKey('recipe-tag-filter'),
      label: AppStrings.recipeTag,
      value: filters.tagCode ?? '',
      entries: [
        const DropdownMenuItem(
          value: '',
          child: Text(AppStrings.recipeAllTags),
        ),
        for (final tag in tags)
          DropdownMenuItem(value: tag.code, child: Text(tag.displayName)),
        if (filters.tagCode != null &&
            !tags.any((tag) => tag.code == filters.tagCode))
          DropdownMenuItem(
            value: filters.tagCode!,
            child: Text(filters.tagCode!),
          ),
      ],
      onChanged: (value) => _controller.setTagCode(value),
    );
    const presets = [15, 30, 45, 60, 90, 120];
    final maxControl = _dropdown<int>(
      key: const ValueKey('recipe-max-minutes-filter'),
      label: AppStrings.recipeMaxTime,
      value: filters.maxMinutes ?? 0,
      entries: [
        const DropdownMenuItem(value: 0, child: Text(AppStrings.recipeAnyTime)),
        for (final minutes in presets)
          DropdownMenuItem(
            value: minutes,
            child: Text('$minutes ${AppStrings.recipeMinutes}'),
          ),
        if (filters.maxMinutes != null && !presets.contains(filters.maxMinutes))
          DropdownMenuItem(
            value: filters.maxMinutes!,
            child: Text('${filters.maxMinutes} ${AppStrings.recipeMinutes}'),
          ),
      ],
      onChanged: (value) =>
          _controller.setMaxMinutes(value == 0 ? null : value),
    );
    if (!wide) {
      return Column(
        children: [
          slotControl,
          const SizedBox(height: 8),
          tagControl,
          const SizedBox(height: 8),
          maxControl,
        ],
      );
    }
    return Row(
      children: [
        Expanded(child: slotControl),
        const SizedBox(width: 8),
        Expanded(child: tagControl),
        const SizedBox(width: 8),
        Expanded(child: maxControl),
      ],
    );
  }

  Widget _dropdown<T>({
    required Key key,
    required String label,
    required T value,
    required List<DropdownMenuItem<T>> entries,
    required ValueChanged<T?> onChanged,
  }) => InputDecorator(
    decoration: InputDecoration(
      labelText: label,
      border: const OutlineInputBorder(),
    ),
    child: DropdownButtonHideUnderline(
      child: DropdownButton<T>(
        key: key,
        isExpanded: true,
        value: value,
        items: entries,
        onChanged: onChanged,
      ),
    ),
  );

  Widget _referenceStatus<T>(
    RecipeReferenceState<T> state,
    String loadingText,
    Key retryKey,
    VoidCallback retry,
  ) {
    if (state.status == RecipeReferenceStatus.loading && state.items.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Text(loadingText),
      );
    }
    if (state.status != RecipeReferenceStatus.error) {
      return const SizedBox.shrink();
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Row(
          children: [
            Expanded(
              child: Text(state.errorMessage ?? AppStrings.catalogLoadFailed),
            ),
            TextButton(
              key: retryKey,
              onPressed: retry,
              child: const Text(AppStrings.retry),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _catalogSlivers(RecipeCatalogState state, bool wide) {
    if (state.status == RecipeListStatus.initial ||
        state.status == RecipeListStatus.loading) {
      return [
        const SliverFillRemaining(
          hasScrollBody: false,
          child: Center(
            child: CircularProgressIndicator(
              key: ValueKey('recipe-initial-loading'),
            ),
          ),
        ),
      ];
    }
    if (state.status == RecipeListStatus.error) {
      return [
        SliverToBoxAdapter(
          child: _messagePanel(
            state.errorMessage ?? AppStrings.catalogLoadFailed,
            const ValueKey('recipe-initial-retry'),
            _controller.retryCatalog,
          ),
        ),
      ];
    }
    if (state.items.isEmpty) {
      return [
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 40),
            child: Center(
              child: Text(
                AppStrings.recipeNoResults,
                textAlign: TextAlign.center,
              ),
            ),
          ),
        ),
      ];
    }
    return [
      SliverGrid.builder(
        itemCount: state.items.length,
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: wide ? 2 : 1,
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          mainAxisExtent: 320,
        ),
        itemBuilder: (context, index) {
          final recipe = state.items[index];
          return RecipeCard(
            key: ValueKey('recipe-item-${recipe.publicId}'),
            recipe: recipe,
            onSelected: widget.onRecipeSelected,
          );
        },
      ),
      if (state.hasMore) SliverToBoxAdapter(child: _pagination(state)),
    ];
  }

  Widget _pagination(RecipeCatalogState state) {
    if (state.status == RecipeListStatus.loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(20),
        child: Center(
          child: CircularProgressIndicator(
            key: ValueKey('recipe-load-more-loading'),
          ),
        ),
      );
    }
    if (state.loadMoreErrorMessage != null) {
      return _messagePanel(
        state.loadMoreErrorMessage!,
        const ValueKey('recipe-load-more-retry'),
        _controller.loadMore,
      );
    }
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: OutlinedButton(
          key: const ValueKey('recipe-load-more'),
          onPressed: _controller.loadMore,
          child: const Text(AppStrings.loadMore),
        ),
      ),
    );
  }

  Widget _messagePanel(String message, Key key, VoidCallback retry) => Card(
    child: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 8),
          OutlinedButton(
            key: key,
            onPressed: retry,
            child: const Text(AppStrings.retry),
          ),
        ],
      ),
    ),
  );
}
