import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/admin/recipes/application/admin_recipe_controller.dart';
import 'package:smart_meal_planner/features/admin/recipes/data/admin_recipe_models.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/recipes/data/recipe_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class AdminRecipeEditPage extends StatefulWidget {
  const AdminRecipeEditPage({
    super.key,
    required this.sessionController,
    required this.controller,
    this.publicId,
  });

  final SessionController sessionController;
  final AdminRecipeController controller;
  final String? publicId;

  @override
  State<AdminRecipeEditPage> createState() => _AdminRecipeEditPageState();
}

class _AdminRecipeEditPageState extends State<AdminRecipeEditPage> {
  AdminRecipeController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _controller.loadReferences();
      final publicId = widget.publicId;
      if (publicId != null) {
        _controller.loadDetail(publicId);
      } else {
        _controller.prepareCreate();
      }
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
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
    if (_controller.referencesErrorMessage != null) {
      return ResponsiveContent(
        child: _ErrorPanel(
          message: _controller.referencesErrorMessage!,
          onRetry: _controller.loadReferences,
          retryKey: const ValueKey('admin-recipe-references-retry'),
        ),
      );
    }
    if (!_controller.referencesLoaded) {
      return const Center(child: CircularProgressIndicator());
    }

    final detailState = _controller.detailState;
    if (widget.publicId != null &&
        detailState.status == AdminRecipeDetailStatus.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (widget.publicId != null &&
        detailState.status == AdminRecipeDetailStatus.error) {
      return ResponsiveContent(
        child: _ErrorPanel(
          message: detailState.errorMessage ?? AppStrings.adminRecipeLoadFailed,
          onRetry: () => _controller.loadDetail(widget.publicId!),
          retryKey: const ValueKey('admin-recipe-detail-retry'),
        ),
      );
    }
    if (widget.publicId != null && detailState.item == null) {
      return const Center(child: CircularProgressIndicator());
    }

    final detail = widget.publicId == null ? null : detailState.item;
    final isDraft = detail == null || detail.status == 'DRAFT';
    return ResponsiveContent(
      child: ListView(
        key: const ValueKey('admin-recipe-edit-page'),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  detail == null
                      ? AppStrings.adminCreateRecipe
                      : AppStrings.adminEditRecipe,
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              IconButton(
                tooltip: AppStrings.adminBackToRecipes,
                onPressed: () => context.go('/admin/recipes'),
                icon: const Icon(Icons.close),
              ),
            ],
          ),
          const SizedBox(height: 12),
          if (_controller.actionErrorMessage != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Text(_controller.actionErrorMessage!),
            ),
          if (detail != null && !isDraft)
            _AdminLifecycleActions(
              detail: detail,
              busy: _controller.mutatingPublicId == detail.publicId,
              onArchive: () => _confirmLifecycle(context, detail, archive: true),
            ),
          if (detail != null && !isDraft)
            const SizedBox(height: 12),
          if (isDraft)
            _AdminRecipeForm(
              key: ValueKey('admin-recipe-form-${detail?.publicId ?? 'new'}'),
              controller: _controller,
              initial: detail,
              publicId: detail?.publicId,
              onSaved: (saved) {
                if (detail == null && mounted) {
                  context.go('/admin/recipes/${saved.publicId}/edit');
                } else if (mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text(AppStrings.adminRecipeSaved)),
                  );
                }
              },
              onPublish: detail == null
                  ? null
                  : () => _confirmLifecycle(context, detail, archive: false),
            )
          else
            _AdminRecipeReadOnly(detail: detail),
        ],
      ),
    );
  }

  Future<void> _confirmLifecycle(
    BuildContext context,
    AdminRecipeDetail detail, {
    required bool archive,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(archive ? AppStrings.adminArchive : AppStrings.adminPublish),
        content: Text(
          archive
              ? AppStrings.adminArchiveConfirmation
              : AppStrings.adminPublishConfirmation,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text(AppStrings.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(archive ? AppStrings.adminArchive : AppStrings.adminPublish),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    final result = archive
        ? await _controller.archive(detail.publicId)
        : await _controller.publish(detail.publicId);
    if (!context.mounted) return;
    if (result != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            archive ? AppStrings.adminRecipeArchived : AppStrings.adminRecipePublished,
          ),
        ),
      );
    }
  }
}

