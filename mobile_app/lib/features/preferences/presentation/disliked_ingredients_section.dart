import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_localizations.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class DislikedIngredientsSection extends StatelessWidget {
  const DislikedIngredientsSection({
    super.key,
    required this.controller,
  });

  final DislikedIngredientsController controller;

  @override
  Widget build(BuildContext context) {
    final state = controller.state;
    return Card(
      key: const ValueKey('disliked-ingredients-section'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              AppStrings.dislikedIngredients,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.dislikedIngredientsDescription),
            const SizedBox(height: 4),
            Text(
              AppStrings.dislikedIngredientsStored,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 4),
            Text(
              AppStrings.dislikedIngredientStrengthDescription,
              style: Theme.of(context).textTheme.bodySmall,
            ),
            const SizedBox(height: 16),
            if (!state.hasLoadedData &&
                state.status == DislikedIngredientsStatus.loading)
              const Center(child: CircularProgressIndicator())
            else if (!state.hasLoadedData &&
                state.status == DislikedIngredientsStatus.error)
              _LoadError(
                message:
                    state.errorMessage ??
                    AppStrings.dislikedIngredientsLoadFailed,
                onRetry: controller.reload,
              )
            else
              _LoadedDislikedIngredients(
                controller: controller,
                state: state,
              ),
          ],
        ),
      ),
    );
  }
}

final class _LoadedDislikedIngredients extends StatelessWidget {
  const _LoadedDislikedIngredients({
    required this.controller,
    required this.state,
  });

  final DislikedIngredientsController controller;
  final DislikedIngredientsState state;

  @override
  Widget build(BuildContext context) {
    final enabled = !state.isSaving;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (state.draftPreferences.isEmpty)
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text(AppStrings.dislikedIngredientsEmpty),
          ),
        for (final preference in state.draftPreferences)
          _DislikedIngredientEditor(
            key: ValueKey(
              'disliked-editor-${preference.ingredientPublicId}',
            ),
            preference: preference,
            enabled: enabled,
            onStrengthChanged: (strength) => controller.setStrength(
              preference.ingredientPublicId,
              strength,
            ),
            onNoteChanged: (note) => controller.setNote(
              preference.ingredientPublicId,
              note,
            ),
            onRemove: () => controller.removeIngredient(
              preference.ingredientPublicId,
            ),
          ),
        if (state.errorMessage != null) ...[
          const SizedBox(height: 12),
          _MessageBanner(
            message: state.errorMessage!,
            isSuccess: false,
          ),
        ],
        if (state.saveMessage != null) ...[
          const SizedBox(height: 12),
          _MessageBanner(
            message: state.saveMessage!,
            isSuccess: true,
          ),
        ],
        const SizedBox(height: 12),
        OutlinedButton.icon(
          key: const ValueKey('disliked-add-ingredient'),
          onPressed: enabled ? () => _openPicker(context) : null,
          icon: const Icon(Icons.add),
          label: const Text(AppStrings.dislikedIngredientAdd),
        ),
        const SizedBox(height: 8),
        FilledButton.icon(
          key: const ValueKey('save-disliked-ingredients'),
          onPressed: enabled && state.hasChanges ? controller.save : null,
          icon: state.isSaving
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.save),
          label: const Text(AppStrings.dislikedIngredientSave),
        ),
      ],
    );
  }

  Future<void> _openPicker(BuildContext context) async {
    unawaited(controller.loadPickerInitial());
    await showDialog<void>(
      context: context,
      builder: (context) => _DislikedIngredientPickerDialog(
        controller: controller,
      ),
    );
  }
}

final class _DislikedIngredientEditor extends StatefulWidget {
  const _DislikedIngredientEditor({
    super.key,
    required this.preference,
    required this.enabled,
    required this.onStrengthChanged,
    required this.onNoteChanged,
    required this.onRemove,
  });

