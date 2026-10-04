import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_validation.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_ingredient_picker.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

/// Full-page create or metadata-only edit form for one physical Pantry lot.
class PantryItemForm extends StatefulWidget {
  const PantryItemForm({
    super.key,
    required this.sessionController,
    required this.controller,
    this.catalogRepository,
    this.publicId,
  });

  final SessionController sessionController;
  final PantryController controller;
  final CatalogRepository? catalogRepository;
  final String? publicId;

  @override
  State<PantryItemForm> createState() => _PantryItemFormState();
}

class _PantryItemFormState extends State<PantryItemForm> {
  final _formKey = GlobalKey<FormState>();
  final _quantityController = TextEditingController();
  final _unitController = TextEditingController();
  final _noteController = TextEditingController();
  IngredientCatalogItem? _ingredient;
  IngredientCatalogDetail? _ingredientDetail;
  String? _foodPublicId;
  PantryStorageLocation? _storage;
  DateTime? _acquiredOn;
  DateTime? _expiryDate;
  PantryExpiryKind _expiryKind = PantryExpiryKind.unknown;
  PantryExpiryConfidence _expiryConfidence = PantryExpiryConfidence.unknown;
  bool _pickerOpen = false;
  bool _mappingLoading = false;
  bool _unitUserEdited = false;
  bool _editInitialized = false;
  int _mappingGeneration = 0;
  String? _mappingError;
  String? _ingredientError;
  String? _storageError;
  String? _expiryError;

  bool get _editing => widget.publicId != null;
  bool get _busy =>
      widget.controller.mutationState.status == PantryMutationStatus.submitting;

