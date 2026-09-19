import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/admin/recipes/application/admin_recipe_controller.dart';
import 'package:smart_meal_planner/features/admin/recipes/data/admin_recipe_models.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class AdminRecipesPage extends StatefulWidget {
  const AdminRecipesPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final AdminRecipeController controller;

  @override
  State<AdminRecipesPage> createState() => _AdminRecipesPageState();
}

class _AdminRecipesPageState extends State<AdminRecipesPage> {
  late final TextEditingController _searchController;

  AdminRecipeController get _controller => widget.controller;

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
    selectedIndex: 9,
    content: _content(context),
  );

  Widget _content(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('admin-recipes-list'),
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Text(
                    AppStrings.adminRecipes,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                ),
                FilledButton.icon(
                  key: const ValueKey('admin-recipe-create'),
                  onPressed: () => context.go('/admin/recipes/new'),
                  icon: const Icon(Icons.add),
                  label: const Text(AppStrings.adminCreateRecipe),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.adminRecipesSubtitle),
            const SizedBox(height: 20),
            TextField(
              key: const ValueKey('admin-recipes-search'),
              controller: _searchController,
              textInputAction: TextInputAction.search,
              onSubmitted: _controller.search,
              decoration: InputDecoration(
                labelText: AppStrings.adminSearch,
                suffixIcon: IconButton(
                  key: const ValueKey('admin-recipes-search-submit'),
                  onPressed: () => _controller.search(_searchController.text),
                  icon: const Icon(Icons.search),
                ),
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              key: const ValueKey('admin-recipes-status-filter'),
              initialValue: state.statusFilter ?? '',
              decoration: const InputDecoration(
                labelText: AppStrings.adminStatusFilter,
              ),
              items: const [
                DropdownMenuItem(value: '', child: Text(AppStrings.adminAllStatuses)),
                DropdownMenuItem(value: 'DRAFT', child: Text(AppStrings.adminDraft)),
                DropdownMenuItem(value: 'PUBLISHED', child: Text(AppStrings.adminPublished)),
                DropdownMenuItem(value: 'ARCHIVED', child: Text(AppStrings.adminArchived)),
              ],
              onChanged: (value) => _controller.setStatus(value),
            ),
            const SizedBox(height: 20),
            ..._body(context, state),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, AdminRecipeListState state) {
    if (state.isInitialLoading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == AdminRecipeListStatus.error) {
      return [
        _AdminRecipeErrorPanel(
          message: state.errorMessage ?? AppStrings.adminRecipeLoadFailed,
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: Center(child: Text(AppStrings.adminRecipesEmpty)),
        ),
      ];
    }
    return [
      if (_controller.actionErrorMessage != null)
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(_controller.actionErrorMessage!),
        ),
      for (final item in state.items)
        _AdminRecipeCard(
          key: ValueKey('admin-recipe-item-${item.publicId}'),
          item: item,
          busy: _controller.mutatingPublicId == item.publicId,
          onEdit: () => context.go('/admin/recipes/${item.publicId}/edit'),
          onView: () => context.go('/admin/recipes/${item.publicId}/edit'),
          onPublish: () => _confirmLifecycle(context, item, publish: true),
          onArchive: () => _confirmLifecycle(context, item, publish: false),
        ),
      if (state.loadMoreErrorMessage != null)
        Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(state.loadMoreErrorMessage!),
        ),
      if (state.hasMore)
        OutlinedButton(
          key: const ValueKey('admin-recipes-load-more'),
          onPressed: state.isLoadingMore ? null : _controller.loadMore,
          child: state.isLoadingMore
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text(AppStrings.loadMore),
        ),
    ];
  }

  Future<void> _confirmLifecycle(
    BuildContext context,
    AdminRecipeItem item, {
    required bool publish,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(publish ? AppStrings.adminPublish : AppStrings.adminArchive),
        content: Text(
          publish
              ? AppStrings.adminPublishConfirmation
              : AppStrings.adminArchiveConfirmation,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text(AppStrings.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(publish ? AppStrings.adminPublish : AppStrings.adminArchive),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    if (publish) {
      await _controller.publish(item.publicId);
    } else {
      await _controller.archive(item.publicId);
    }
  }
}

final class _AdminRecipeCard extends StatelessWidget {
  const _AdminRecipeCard({
    super.key,
    required this.item,
    required this.busy,
    required this.onEdit,
    required this.onView,
    required this.onPublish,
    required this.onArchive,
  });

  final AdminRecipeItem item;
  final bool busy;
  final VoidCallback onEdit;
  final VoidCallback onView;
  final VoidCallback onPublish;
  final VoidCallback onArchive;

  @override
  Widget build(BuildContext context) => Card(
    margin: const EdgeInsets.only(bottom: 12),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(item.title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 6),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              Chip(label: Text(_statusLabel(item.status))),
              Text('${item.servings} ${AppStrings.recipeServings}'),
              if (item.totalMinutes != null)
                Text('${item.totalMinutes} ${AppStrings.recipeMinutes}'),
            ],
          ),
          if (item.summary != null && item.summary!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(item.summary!),
          ],
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
          const SizedBox(height: 8),
          if (item.status == 'DRAFT')
            Wrap(
              spacing: 8,
              children: [
                OutlinedButton(
                  key: ValueKey('admin-recipe-edit-${item.publicId}'),
                  onPressed: busy ? null : onEdit,
                  child: const Text(AppStrings.adminEdit),
                ),
                FilledButton(
                  key: ValueKey('admin-recipe-publish-${item.publicId}'),
                  onPressed: busy ? null : onPublish,
                  child: busy
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text(AppStrings.adminPublish),
                ),
              ],
            )
          else
            Wrap(
              spacing: 8,
              children: [
                OutlinedButton(
                  key: ValueKey('admin-recipe-view-${item.publicId}'),
                  onPressed: onView,
                  child: const Text(AppStrings.adminView),
                ),
                if (item.status == 'PUBLISHED')
                  OutlinedButton(
                    key: ValueKey('admin-recipe-archive-${item.publicId}'),
                    onPressed: busy ? null : onArchive,
                    child: busy
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text(AppStrings.adminArchive),
                  ),
              ],
            ),
        ],
      ),
    ),
  );

  static String _statusLabel(String status) => switch (status) {
    'DRAFT' => AppStrings.adminDraft,
    'PUBLISHED' => AppStrings.adminPublished,
    'ARCHIVED' => AppStrings.adminArchived,
    _ => status,
  };
}

final class _AdminRecipeErrorPanel extends StatelessWidget {
  const _AdminRecipeErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('admin-recipes-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