final class _AdminLifecycleActions extends StatelessWidget {
  const _AdminLifecycleActions({
    required this.detail,
    required this.busy,
    required this.onArchive,
  });

  final AdminRecipeDetail detail;
  final bool busy;
  final VoidCallback onArchive;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          Chip(label: Text(_statusLabel(detail.status))),
          const Spacer(),
          if (detail.status == 'PUBLISHED')
            OutlinedButton(
              key: const ValueKey('admin-recipe-detail-archive'),
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
    ),
  );

  static String _statusLabel(String status) => switch (status) {
    'DRAFT' => AppStrings.adminDraft,
    'PUBLISHED' => AppStrings.adminPublished,
    'ARCHIVED' => AppStrings.adminArchived,
    _ => status,
  };
}

final class _AdminRecipeReadOnly extends StatelessWidget {
  const _AdminRecipeReadOnly({required this.detail});

  final AdminRecipeDetail detail;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(detail.title, style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 8),
      if (detail.summary != null) Text(detail.summary!),
      const SizedBox(height: 12),
      Text('${AppStrings.adminSlug}: ${detail.slug}'),
      Text('${AppStrings.adminSource}: ${detail.source}'),
      if (detail.publishedAt != null)
        Text('${AppStrings.adminPublishedAt}: ${detail.publishedAt}'),
      if (detail.archivedAt != null)
        Text('${AppStrings.adminArchivedAt}: ${detail.archivedAt}'),
      Text('${AppStrings.recipeServings}: ${detail.servings}'),
      if (detail.totalMinutes != null)
        Text('${AppStrings.recipeMinutes}: ${detail.totalMinutes}'),
      const SizedBox(height: 16),
      Text(AppStrings.recipeIngredients, style: Theme.of(context).textTheme.titleMedium),
      for (final item in detail.ingredients)
        ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(item.ingredientDisplayName),
          subtitle: Text(
            item.quantity == null || item.unitCode == null
                ? AppStrings.recipeToTaste
                : '${item.quantity} ${item.unitCode}',
          ),
        ),
      const SizedBox(height: 12),
      Text(AppStrings.recipeSteps, style: Theme.of(context).textTheme.titleMedium),
      for (final step in detail.steps)
        ListTile(
          contentPadding: EdgeInsets.zero,
          leading: CircleAvatar(child: Text('${step.stepNumber}')),
          title: Text(step.instruction),
        ),
      if (detail.nutrition != null) ...[
        const SizedBox(height: 12),
        Text(AppStrings.recipeNutrition, style: Theme.of(context).textTheme.titleMedium),
        for (final value in detail.nutrition!.values)
          Text('${value.nutrientDisplayName}: ${value.amountPerServing} ${value.unitDisplayName}'),
      ],
    ],
  );
}

final class _AdminRecipeForm extends StatefulWidget {
  const _AdminRecipeForm({
    super.key,
    required this.controller,
    required this.initial,
    required this.publicId,
    required this.onSaved,
    required this.onPublish,
  });

  final AdminRecipeController controller;
  final AdminRecipeDetail? initial;
  final String? publicId;
  final ValueChanged<AdminRecipeDetail> onSaved;
  final VoidCallback? onPublish;

  @override
  State<_AdminRecipeForm> createState() => _AdminRecipeFormState();
}