  final DislikedIngredientPreference preference;
  final bool enabled;
  final ValueChanged<DislikedIngredientStrength> onStrengthChanged;
  final ValueChanged<String> onNoteChanged;
  final VoidCallback onRemove;

  @override
  State<_DislikedIngredientEditor> createState() =>
      _DislikedIngredientEditorState();
}

final class _DislikedIngredientEditorState
    extends State<_DislikedIngredientEditor> {
  late final TextEditingController _noteController;

  @override
  void initState() {
    super.initState();
    _noteController = TextEditingController(text: widget.preference.note ?? '');
  }

  @override
  void didUpdateWidget(covariant _DislikedIngredientEditor oldWidget) {
    super.didUpdateWidget(oldWidget);
    final nextText = widget.preference.note ?? '';
    if (_noteController.text != nextText) {
      _noteController.value = TextEditingValue(
        text: nextText,
        selection: TextSelection.collapsed(offset: nextText.length),
      );
    }
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Card(
    color: Theme.of(context).colorScheme.surfaceContainerHighest,
    margin: const EdgeInsets.only(bottom: 12),
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  widget.preference.ingredientDisplayName,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              IconButton(
                key: ValueKey(
                  'disliked-remove-${widget.preference.ingredientPublicId}',
                ),
                tooltip: AppStrings.dislikedIngredientRemove,
                onPressed: widget.enabled ? widget.onRemove : null,
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
          Text(CatalogLocalizations.categoryName(widget.preference.category)),
          const SizedBox(height: 12),
          InputDecorator(
            decoration: const InputDecoration(
              labelText: AppStrings.dislikedIngredientStrength,
              border: OutlineInputBorder(),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<DislikedIngredientStrength>(
                key: ValueKey(
                  'disliked-strength-${widget.preference.ingredientPublicId}',
                ),
                value: widget.preference.strength,
                isExpanded: true,
                items: [
                  for (final strength in DislikedIngredientStrength.values)
                    DropdownMenuItem(
                      value: strength,
                      child: Text(_strengthName(strength)),
                    ),
                ],
                onChanged: widget.enabled
                    ? (strength) {
                        if (strength != null) {
                          widget.onStrengthChanged(strength);
                        }
                      }
                    : null,
              ),
            ),
          ),
          const SizedBox(height: 12),
          TextFormField(
            key: ValueKey(
              'disliked-note-${widget.preference.ingredientPublicId}',
            ),
            controller: _noteController,
            enabled: widget.enabled,
            maxLength: 255,
            maxLengthEnforcement: MaxLengthEnforcement.none,
            decoration: const InputDecoration(
              labelText: AppStrings.dislikedIngredientNote,
              alignLabelWithHint: true,
              border: OutlineInputBorder(),
            ),
            onChanged: widget.onNoteChanged,
          ),
        ],
      ),
    ),
  );

  String _strengthName(DislikedIngredientStrength strength) =>
      AppStrings.dislikedIngredientStrengthLabels[strength.wireValue] ??
      strength.wireValue;
}

final class _DislikedIngredientPickerDialog extends StatefulWidget {
  const _DislikedIngredientPickerDialog({required this.controller});

  final DislikedIngredientsController controller;

  @override
  State<_DislikedIngredientPickerDialog> createState() =>
      _DislikedIngredientPickerDialogState();
}

