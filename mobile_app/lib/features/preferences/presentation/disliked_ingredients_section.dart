import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:smart_meal_planner/core/ui/wellness_components.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_localizations.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/data/disliked_ingredient_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

final class DislikedIngredientsSection extends StatefulWidget {
  const DislikedIngredientsSection({super.key, required this.controller});

  final DislikedIngredientsController controller;

  @override
  State<DislikedIngredientsSection> createState() =>
      _DislikedIngredientsSectionState();
}

final class _DislikedIngredientsSectionState
    extends State<DislikedIngredientsSection> {
  final _queryController = TextEditingController();
  Timer? _debounce;
  String? _editingId;

  @override
  void dispose() {
    _debounce?.cancel();
    _queryController.dispose();
    super.dispose();
  }

  void _search(String value) {
    _debounce?.cancel();
    setState(() {});
    final query = value.trim();
    if (query.isEmpty) return;
    _debounce = Timer(const Duration(milliseconds: 320), () {
      if (mounted) unawaited(widget.controller.searchPicker(query));
    });
  }

  void _submitSearch() {
    _debounce?.cancel();
    final query = _queryController.text.trim();
    if (query.isEmpty) return;
    final picker = widget.controller.pickerState;
    if (picker.status == DislikedIngredientPickerStatus.loaded &&
        picker.query == query) {
      final matches = picker.items.where(
        (item) => !widget.controller.isIngredientSelected(item.publicId),
      );
      if (matches.isNotEmpty) {
        _select(matches.first);
        return;
      }
    }
    unawaited(widget.controller.searchPicker(query));
  }

  void _select(IngredientCatalogItem item) {
    widget.controller.addIngredient(item);
    _queryController.clear();
    setState(() => _editingId = null);
  }

  @override
  Widget build(BuildContext context) {
    final state = widget.controller.state;
    return SectionSurface(
      key: const ValueKey('disliked-ingredients-section'),
      title: AppStrings.dislikedSectionTitle,
      subtitle: AppStrings.dislikedIngredientsDescription,
      icon: Icons.heart_broken_outlined,
      child:
          !state.hasLoadedData &&
              state.status == DislikedIngredientsStatus.loading
          ? const Center(child: CircularProgressIndicator())
          : !state.hasLoadedData &&
                state.status == DislikedIngredientsStatus.error
          ? WellnessEmptyState(
              icon: Icons.cloud_off_outlined,
              message:
                  state.errorMessage ??
                  AppStrings.dislikedIngredientsLoadFailed,
              action: OutlinedButton(
                onPressed: widget.controller.reload,
                child: const Text(AppStrings.retry),
              ),
            )
          : _loaded(context, state),
    );
  }

  Widget _loaded(BuildContext context, DislikedIngredientsState state) {
    final enabled = !state.isSaving;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          key: const ValueKey('disliked-search'),
          controller: _queryController,
          enabled: enabled,
          textInputAction: TextInputAction.search,
          onChanged: _search,
          onSubmitted: (_) => _submitSearch(),
          decoration: const InputDecoration(
            hintText: AppStrings.dislikedSearchHint,
            prefixIcon: Icon(Icons.search_rounded),
          ),
        ),
        if (_queryController.text.trim().isNotEmpty) ...[
          const SizedBox(height: 8),
          _suggestions(context),
        ],
        const SizedBox(height: 16),
        if (state.draftPreferences.isEmpty)
          const WellnessEmptyState(
            icon: Icons.restaurant_menu_outlined,
            message: AppStrings.dislikedIngredientsEmpty,
          )
        else ...[
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final preference in state.draftPreferences)
                InputChip(
                  key: ValueKey(
                    'disliked-chip-${preference.ingredientPublicId}',
                  ),
                  label: Text(preference.ingredientDisplayName),
                  tooltip: _strengthName(preference.strength),
                  selected: _editingId == preference.ingredientPublicId,
                  onPressed: enabled
                      ? () => setState(() {
                          _editingId =
                              _editingId == preference.ingredientPublicId
                              ? null
                              : preference.ingredientPublicId;
                        })
                      : null,
                  onDeleted: enabled
                      ? () {
                          widget.controller.removeIngredient(
                            preference.ingredientPublicId,
                          );
                          if (_editingId == preference.ingredientPublicId) {
                            setState(() => _editingId = null);
                          }
                        }
                      : null,
                  deleteIcon: const Icon(Icons.close_rounded, size: 18),
                ),
            ],
          ),
          if (_editingId != null)
            for (final preference in state.draftPreferences)
              if (preference.ingredientPublicId == _editingId) ...[
                const SizedBox(height: 12),
                _DislikedIngredientEditor(
                  key: ValueKey(
                    'disliked-editor-${preference.ingredientPublicId}',
                  ),
                  preference: preference,
                  enabled: enabled,
                  onStrengthChanged: (strength) => widget.controller
                      .setStrength(preference.ingredientPublicId, strength),
                  onNoteChanged: (note) => widget.controller.setNote(
                    preference.ingredientPublicId,
                    note,
                  ),
                  onRemove: () {
                    widget.controller.removeIngredient(
                      preference.ingredientPublicId,
                    );
                    setState(() => _editingId = null);
                  },
                ),
              ],
        ],
        if (state.errorMessage != null) ...[
          const SizedBox(height: 12),
          WellnessNotice(message: state.errorMessage!, error: true),
        ],
        if (state.saveMessage != null) ...[
          const SizedBox(height: 12),
          WellnessNotice(message: state.saveMessage!),
        ],
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            key: const ValueKey('save-disliked-ingredients'),
            onPressed: enabled && state.hasChanges
                ? widget.controller.save
                : null,
            icon: state.isSaving
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check_rounded),
            label: const Text(AppStrings.dislikedIngredientSave),
          ),
        ),
      ],
    );
  }

  Widget _suggestions(BuildContext context) {
    final state = widget.controller.pickerState;
    final query = _queryController.text.trim();
    if (state.query != query ||
        state.status == DislikedIngredientPickerStatus.loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 12),
        child: LinearProgressIndicator(),
      );
    }
    if (state.status == DislikedIngredientPickerStatus.error) {
      return WellnessNotice(
        message:
            state.errorMessage ?? AppStrings.dislikedIngredientPickerLoadFailed,
        error: true,
        action: IconButton(
          tooltip: AppStrings.retry,
          onPressed: () => unawaited(widget.controller.retryPicker()),
          icon: const Icon(Icons.refresh_rounded),
        ),
      );
    }
    final items = state.items
        .where((item) => !widget.controller.isIngredientSelected(item.publicId))
        .toList();
    if (items.isEmpty) {
      return const WellnessEmptyState(
        icon: Icons.search_off_rounded,
        message: AppStrings.dislikedNoMatch,
      );
    }
    return Container(
      key: const ValueKey('disliked-suggestions'),
      constraints: const BoxConstraints(maxHeight: 230),
      child: Material(
        color: Theme.of(context).colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        clipBehavior: Clip.antiAlias,
        child: ListView(
          shrinkWrap: true,
          children: [
            for (final item in items)
              ListTile(
                key: ValueKey('disliked-picker-item-${item.publicId}'),
                leading: const Icon(Icons.add_circle_outline_rounded),
                title: Text(item.displayName),
                subtitle: Text(
                  CatalogLocalizations.categoryName(item.category),
                ),
                onTap: () => _select(item),
              ),
            if (state.hasMore)
              TextButton(
                key: const ValueKey('disliked-picker-load-more'),
                onPressed: state.loadingMore
                    ? null
                    : () => unawaited(widget.controller.loadPickerMore()),
                child: Text(
                  state.loadingMore ? AppStrings.loading : AppStrings.loadMore,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

String _strengthName(DislikedIngredientStrength strength) =>
    AppStrings.dislikedIngredientStrengthLabels[strength.wireValue] ??
    strength.wireValue;

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
    final next = widget.preference.note ?? '';
    if (_noteController.text != next) {
      _noteController.value = TextEditingValue(
        text: next,
        selection: TextSelection.collapsed(offset: next.length),
      );
    }
  }

  @override
  void dispose() {
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(16),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerLow,
      borderRadius: BorderRadius.circular(16),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          CatalogLocalizations.categoryName(widget.preference.category),
          style: Theme.of(context).textTheme.bodySmall,
        ),
        const SizedBox(height: 10),
        SegmentedButton<DislikedIngredientStrength>(
          key: ValueKey(
            'disliked-strength-${widget.preference.ingredientPublicId}',
          ),
          segments: [
            for (final strength in DislikedIngredientStrength.values)
              ButtonSegment(
                value: strength,
                label: Text(_strengthName(strength)),
              ),
          ],
          selected: {widget.preference.strength},
          onSelectionChanged: widget.enabled
              ? (selection) => widget.onStrengthChanged(selection.first)
              : null,
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
          ),
          onChanged: widget.onNoteChanged,
        ),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            key: ValueKey(
              'disliked-remove-${widget.preference.ingredientPublicId}',
            ),
            onPressed: widget.enabled ? widget.onRemove : null,
            icon: const Icon(Icons.delete_outline_rounded),
            label: const Text(AppStrings.dislikedIngredientRemove),
          ),
        ),
      ],
    ),
  );
}