class _AdminRecipeFormState extends State<_AdminRecipeForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _titleController;
  late final TextEditingController _slugController;
  late final TextEditingController _summaryController;
  late final TextEditingController _servingsController;
  late final TextEditingController _prepController;
  late final TextEditingController _cookController;
  late final TextEditingController _instructionsController;
  late final TextEditingController _imageController;
  late final List<_IngredientDraft> _ingredients;
  late final List<_StepDraft> _steps;
  late String _difficulty;
  late final Set<String> _tagCodes;
  late final Set<String> _mealSlotCodes;
  String? _validationMessage;

  @override
  void initState() {
    super.initState();
    final initial = widget.initial;
    _titleController = TextEditingController(text: initial?.title ?? '');
    _slugController = TextEditingController(text: initial?.slug ?? '');
    _summaryController = TextEditingController(text: initial?.summary ?? '');
    _servingsController = TextEditingController(
      text: initial == null ? '1' : '${initial.servings}',
    );
    _prepController = TextEditingController(
      text: initial?.prepMinutes?.toString() ?? '',
    );
    _cookController = TextEditingController(
      text: initial?.cookMinutes?.toString() ?? '',
    );
    _instructionsController = TextEditingController(
      text: initial?.instructionsNote ?? '',
    );
    _imageController = TextEditingController(text: initial?.imageUrl ?? '');
    _difficulty = initial?.difficulty ?? 'EASY';
    _tagCodes = {
      if (initial != null) ...initial.tags.map((tag) => tag.code),
    };
    _mealSlotCodes = {
      if (initial != null) ...initial.mealSlots.map((slot) => slot.code),
    };
    _ingredients = [
      for (final item in initial?.ingredients ?? const <RecipeIngredient>[])
        _IngredientDraft.fromRecipe(item),
    ];
    _steps = [
      for (final item in initial?.steps ?? const <RecipeStep>[])
        _StepDraft.fromRecipe(item),
    ];
  }

  @override
  void dispose() {
    _titleController.dispose();
    _slugController.dispose();
    _summaryController.dispose();
    _servingsController.dispose();
    _prepController.dispose();
    _cookController.dispose();
    _instructionsController.dispose();
    _imageController.dispose();
    for (final item in _ingredients) {
      item.dispose();
    }
    for (final item in _steps) {
      item.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Form(
    key: _formKey,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _textField(_titleController, AppStrings.adminRecipeTitle, required: true),
        _textField(_slugController, AppStrings.adminSlug, required: true),
        _textField(_summaryController, AppStrings.adminRecipeSummary),
        Row(
          children: [
            Expanded(
              child: _textField(
                _servingsController,
                AppStrings.recipeServings,
                required: true,
                keyboardType: TextInputType.number,
                validator: _positiveInteger,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: DropdownButtonFormField<String>(
                initialValue: _difficulty,
                decoration: const InputDecoration(labelText: AppStrings.adminDifficulty),
                items: const [
                  DropdownMenuItem(value: 'EASY', child: Text(AppStrings.adminEasy)),
                  DropdownMenuItem(value: 'MEDIUM', child: Text(AppStrings.adminMedium)),
                  DropdownMenuItem(value: 'HARD', child: Text(AppStrings.adminHard)),
                ],
                onChanged: (value) {
                  if (value != null) setState(() => _difficulty = value);
                },
              ),
            ),
          ],
        ),
        Row(
          children: [
            Expanded(
              child: _textField(
                _prepController,
                AppStrings.adminPrepMinutes,
                keyboardType: TextInputType.number,
                validator: _optionalNonNegativeInteger,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: _textField(
                _cookController,
                AppStrings.adminCookMinutes,
                keyboardType: TextInputType.number,
                validator: _optionalNonNegativeInteger,
              ),
            ),
          ],
        ),
        _textField(_instructionsController, AppStrings.adminInstructionsNote, maxLines: 3),
        _textField(_imageController, AppStrings.adminImageUrl),
        const SizedBox(height: 20),
        Text(AppStrings.recipeIngredients, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        for (var index = 0; index < _ingredients.length; index++)
          _ingredientEditor(index, _ingredients[index]),
        OutlinedButton.icon(
          key: const ValueKey('admin-recipe-add-ingredient'),
          onPressed: _addIngredient,
          icon: const Icon(Icons.add),
          label: const Text(AppStrings.adminAddIngredient),
        ),
        const SizedBox(height: 20),
        Text(AppStrings.recipeSteps, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        for (var index = 0; index < _steps.length; index++)
          _stepEditor(index, _steps[index]),
        OutlinedButton.icon(
          key: const ValueKey('admin-recipe-add-step'),
          onPressed: _addStep,
          icon: const Icon(Icons.add),
          label: const Text(AppStrings.adminAddStep),
        ),
        const SizedBox(height: 20),
        _referenceSelection(context),
        if (_validationMessage != null) ...[
          const SizedBox(height: 12),
          Text(_validationMessage!),
        ],
        const SizedBox(height: 20),
        Wrap(
          spacing: 12,
          runSpacing: 8,
          children: [
            FilledButton(
              key: const ValueKey('admin-recipe-save'),
              onPressed: widget.controller.saving ? null : _save,
              child: widget.controller.saving
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text(AppStrings.adminSaveDraft),
            ),
            if (widget.onPublish != null)
              OutlinedButton(
                key: const ValueKey('admin-recipe-publish-detail'),
                onPressed: widget.controller.saving ? null : widget.onPublish,
                child: const Text(AppStrings.adminPublish),
              ),
          ],
        ),
      ],
    ),
  );

  Widget _textField(
    TextEditingController controller,
    String label, {
    bool required = false,
    int maxLines = 1,
    TextInputType? keyboardType,
    String? Function(String?)? validator,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextFormField(
      controller: controller,
      keyboardType: keyboardType,
      maxLines: maxLines,
      decoration: InputDecoration(labelText: label),
      validator: validator ??
          (required
              ? (value) => value == null || value.trim().isEmpty
                  ? AppStrings.adminFieldRequired
                  : null
              : null),
    ),
  );

  Widget _ingredientEditor(
    int index,
    _IngredientDraft draft,
  ) => Card(
    key: ValueKey('admin-recipe-ingredient-$index'),
    margin: const EdgeInsets.only(bottom: 8),
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(child: Text(draft.displayName)),
              IconButton(
                tooltip: AppStrings.adminRemove,
                onPressed: () => setState(() {
                  final removed = _ingredients.removeAt(index);
                  removed.dispose();
                }),
                icon: const Icon(Icons.delete_outline),
              ),
            ],
          ),
          Row(
            children: [
              Expanded(
                child: TextFormField(
                  controller: draft.quantity,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: AppStrings.adminQuantity),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: TextFormField(
                  controller: draft.unitCode,
                  decoration: const InputDecoration(labelText: AppStrings.adminUnitCode),
                ),
              ),
            ],
          ),
          TextFormField(
            controller: draft.preparationNote,
            decoration: const InputDecoration(labelText: AppStrings.adminPreparationNote),
          ),
          TextFormField(
            controller: draft.sectionLabel,
            decoration: const InputDecoration(labelText: AppStrings.adminSectionLabel),
          ),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            value: draft.optional,
            title: const Text(AppStrings.adminOptional),
            onChanged: (value) => setState(() => draft.optional = value ?? false),
          ),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            value: draft.allowSubstitution,
            title: const Text(AppStrings.adminAllowSubstitution),
            onChanged: (value) =>
                setState(() => draft.allowSubstitution = value ?? false),
          ),
        ],
      ),
    ),
  );

  Widget _stepEditor(int index, _StepDraft draft) => Card(
    key: ValueKey('admin-recipe-step-$index'),
    margin: const EdgeInsets.only(bottom: 8),
    child: Padding(
      padding: const EdgeInsets.all(12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              children: [
                TextFormField(
                  controller: draft.instruction,
                  maxLines: 3,
                  decoration: InputDecoration(
                    labelText: '${AppStrings.adminStep} ${index + 1}',
                  ),
                  validator: (value) => value == null || value.trim().isEmpty
                      ? AppStrings.adminFieldRequired
                      : null,
                ),
                TextFormField(
                  controller: draft.durationMinutes,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: AppStrings.adminStepDuration,
                  ),
                  validator: _optionalNonNegativeInteger,
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: AppStrings.adminRemove,
            onPressed: () => setState(() {
              final removed = _steps.removeAt(index);
              removed.dispose();
            }),
            icon: const Icon(Icons.delete_outline),
          ),
        ],
      ),
    ),
  );

  Widget _referenceSelection(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(AppStrings.adminTags, style: Theme.of(context).textTheme.titleMedium),
      for (final tag in widget.controller.tags)
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _tagCodes.contains(tag.code),
          title: Text(tag.displayName),
          onChanged: (value) => setState(() {
            if (value == true) {
              _tagCodes.add(tag.code);
            } else {
              _tagCodes.remove(tag.code);
            }
          }),
        ),
      const SizedBox(height: 8),
      Text(AppStrings.adminMealSlots, style: Theme.of(context).textTheme.titleMedium),
      for (final slot in widget.controller.mealSlots)
        CheckboxListTile(
          contentPadding: EdgeInsets.zero,
          value: _mealSlotCodes.contains(slot.code),
          title: Text(slot.displayName),
          onChanged: (value) => setState(() {
            if (value == true) {
              _mealSlotCodes.add(slot.code);
            } else {
              _mealSlotCodes.remove(slot.code);
            }
          }),
        ),
    ],
  );

  Future<void> _addIngredient() async {
    final selectedIds = {for (final item in _ingredients) item.publicId};
    final selected = await showDialog<IngredientCatalogItem>(
      context: context,
      builder: (context) => _IngredientPickerDialog(
        controller: widget.controller,
        selectedIds: selectedIds,
      ),
    );
    if (selected == null || !mounted || selectedIds.contains(selected.publicId)) {
      return;
    }
    setState(() => _ingredients.add(_IngredientDraft.fromCatalog(selected)));
  }

  void _addStep() => setState(() => _steps.add(_StepDraft.empty()));

  Future<void> _save() async {
    setState(() => _validationMessage = null);
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final servings = int.tryParse(_servingsController.text.trim());
    if (servings == null || servings < 1) {
      setState(() => _validationMessage = AppStrings.adminInvalidNumber);
      return;
    }
    final ingredients = <AdminRecipeIngredientRequest>[];
    for (final item in _ingredients) {
      final quantityText = item.quantity.text.trim();
      final unitText = item.unitCode.text.trim();
      if (quantityText.isEmpty != unitText.isEmpty) {
        setState(() => _validationMessage = AppStrings.adminQuantityUnitTogether);
        return;
      }
      final quantity = quantityText.isEmpty ? null : double.tryParse(quantityText);
      if (quantityText.isNotEmpty && (quantity == null || quantity <= 0)) {
        setState(() => _validationMessage = AppStrings.adminInvalidNumber);
        return;
      }
      ingredients.add(
        AdminRecipeIngredientRequest(
          ingredientPublicId: item.publicId,
          quantity: quantity,
          unitCode: unitText.isEmpty ? null : unitText,
          preparationNote: _nullable(item.preparationNote.text),
          optional: item.optional,
          allowSubstitution: item.allowSubstitution,
          sectionLabel: _nullable(item.sectionLabel.text),
        ),
      );
    }
    final steps = <AdminRecipeStepRequest>[];
    for (var index = 0; index < _steps.length; index++) {
      steps.add(
        AdminRecipeStepRequest(
          stepNumber: index + 1,
          instruction: _steps[index].instruction.text.trim(),
          durationMinutes: _optionalInteger(
            _steps[index].durationMinutes.text,
          ),
        ),
      );
    }
    final result = await widget.controller.save(
      AdminRecipeUpsertRequest(
        title: _titleController.text.trim(),
        slug: _slugController.text.trim(),
        summary: _nullable(_summaryController.text),
        servings: servings,
        prepMinutes: _optionalInteger(_prepController.text),
        cookMinutes: _optionalInteger(_cookController.text),
        difficulty: _difficulty,
        instructionsNote: _nullable(_instructionsController.text),
        imageUrl: _nullable(_imageController.text),
        ingredients: ingredients,
        steps: steps,
        tagCodes: _tagCodes.toList(),
        mealSlotCodes: _mealSlotCodes.toList(),
      ),
      publicId: widget.publicId,
    );
    if (result != null && mounted) widget.onSaved(result);
  }

  static String? _positiveInteger(String? value) {
    final parsed = int.tryParse(value?.trim() ?? '');
    return parsed == null || parsed < 1 ? AppStrings.adminInvalidNumber : null;
  }

  static String? _optionalNonNegativeInteger(String? value) {
    if (value == null || value.trim().isEmpty) return null;
    final parsed = int.tryParse(value.trim());
    return parsed == null || parsed < 0 ? AppStrings.adminInvalidNumber : null;
  }

  static int? _optionalInteger(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : int.tryParse(trimmed);
  }

  static String? _nullable(String value) {
    final trimmed = value.trim();
    return trimmed.isEmpty ? null : trimmed;
  }
}

