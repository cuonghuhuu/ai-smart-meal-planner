import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/core/ui/wellness_components.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/disliked_ingredients_controller.dart';
import 'package:smart_meal_planner/features/preferences/application/preferences_validation.dart';
import 'package:smart_meal_planner/features/preferences/data/preference_models.dart';
import 'package:smart_meal_planner/features/preferences/presentation/disliked_ingredients_section.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';
import 'package:smart_meal_planner/l10n/reference_localizations.dart';

class PreferencesPage extends StatefulWidget {
  const PreferencesPage({
    super.key,
    required this.sessionController,
    required this.preferencesController,
    this.dislikedIngredientsController,
  });

  final SessionController sessionController;
  final PreferencesController preferencesController;
  final DislikedIngredientsController? dislikedIngredientsController;

  @override
  State<PreferencesPage> createState() => _PreferencesPageState();
}

class _PreferencesPageState extends State<PreferencesPage> {
  final _allergensFormKey = GlobalKey<FormState>();
  TextEditingController? _allergenInput;
  String _allergenQuery = '';

  PreferencesController get _controller => widget.preferencesController;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onControllerChanged);
    final dislikedController = widget.dislikedIngredientsController;
    dislikedController?.addListener(_onControllerChanged);
    if (dislikedController != null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        dislikedController.load();
      });
    }
    _controller.load();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    widget.dislikedIngredientsController?.removeListener(_onControllerChanged);
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  bool _matchesAllergen(AllergenReference reference, String query) {
    final normalized = query.trim().toLowerCase();
    return ReferenceLocalizations.allergenName(
          reference.code,
          reference.displayName,
        ).toLowerCase().contains(normalized) ||
        ReferenceLocalizations.allergenDescription(
          reference.code,
          reference.description,
        ).toLowerCase().contains(normalized);
  }

  Future<void> _saveDietaryPreferences() =>
      _controller.saveDietaryPreferences();

  Future<void> _saveAllergens() async {
    if (!(_allergensFormKey.currentState?.validate() ?? false)) {
      return;
    }
    await _controller.saveAllergens();
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 3,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.state;
    if (!state.hasLoadedData && state.status == PreferencesStatus.loading) {
      return Center(
        child: Semantics(
          label: AppStrings.loadingPreferences,
          child: CircularProgressIndicator(),
        ),
      );
    }
    if (!state.hasLoadedData && state.status == PreferencesStatus.error) {
      return _PreferencesLoadError(
        message: state.errorMessage ?? AppStrings.preferencesLoadSaveFailed,
        onRetry: _controller.reload,
      );
    }

    final busy = state.isSaving || state.status == PreferencesStatus.loading;
    return SingleChildScrollView(
      key: const ValueKey('preferences-scroll'),
      child: ResponsiveContent(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const PageIntro(
              eyebrow: AppStrings.wellnessEyebrow,
              title: AppStrings.preferences,
              subtitle: AppStrings.preferencesSubtitle,
              icon: Icons.tune_rounded,
            ),
            if (state.status == PreferencesStatus.loading) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
            ],
            if (state.saveMessage != null) ...[
              const SizedBox(height: 16),
              _PreferencesBanner(message: state.saveMessage!, success: true),
            ],
            if (state.errorMessage != null) ...[
              const SizedBox(height: 16),
              _PreferencesBanner(
                message: state.errorMessage!,
                action: TextButton(
                  onPressed: busy ? null : _controller.reload,
                  child: const Text(AppStrings.reload),
                ),
              ),
            ],
            const SizedBox(height: 20),
            _buildDietarySection(state, busy),
            const SizedBox(height: 20),
            Form(
              key: _allergensFormKey,
              child: _buildAllergenSection(state, busy),
            ),
            if (widget.dislikedIngredientsController
                case final controller?) ...[
              const SizedBox(height: 20),
              DislikedIngredientsSection(controller: controller),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildDietarySection(PreferencesState state, bool busy) =>
      SectionSurface(
        key: const ValueKey('dietary-preferences-section'),
        title: AppStrings.dietarySectionTitle,
        subtitle: AppStrings.dietaryPreferencesDescription,
        icon: Icons.eco_outlined,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (state.dietaryPreferenceReferences.isEmpty)
              const Text(AppStrings.noDietaryPreferences),
            Wrap(
              spacing: 10,
              runSpacing: 10,
              children: [
                for (final reference in state.dietaryPreferenceReferences)
                  Tooltip(
                    message: _dietaryDescription(reference),
                    child: FilterChip(
                      key: ValueKey('dietary-${reference.code}'),
                      selected: state.selectedDietaryCodes.contains(
                        reference.code,
                      ),
                      showCheckmark: true,
                      avatar: const Icon(Icons.spa_outlined, size: 18),
                      label: Text(
                        ReferenceLocalizations.dietaryName(
                          reference.code,
                          reference.displayName,
                        ),
                      ),
                      onSelected: busy
                          ? null
                          : (selected) =>
                                _controller.setDietaryPreferenceSelected(
                                  reference.code,
                                  selected,
                                ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                key: const ValueKey('save-dietary-preferences'),
                onPressed: busy ? null : _saveDietaryPreferences,
                icon: busy
                    ? const SizedBox.square(
                        dimension: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.save),
                label: const Text(AppStrings.saveDietaryPreferences),
              ),
            ),
          ],
        ),
      );

  String _dietaryDescription(DietaryPreferenceReference reference) {
    final description = ReferenceLocalizations.dietaryDescription(
      reference.code,
      reference.description,
    );
    return reference.isExclusionary
        ? '$description\n${AppStrings.exclusionaryPreference}'
        : description;
  }

  Widget _buildAllergenSection(
    PreferencesState state,
    bool busy,
  ) => SectionSurface(
    key: const ValueKey('allergens-section'),
    title: AppStrings.allergenSectionTitle,
    subtitle: AppStrings.allergenSafety,
    icon: Icons.shield_outlined,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (state.allergenReferences.isEmpty)
          const Text(AppStrings.noAllergens),
        Autocomplete<AllergenReference>(
          displayStringForOption: (reference) =>
              ReferenceLocalizations.allergenName(
                reference.code,
                reference.displayName,
              ),
          optionsBuilder: (value) {
            final query = value.text.trim().toLowerCase();
            if (query.isEmpty) return const Iterable<AllergenReference>.empty();
            return state.allergenReferences.where(
              (reference) =>
                  !state.selectedAllergenCodes.contains(reference.code) &&
                  _matchesAllergen(reference, query),
            );
          },
          onSelected: (reference) {
            _controller.setAllergenSelected(reference.code, true);
            _allergenInput?.clear();
            setState(() => _allergenQuery = '');
          },
          fieldViewBuilder: (context, fieldController, focusNode, onSubmitted) {
            _allergenInput = fieldController;
            return TextField(
              key: const ValueKey('allergen-search'),
              controller: fieldController,
              focusNode: focusNode,
              enabled: !busy,
              textInputAction: TextInputAction.search,
              onChanged: (value) => setState(() => _allergenQuery = value),
              onSubmitted: (_) => onSubmitted(),
              decoration: const InputDecoration(
                hintText: AppStrings.allergenSearchHint,
                prefixIcon: Icon(Icons.search_rounded),
              ),
            );
          },
        ),
        if (_allergenQuery.trim().isNotEmpty &&
            !state.allergenReferences.any(
              (reference) =>
                  !state.selectedAllergenCodes.contains(reference.code) &&
                  _matchesAllergen(reference, _allergenQuery),
            ))
          const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text(AppStrings.allergenNoMatch),
          ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final selected in state.selectedAllergens)
              InputChip(
                key: ValueKey('allergen-${selected.allergen}'),
                label: Text(
                  ReferenceLocalizations.allergenName(
                    selected.allergen,
                    selected.displayName,
                  ),
                ),
                onDeleted: busy
                    ? null
                    : () => _controller.setAllergenSelected(
                        selected.allergen,
                        false,
                      ),
              ),
          ],
        ),
        for (final selected in state.selectedAllergens)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: ExpansionTile(
              key: ValueKey('allergen-editor-${selected.allergen}'),
              title: Text(
                ReferenceLocalizations.allergenName(
                  selected.allergen,
                  selected.displayName,
                ),
              ),
              subtitle: Text(
                ReferenceLocalizations.reactionKindName(
                  selected.reactionKind.wireValue,
                  selected.reactionKind.displayName,
                ),
              ),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: [
                _AllergenEditor(
                  selected: selected,
                  enabled: !busy,
                  onReactionChanged: (reactionKind) => _controller
                      .setAllergenReactionKind(selected.allergen, reactionKind),
                  onNoteChanged: (note) =>
                      _controller.setAllergenNote(selected.allergen, note),
                ),
              ],
            ),
          ),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton.icon(
            key: const ValueKey('save-allergens'),
            onPressed: busy ? null : _saveAllergens,
            icon: busy
                ? const SizedBox.square(
                    dimension: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.save),
            label: const Text(AppStrings.saveAllergens),
          ),
        ),
      ],
    ),
  );
}

