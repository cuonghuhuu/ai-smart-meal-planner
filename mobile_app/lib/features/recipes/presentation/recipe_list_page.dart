import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/recipes/application/recipe_controller.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/features/recipes/presentation/recipe_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class RecipeListPage extends StatefulWidget {
  const RecipeListPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final RecipeController controller;

  @override
  State<RecipeListPage> createState() => _RecipeListPageState();
}

class _RecipeListPageState extends State<RecipeListPage> {
  late final TextEditingController _searchController;

  RecipeController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: _controller.state.searchQuery);
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadInitial();
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 2,
    content: _content(context),
  );

  Widget _content(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('recipe-list'),
          children: [
            Text(
              AppStrings.recipes,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.recipesSubtitle),
            const SizedBox(height: 20),
            TextField(
              key: const ValueKey('recipe-search-field'),
              controller: _searchController,
              textInputAction: TextInputAction.search,
              onSubmitted: _controller.search,
              decoration: InputDecoration(
                labelText: AppStrings.recipeSearchHint,
                suffixIcon: IconButton(
                  key: const ValueKey('recipe-search-submit'),
                  onPressed: () => _controller.search(_searchController.text),
                  icon: const Icon(Icons.search),
                ),
              ),
            ),
            const SizedBox(height: 20),
            ..._body(context, state),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, RecipeListState state) {
    if (state.isInitialLoading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == RecipeListStatus.error) {
      return [
        _RecipeErrorPanel(
          message: state.errorMessage ?? AppStrings.recipeLoadFailed,
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: Center(child: Text(AppStrings.recipeEmpty)),
        ),
      ];
    }
    return [
      for (final item in state.items)
        _RecipeListCard(
          key: ValueKey('recipe-item-${item.publicId}'),
          item: item,
        ),
      if (state.hasMore) ...[
        if (state.loadMoreErrorMessage != null)
          Text(state.loadMoreErrorMessage!),
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: OutlinedButton(
            key: const ValueKey('recipe-load-more'),
            onPressed: state.isLoadingMore ? null : _controller.loadMore,
            child: state.isLoadingMore
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text(AppStrings.loadMore),
          ),
        ),
      ],
    ];
  }
}

final class _RecipeListCard extends StatelessWidget {
  const _RecipeListCard({super.key, required this.item});

  final RecipeListItem item;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 12),
    child: InkWell(
      onTap: () => context.go('/recipes/${item.publicId}'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(item.title, style: Theme.of(context).textTheme.titleMedium),
            if (item.summary != null && item.summary!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(item.summary!),
            ],
            const SizedBox(height: 8),
            Text(
              [
                if (item.totalMinutes != null)
                  '${item.totalMinutes} ${AppStrings.recipeMinutes}',
                '${item.servings} ${AppStrings.recipeServings}',
                RecipeLocalizations.difficultyName(item.difficulty),
              ].join(' · '),
            ),
            if (item.tags.isNotEmpty || item.mealSlots.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                [
                  ...item.tags.map((tag) => tag.displayName),
                  ...item.mealSlots.map((slot) => slot.displayName),
                ].join(' · '),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ],
        ),
      ),
    ),
  );
}

final class _RecipeErrorPanel extends StatelessWidget {
  const _RecipeErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('recipe-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