final class _IngredientDraft {
  _IngredientDraft({
    required this.publicId,
    required this.displayName,
    required String? quantityValue,
    required String? unitValue,
    required String? preparationValue,
    required String? sectionValue,
    required this.optional,
    required this.allowSubstitution,
  })  : quantity = TextEditingController(text: quantityValue ?? ''),
        unitCode = TextEditingController(text: unitValue ?? ''),
        preparationNote = TextEditingController(text: preparationValue ?? ''),
        sectionLabel = TextEditingController(text: sectionValue ?? '');

  factory _IngredientDraft.fromRecipe(RecipeIngredient item) => _IngredientDraft(
    publicId: item.ingredientPublicId,
    displayName: item.ingredientDisplayName,
    quantityValue: item.quantity?.toString(),
    unitValue: item.unitCode,
    preparationValue: item.preparationNote,
    sectionValue: item.sectionLabel,
    optional: item.optional,
    allowSubstitution: item.allowSubstitution,
  );

  factory _IngredientDraft.fromCatalog(IngredientCatalogItem item) => _IngredientDraft(
    publicId: item.publicId,
    displayName: item.displayName,
    quantityValue: null,
    unitValue: null,
    preparationValue: null,
    sectionValue: null,
    optional: false,
    allowSubstitution: false,
  );