class _AllergenEditor extends StatefulWidget {
  const _AllergenEditor({
    required this.selected,
    required this.enabled,
    required this.onReactionChanged,
    required this.onNoteChanged,
  });

  final SelectedAllergen selected;
  final bool enabled;
  final ValueChanged<ReactionKind> onReactionChanged;
  final ValueChanged<String> onNoteChanged;

  @override
  State<_AllergenEditor> createState() => _AllergenEditorState();
}

class _AllergenEditorState extends State<_AllergenEditor> {
  late final TextEditingController _noteController;

  @override
  void initState() {
    super.initState();
    _noteController = TextEditingController(text: widget.selected.note ?? '');
  }

  @override
  void didUpdateWidget(covariant _AllergenEditor oldWidget) {
    super.didUpdateWidget(oldWidget);
    final nextText = widget.selected.note ?? '';
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
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      InputDecorator(
        decoration: const InputDecoration(
          labelText: AppStrings.reactionKind,
          border: OutlineInputBorder(),
        ),
        child: DropdownButtonHideUnderline(
          child: DropdownButton<ReactionKind>(
            key: ValueKey('reaction-${widget.selected.allergen}'),
            value: widget.selected.reactionKind,
            isExpanded: true,
            items: [
              for (final reactionKind in ReactionKind.values)
                DropdownMenuItem(
                  value: reactionKind,
                  child: Text(
                    ReferenceLocalizations.reactionKindName(
                      reactionKind.wireValue,
                      reactionKind.displayName,
                    ),
                  ),
                ),
            ],
            onChanged: widget.enabled
                ? (reactionKind) {
                    if (reactionKind != null) {
                      widget.onReactionChanged(reactionKind);
                    }
                  }
                : null,
          ),
        ),
      ),
      const SizedBox(height: 12),
      TextFormField(
        key: ValueKey('note-${widget.selected.allergen}'),
        controller: _noteController,
        enabled: widget.enabled,
        maxLength: 255,
        maxLengthEnforcement: MaxLengthEnforcement.none,
        decoration: const InputDecoration(
          labelText: AppStrings.optionalNote,
          alignLabelWithHint: true,
          border: OutlineInputBorder(),
        ),
        validator: PreferencesValidation.validateAllergenNote,
        onChanged: widget.onNoteChanged,
      ),
    ],
  );
}

class _PreferencesLoadError extends StatelessWidget {
  const _PreferencesLoadError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          FilledButton(onPressed: onRetry, child: const Text(AppStrings.retry)),
        ],
      ),
    ),
  );
}

class _PreferencesBanner extends StatelessWidget {
  const _PreferencesBanner({
    required this.message,
    this.success = false,
    this.action,
  });

  final String message;
  final bool success;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Card(
    color: success
        ? Theme.of(context).colorScheme.secondaryContainer
        : Theme.of(context).colorScheme.errorContainer,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(message)),
          ?action,
        ],
      ),
    ),
  );
}