  @override
  void initState() {
    super.initState();
    widget.controller.clearMutationFeedback();
    widget.controller.addListener(_onControllerChanged);
    if (_editing) {
      final detail = widget.controller.detailState;
      if (detail.publicId == widget.publicId && detail.item != null) {
        _initializeEdit(detail.item!);
      } else {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) widget.controller.loadDetail(widget.publicId!);
        });
      }
    }
  }

  @override
  void dispose() {
    _mappingGeneration++;
    widget.controller.removeListener(_onControllerChanged);
    _quantityController.dispose();
    _unitController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    if (!mounted) return;
    if (_editing && !_editInitialized) {
      final detail = widget.controller.detailState;
      if (detail.publicId == widget.publicId && detail.item != null) {
        _initializeEdit(detail.item!);
      }
    }
    setState(() {});
  }

  void _initializeEdit(PantryItem item) {
    _editInitialized = true;
    _storage = item.storageLocation;
    _acquiredOn = item.acquiredOn;
    _expiryDate = item.expiryDate;
    _expiryKind = item.expiryKind;
    _expiryConfidence = item.expiryConfidence;
    _noteController.text = item.note ?? '';
  }

  Future<void> _selectIngredient(IngredientCatalogItem ingredient) async {
    final catalog = widget.catalogRepository;
    if (catalog == null) return;
    final generation = ++_mappingGeneration;
    setState(() {
      _ingredient = ingredient;
      _ingredientDetail = null;
      _foodPublicId = null;
      _ingredientError = null;
      _mappingError = null;
      _mappingLoading = true;
      _pickerOpen = false;
    });
    try {
      final detail = await catalog.getIngredient(ingredient.publicId);
      if (!mounted || generation != _mappingGeneration) return;
      setState(() {
        _ingredientDetail = detail;
        _mappingLoading = false;
        if (!_unitUserEdited && detail.defaultUnitCode != null) {
          _unitController.text = detail.defaultUnitCode!;
        }
      });
    } on Object catch (error) {
      if (!mounted || generation != _mappingGeneration) return;
      setState(() {
        _mappingLoading = false;
        _mappingError = error is ApiTransportException
            ? AppStrings.unableToReachService
            : AppStrings.pantryIngredientMappingsFailed;
      });
    }
  }

  Future<void> _retryMappings() async {
    final ingredient = _ingredient;
    if (ingredient != null) await _selectIngredient(ingredient);
  }

  Future<void> _pickDate({required bool expiry}) async {
    final selected = await showDatePicker(
      context: context,
      initialDate: (expiry ? _expiryDate : _acquiredOn) ?? DateTime.now(),
      firstDate: DateTime(1),
      lastDate: DateTime(9999, 12, 31),
      locale: const Locale('vi', 'VN'),
    );
    if (selected == null || !mounted) return;
    setState(() {
      if (expiry) {
        _expiryDate = selected;
        if (_expiryKind == PantryExpiryKind.unknown) {
          _expiryKind = PantryExpiryKind.useBy;
        }
        if (_expiryConfidence == PantryExpiryConfidence.unknown) {
          _expiryConfidence = PantryExpiryConfidence.labelled;
        }
      } else {
        _acquiredOn = selected;
      }
      _expiryError = null;
    });
  }

  void _clearDate({required bool expiry}) {
    setState(() {
      if (expiry) {
        _expiryDate = null;
        _expiryKind = PantryExpiryKind.unknown;
        _expiryConfidence = PantryExpiryConfidence.unknown;
      } else {
        _acquiredOn = null;
      }
      _expiryError = null;
    });
  }

  Future<void> _submit() async {
    if (_busy || (_editing && !_editInitialized)) return;
    final validFields = _formKey.currentState?.validate() ?? false;
    final validIngredient = _editing || _ingredient != null;
    final validStorage = _storage != null;
    final validExpiry = PantryValidation.isValidExpiry(
      acquiredOn: _acquiredOn,
      expiryDate: _expiryDate,
      expiryKind: _expiryKind,
      expiryConfidence: _expiryConfidence,
    );
    setState(() {
      _ingredientError = validIngredient
          ? null
          : AppStrings.pantryIngredientRequired;
      _storageError = validStorage ? null : AppStrings.pantryStorageRequired;
      _expiryError = validExpiry ? null : AppStrings.pantryExpiryInvalid;
    });
    if (!validFields || !validIngredient || !validStorage || !validExpiry) {
      return;
    }

    final note = PantryNote.normalize(_noteController.text);
    PantryItem? saved;
    if (_editing) {
      saved = await widget.controller.updateMetadata(
        widget.publicId!,
        UpdatePantryMetadataRequest(
          storageLocation: _storage!,
          acquiredOn: _acquiredOn,
          expiryDate: _expiryDate,
          expiryKind: _expiryKind,
          expiryConfidence: _expiryConfidence,
          note: note,
        ),
      );
    } else {
      saved = await widget.controller.createItem(
        CreatePantryItemRequest(
          ingredientPublicId: _ingredient!.publicId,
          foodPublicId: _foodPublicId,
          quantity: PantryDecimal.parse(_quantityController.text).toString(),
          unitCode: PantryUnitCode.normalize(_unitController.text),
          storageLocation: _storage!,
          acquiredOn: _acquiredOn,
          expiryDate: _expiryDate,
          expiryKind: _expiryKind,
          expiryConfidence: _expiryConfidence,
          note: note,
        ),
      );
    }
    if (mounted && saved != null) {
      ScaffoldMessenger.maybeOf(context)?.showSnackBar(
        SnackBar(
          content: Text(
            _editing
                ? AppStrings.pantryMetadataUpdated
                : AppStrings.pantryCreateSucceeded,
          ),
        ),
      );
      context.go('/pantry/${saved.publicId}');
    }
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 6,
    content: SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: ValueKey(_editing ? 'pantry-edit-form' : 'pantry-create-form'),
          children: [
            Text(
              _editing
                  ? AppStrings.pantryEditMetadata
                  : AppStrings.pantryCreate,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 16),
            if (_editing && !_editInitialized)
              _editLoadingOrError()
            else
              _form(),
          ],
        ),
      ),
    ),
  );

  Widget _editLoadingOrError() {
    final state = widget.controller.detailState;
    if (state.status == PantryLoadStatus.error) {
      return Column(
        children: [
          Text(state.errorMessage ?? AppStrings.pantryDetailLoadFailed),
          TextButton(
            key: const ValueKey('pantry-edit-load-retry'),
            onPressed: () => widget.controller.loadDetail(widget.publicId!),
            child: const Text(AppStrings.retry),
          ),
        ],
      );
    }
    return const Center(child: CircularProgressIndicator());
  }

  Widget _form() => Form(
    key: _formKey,
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!_editing) ..._createFields(),
        _storageField(),
        const SizedBox(height: 12),
        _dateField(AppStrings.pantryAcquiredOn, _acquiredOn, expiry: false),
        _dateField(AppStrings.pantryExpiryDate, _expiryDate, expiry: true),
        if (_expiryDate != null) ...[
          _expiryKindField(),
          const SizedBox(height: 12),
          _expiryConfidenceField(),
        ],
        if (_expiryError != null)
          Text(
            _expiryError!,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        const SizedBox(height: 12),
        TextFormField(
          key: const ValueKey('pantry-form-note'),
          controller: _noteController,
          enabled: !_busy,
          maxLength: 255,
          maxLengthEnforcement: MaxLengthEnforcement.none,
          maxLines: 3,
          decoration: const InputDecoration(
            labelText: AppStrings.pantryNote,
            border: OutlineInputBorder(),
          ),
          validator: (value) => PantryValidation.isValidNote(value)
              ? null
              : AppStrings.pantryNoteTooLong,
        ),
        if (widget.controller.mutationState.status ==
            PantryMutationStatus.error)
          Text(
            widget.controller.mutationState.errorMessage ??
                AppStrings.pantrySaveFailed,
            key: const ValueKey('pantry-form-error'),
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 12,
          children: [
            FilledButton(
              key: const ValueKey('pantry-form-save'),
              onPressed: _busy ? null : _submit,
              child: Text(_busy ? AppStrings.loading : AppStrings.save),
            ),
            TextButton(
              key: const ValueKey('pantry-form-cancel'),
              onPressed: _busy
                  ? null
                  : () => context.go(
                      _editing ? '/pantry/${widget.publicId}' : '/pantry',
                    ),
              child: const Text(AppStrings.cancel),
            ),
          ],
        ),
      ],
    ),
  );

  List<Widget> _createFields() => [
    OutlinedButton.icon(
      key: const ValueKey('pantry-select-ingredient'),
      onPressed: _busy
          ? null
          : () => setState(() => _pickerOpen = !_pickerOpen),
      icon: const Icon(Icons.search),
      label: Text(
        _ingredient?.displayName ?? AppStrings.pantrySelectIngredient,
      ),
    ),
    if (_ingredientError != null)
      Text(
        _ingredientError!,
        style: TextStyle(color: Theme.of(context).colorScheme.error),
      ),
    if (_pickerOpen && widget.catalogRepository != null)
      PantryIngredientPicker(
        repository: widget.catalogRepository!,
        onSelected: _selectIngredient,
      ),
    if (_mappingLoading) const LinearProgressIndicator(),
    if (_mappingError != null) ...[
      Text(_mappingError!),
      TextButton(
        key: const ValueKey('pantry-mappings-retry'),
        onPressed: _retryMappings,
        child: const Text(AppStrings.retry),
      ),
    ],
    if (_ingredientDetail != null) _foodField(),
    const SizedBox(height: 12),
    TextFormField(
      key: const ValueKey('pantry-form-quantity'),
      controller: _quantityController,
      enabled: !_busy,
      keyboardType: const TextInputType.numberWithOptions(decimal: true),
      decoration: const InputDecoration(
        labelText: AppStrings.pantryQuantity,
        border: OutlineInputBorder(),
      ),
      validator: (value) => PantryValidation.isPositiveQuantity(value)
          ? null
          : AppStrings.pantryQuantityInvalid,
    ),
    const SizedBox(height: 12),
    TextFormField(
      key: const ValueKey('pantry-form-unit'),
      controller: _unitController,
      enabled: !_busy,
      decoration: const InputDecoration(
        labelText: AppStrings.pantryUnit,
        border: OutlineInputBorder(),
      ),
      onChanged: (_) => _unitUserEdited = true,
      validator: (value) => PantryValidation.isValidUnitCode(value)
          ? null
          : AppStrings.pantryUnitRequired,
    ),
    const SizedBox(height: 8),
    Text(
      AppStrings.pantryCommonUnits,
      style: Theme.of(context).textTheme.bodySmall,
    ),
    Wrap(
      spacing: 8,
      children: [
        for (final code in _suggestedUnits())
          ActionChip(
            key: ValueKey('pantry-unit-$code'),
            label: Text(code),
            onPressed: _busy
                ? null
                : () {
                    _unitUserEdited = true;
                    setState(() => _unitController.text = code);
                  },
          ),
      ],
    ),
    const SizedBox(height: 12),
  ];

  List<String> _suggestedUnits() {
    final codes = <String>{'g', 'kg', 'ml', 'piece'};
    final detail = _ingredientDetail;
    if (detail?.defaultUnitCode != null) codes.add(detail!.defaultUnitCode!);
    for (final conversion
        in detail?.unitConversions ?? <IngredientUnitConversion>[]) {
      codes.add(conversion.fromUnitCode);
      codes.add(conversion.toUnitCode);
    }
    return codes.toList();
  }

  Widget _foodField() {
    final mappings = _ingredientDetail!.foodMappings;
    return DropdownButtonFormField<String?>(
      key: ValueKey('pantry-form-food-${_ingredient?.publicId}'),
      initialValue: _foodPublicId,
      decoration: const InputDecoration(
        labelText: AppStrings.pantryFood,
        border: OutlineInputBorder(),
      ),
      items: [
        const DropdownMenuItem<String?>(
          value: null,
          child: Text(AppStrings.pantryFoodNone),
        ),
        for (final mapping in mappings)
          DropdownMenuItem<String?>(
            value: mapping.foodPublicId,
            child: Text(mapping.foodDisplayName),
          ),
      ],
      onChanged: _busy
          ? null
          : (value) => setState(() => _foodPublicId = value),
    );
  }

  Widget _storageField() => DropdownButtonFormField<PantryStorageLocation>(
    key: const ValueKey('pantry-form-storage'),
    initialValue: _storage,
    decoration: InputDecoration(
      labelText: AppStrings.pantryStorage,
      errorText: _storageError,
      border: const OutlineInputBorder(),
    ),
    items: [
      for (final location in PantryStorageLocation.values)
        DropdownMenuItem(
          value: location,
          child: Text(PantryLocalizations.storage(location)),
        ),
    ],
    onChanged: _busy
        ? null
        : (value) => setState(() {
            _storage = value;
            _storageError = null;
          }),
  );

  Widget _dateField(
    String label,
    DateTime? date, {
    required bool expiry,
  }) => Card(
    child: Padding(
      padding: const EdgeInsets.all(8),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: 8,
        children: [
          Text(
            '$label: ${date == null ? AppStrings.pantryNotSet : PantryDate.format(date)}',
          ),
          TextButton.icon(
            key: ValueKey(
              expiry ? 'pantry-pick-expiry' : 'pantry-pick-acquired',
            ),
            onPressed: _busy ? null : () => _pickDate(expiry: expiry),
            icon: const Icon(Icons.calendar_today),
            label: const Text(AppStrings.pantryPickDate),
          ),
          if (date != null)
            TextButton(
              key: ValueKey(
                expiry ? 'pantry-clear-expiry' : 'pantry-clear-acquired',
              ),
              onPressed: _busy ? null : () => _clearDate(expiry: expiry),
              child: const Text(AppStrings.pantryClearDate),
            ),
        ],
      ),
    ),
  );

  Widget _expiryKindField() => DropdownButtonFormField<PantryExpiryKind>(
    key: const ValueKey('pantry-form-expiry-kind'),
    initialValue: _expiryKind,
    decoration: const InputDecoration(
      labelText: AppStrings.pantryExpiryKind,
      border: OutlineInputBorder(),
    ),
    items: [
      for (final kind in PantryExpiryKind.values)
        if (kind != PantryExpiryKind.unknown)
          DropdownMenuItem(
            value: kind,
            child: Text(PantryLocalizations.expiryKind(kind)),
          ),
    ],
    onChanged: _busy
        ? null
        : (value) => setState(() {
            _expiryKind = value ?? PantryExpiryKind.unknown;
            _expiryError = null;
          }),
  );

  Widget _expiryConfidenceField() =>
      DropdownButtonFormField<PantryExpiryConfidence>(
        key: const ValueKey('pantry-form-expiry-confidence'),
        initialValue: _expiryConfidence,
        decoration: const InputDecoration(
          labelText: AppStrings.pantryExpiryConfidence,
          border: OutlineInputBorder(),
        ),
        items: [
          for (final confidence in PantryExpiryConfidence.values)
            if (confidence != PantryExpiryConfidence.unknown)
              DropdownMenuItem(
                value: confidence,
                child: Text(PantryLocalizations.expiryConfidence(confidence)),
              ),
        ],
        onChanged: _busy
            ? null
            : (value) => setState(() {
                _expiryConfidence = value ?? PantryExpiryConfidence.unknown;
                _expiryError = null;
              }),
      );
}