  final String publicId;
  final String displayName;
  final TextEditingController quantity;
  final TextEditingController unitCode;
  final TextEditingController preparationNote;
  final TextEditingController sectionLabel;
  bool optional;
  bool allowSubstitution;

  void dispose() {
    quantity.dispose();
    unitCode.dispose();
    preparationNote.dispose();
    sectionLabel.dispose();
  }
}

final class _StepDraft {
  _StepDraft({
    required String instructionValue,
    required String? durationValue,
  })  : instruction = TextEditingController(text: instructionValue),
        durationMinutes = TextEditingController(text: durationValue ?? '');

  factory _StepDraft.empty() =>
      _StepDraft(instructionValue: '', durationValue: null);

  factory _StepDraft.fromRecipe(RecipeStep item) =>
      _StepDraft(
        instructionValue: item.instruction,
        durationValue: item.durationMinutes?.toString(),
      );

  final TextEditingController instruction;
  final TextEditingController durationMinutes;

  void dispose() {
    instruction.dispose();
    durationMinutes.dispose();
  }
}

final class _IngredientPickerDialog extends StatefulWidget {
  const _IngredientPickerDialog({
    required this.controller,
    required this.selectedIds,
  });

  final AdminRecipeController controller;
  final Set<String> selectedIds;

  @override
  State<_IngredientPickerDialog> createState() => _IngredientPickerDialogState();
}