final class _DislikedIngredientPickerDialogState
    extends State<_DislikedIngredientPickerDialog> {
  late final TextEditingController _queryController;

  @override
  void initState() {
    super.initState();
    _queryController = TextEditingController(
      text: widget.controller.pickerState.query,
    );
  }

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    return AlertDialog(
      key: const ValueKey('disliked-picker'),
      title: const Text(AppStrings.dislikedIngredientPickerTitle),
      content: SizedBox(
        width: math.min(size.width * .82, 520),
        height: math.min(size.height * .68, 520),
        child: AnimatedBuilder(
          animation: widget.controller,
          builder: (context, _) {
            final state = widget.controller.pickerState;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  key: const ValueKey('disliked-picker-search'),
                  controller: _queryController,
                  textInputAction: TextInputAction.search,
                  decoration: const InputDecoration(
                    labelText: AppStrings.dislikedIngredientPickerSearch,
                    border: OutlineInputBorder(),
                  ),
                  onSubmitted: (_) => _search(),
                ),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.icon(
                    key: const ValueKey('disliked-picker-submit'),
                    onPressed: _search,
                    icon: const Icon(Icons.search),
                    label: const Text(AppStrings.catalogSearchSubmit),
                  ),
                ),
                const SizedBox(height: 8),
                Expanded(child: _results(context, state)),
              ],
            );
          },
        ),
      ),
      actions: [
        TextButton(
          key: const ValueKey('disliked-picker-close'),
          onPressed: () => Navigator.of(context).pop(),
          child: const Text(AppStrings.dislikedIngredientPickerClose),
        ),
      ],
    );
  }

  Widget _results(
    BuildContext context,
    DislikedIngredientPickerState state,
  ) {
    if (state.status == DislikedIngredientPickerStatus.loading &&
        state.items.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.status == DislikedIngredientPickerStatus.error &&
        state.items.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              state.errorMessage ??
                  AppStrings.dislikedIngredientPickerLoadFailed,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            FilledButton(
              key: const ValueKey('disliked-picker-retry'),
              onPressed: () => unawaited(widget.controller.retryPicker()),
              child: const Text(AppStrings.dislikedIngredientPickerRetry),
            ),
          ],
        ),
      );
    }
    if (state.items.isEmpty) {
      return const Center(
        child: Text(AppStrings.dislikedIngredientPickerNoResults),
      );
    }

    return ListView(
      key: const ValueKey('disliked-picker-list'),
      children: [
        for (final item in state.items) _pickerItem(item),
        if (state.loadingMore)
          const Padding(
            padding: EdgeInsets.all(12),
            child: Center(child: CircularProgressIndicator()),
          ),
        if (state.loadMoreErrorMessage != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Column(
              children: [
                Text(
                  state.loadMoreErrorMessage!,
                  textAlign: TextAlign.center,
                ),
                TextButton(
                  key: const ValueKey('disliked-picker-load-more-retry'),
                  onPressed: () =>
                      unawaited(widget.controller.retryPicker()),
                  child: const Text(AppStrings.dislikedIngredientPickerRetry),
                ),
              ],
            ),
          ),
        if (state.hasMore && !state.loadingMore)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: OutlinedButton(
              key: const ValueKey('disliked-picker-load-more'),
              onPressed: () =>
                  unawaited(widget.controller.loadPickerMore()),
              child: const Text(AppStrings.loadMore),
            ),
          ),
      ],
    );
  }

  Widget _pickerItem(IngredientCatalogItem item) {
    final selected = widget.controller.isIngredientSelected(item.publicId);
    return ListTile(
      key: ValueKey('disliked-picker-item-${item.publicId}'),
      enabled: !selected,
      title: Text(item.displayName),
      subtitle: Text(CatalogLocalizations.categoryName(item.category)),
      trailing: selected
          ? const Text(AppStrings.dislikedIngredientPickerSelected)
          : const Icon(Icons.add_circle_outline),
      onTap: selected
          ? null
          : () => widget.controller.addIngredient(item),
    );
  }

  void _search() {
    unawaited(widget.controller.searchPicker(_queryController.text));
  }
}

final class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message, textAlign: TextAlign.center),
      const SizedBox(height: 8),
      FilledButton(
        key: const ValueKey('disliked-ingredients-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}

final class _MessageBanner extends StatelessWidget {
  const _MessageBanner({required this.message, required this.isSuccess});

  final String message;
  final bool isSuccess;

  @override
  Widget build(BuildContext context) => Card(
    color: isSuccess
        ? Theme.of(context).colorScheme.secondaryContainer
        : Theme.of(context).colorScheme.errorContainer,
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Text(message),
    ),
  );
}