class _IngredientPickerDialogState extends State<_IngredientPickerDialog> {
  late final TextEditingController _searchController;
  late Future<IngredientCatalogPage> _future;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _future = widget.controller.searchIngredients('');
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _search() {
    setState(() {
      _future = widget.controller.searchIngredients(_searchController.text);
    });
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    key: const ValueKey('admin-ingredient-picker'),
    title: const Text(AppStrings.adminChooseIngredient),
    content: SizedBox(
      width: MediaQuery.of(context).size.width < 528
          ? MediaQuery.of(context).size.width - 48
          : 480,
      height: 420,
      child: Column(
        children: [
          TextField(
            key: const ValueKey('admin-ingredient-search'),
            controller: _searchController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _search(),
            decoration: InputDecoration(
              labelText: AppStrings.adminSearch,
              suffixIcon: IconButton(
                key: const ValueKey('admin-ingredient-search-submit'),
                onPressed: _search,
                icon: const Icon(Icons.search),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Expanded(
            child: FutureBuilder<IngredientCatalogPage>(
              future: _future,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return Center(child: Text(AppStrings.adminIngredientLoadFailed));
                }
                final items = snapshot.data?.content ?? const <IngredientCatalogItem>[];
                if (items.isEmpty) {
                  return const Center(child: Text(AppStrings.adminIngredientEmpty));
                }
                return ListView.builder(
                  key: const ValueKey('admin-ingredient-picker-list'),
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    final selected = widget.selectedIds.contains(item.publicId);
                    return ListTile(
                      key: ValueKey('admin-ingredient-picker-item-${item.publicId}'),
                      title: Text(item.displayName),
                      subtitle: Text(item.category?.displayName ?? item.code),
                      trailing: selected
                          ? const Text(AppStrings.adminAlreadySelected)
                          : const Icon(Icons.add),
                      enabled: !selected,
                      onTap: selected
                          ? null
                          : () => Navigator.of(context).pop(item),
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.of(context).pop(),
        child: const Text(AppStrings.cancel),
      ),
    ],
  );
}

final class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({
    required this.message,
    required this.onRetry,
    required this.retryKey,
  });

  final String message;
  final VoidCallback onRetry;
  final Key retryKey;

  @override
  Widget build(BuildContext context) => Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(key: retryKey, onPressed: onRetry, child: const Text(AppStrings.retry)),
    ],
  );
}
