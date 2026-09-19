import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class PantryPage extends StatefulWidget {
  const PantryPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final PantryController controller;

  @override
  State<PantryPage> createState() => _PantryPageState();
}

class _PantryPageState extends State<PantryPage> {
  PantryController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadInitial();
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
    selectedIndex: 3,
    content: _content(context),
  );

  Widget _content(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('pantry-list'),
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    AppStrings.pantry,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                ),
                IconButton(
                  key: const ValueKey('pantry-refresh'),
                  tooltip: AppStrings.pantryRefresh,
                  onPressed: _controller.actionInProgress
                      ? null
                      : _controller.reload,
                  icon: const Icon(Icons.refresh),
                ),
              ],
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.pantrySubtitle),
            const SizedBox(height: 16),
            Align(
              alignment: Alignment.centerLeft,
              child: FilledButton.icon(
                key: const ValueKey('pantry-add-item'),
                onPressed: _controller.actionInProgress
                    ? null
                    : () => _openAddDialog(context),
                icon: const Icon(Icons.add),
                label: const Text(AppStrings.addPantryItem),
              ),
            ),
            if (_controller.actionErrorMessage != null) ...[
              const SizedBox(height: 12),
              Text(
                _controller.actionErrorMessage!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            const SizedBox(height: 20),
            ..._body(context, state),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, PantryListState state) {
    if (state.isInitialLoading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == PantryListStatus.error && state.items.isEmpty) {
      return [
        _ErrorPanel(
          message: state.errorMessage ?? AppStrings.pantryLoadFailed,
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: Center(child: Text(AppStrings.pantryEmpty)),
        ),
      ];
    }
    return [
      for (final item in state.items) _PantryItemCard(item: item, controller: _controller),
      if (state.status == PantryListStatus.loading)
        const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
    ];
  }

  Future<void> _openAddDialog(BuildContext context) async {
    final request = await showDialog<PantryCreateRequest>(
      context: context,
      builder: (context) => _AddPantryDialog(controller: _controller),
    );
    if (!mounted || request == null) return;
    await _controller.create(request);
  }
}

final class _PantryItemCard extends StatelessWidget {
  const _PantryItemCard({required this.item, required this.controller});

  final PantryItem item;
  final PantryController controller;

  @override
  Widget build(BuildContext context) => Card(
    key: ValueKey('pantry-item-${item.publicId}'),
    margin: const EdgeInsets.only(bottom: 12),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  item.ingredientName,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              PopupMenuButton<String>(
                key: ValueKey('pantry-actions-${item.publicId}'),
                enabled: !controller.actionInProgress && _isOpen,
                onSelected: (value) => _action(context, value),
                itemBuilder: (context) => const [
                  PopupMenuItem(value: 'adjust', child: Text(AppStrings.pantryAdjust)),
                  PopupMenuItem(value: 'consume', child: Text(AppStrings.pantryConsume)),
                  PopupMenuItem(value: 'discard', child: Text(AppStrings.pantryDiscard)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${_format(item.quantityRemaining)} ${item.unitDisplayName} · '
            '${PantryLocalizations.storageName(item.storageLocation)}',
          ),
          const SizedBox(height: 4),
          Text(PantryLocalizations.statusName(item.status)),
          if (item.expiryDate != null) ...[
            const SizedBox(height: 4),
            Text('${AppStrings.pantryExpiryDate}: ${item.expiryDate}'),
          ],
          if (item.note != null && item.note!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(item.note!),
          ],
        ],
      ),
    ),
  );

  bool get _isOpen => item.status == 'AVAILABLE' || item.status == 'RESERVED';

  Future<void> _action(BuildContext context, String action) async {
    if (action == 'adjust') {
      final delta = await _numberDialog(context, AppStrings.pantryAdjust);
      if (delta != null) {
        await controller.adjust(
          item.publicId,
          PantryQuantityAdjustment(quantityDelta: delta),
        );
      }
    } else if (action == 'consume') {
      final quantity = await _numberDialog(context, AppStrings.pantryConsume);
      if (quantity != null && quantity > 0) {
        await controller.consume(
          item.publicId,
          PantryQuantityConsumption(quantity: quantity),
        );
      }
    } else if (action == 'discard') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text(AppStrings.pantryDiscard),
          content: Text(item.ingredientName),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text(AppStrings.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text(AppStrings.pantryDiscard),
            ),
          ],
        ),
      );
      if (confirmed == true) await controller.discard(item.publicId);
    }
  }

  Future<double?> _numberDialog(BuildContext context, String title) async {
    final textController = TextEditingController();
    final value = await showDialog<double>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: TextField(
          controller: textController,
          autofocus: true,
          keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
          decoration: const InputDecoration(labelText: AppStrings.pantryQuantity),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          FilledButton(
            onPressed: () {
              final parsed = double.tryParse(textController.text.trim());
              if (parsed != null && parsed.isFinite && parsed != 0) {
                Navigator.pop(context, parsed);
              }
            },
            child: const Text(AppStrings.save),
          ),
        ],
      ),
    );
    textController.dispose();
    return value;
  }

  String _format(double value) =>
      value == value.roundToDouble() ? value.toInt().toString() : value.toString();
}

final class _AddPantryDialog extends StatefulWidget {
  const _AddPantryDialog({required this.controller});

  final PantryController controller;

  @override
  State<_AddPantryDialog> createState() => _AddPantryDialogState();
}

final class _AddPantryDialogState extends State<_AddPantryDialog> {
  final _searchController = TextEditingController();
  final _quantityController = TextEditingController(text: '100');
  final _unitController = TextEditingController(text: 'g');
  final _expiryController = TextEditingController();
  IngredientCatalogItem? _selected;
  List<IngredientCatalogItem> _ingredients = const [];
  String _storage = 'FRIDGE';
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _search(''));
  }

  @override
  void dispose() {
    _searchController.dispose();
    _quantityController.dispose();
    _unitController.dispose();
    _expiryController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    key: const ValueKey('pantry-add-dialog'),
    title: const Text(AppStrings.addPantryItem),
    content: SizedBox(
      width: 520,
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              key: const ValueKey('pantry-ingredient-search'),
              controller: _searchController,
              textInputAction: TextInputAction.search,
              onSubmitted: _search,
              decoration: InputDecoration(
                labelText: AppStrings.pantrySearchIngredient,
                suffixIcon: IconButton(
                  onPressed: () => _search(_searchController.text),
                  icon: const Icon(Icons.search),
                ),
              ),
            ),
            if (_loading)
              const Padding(
                padding: EdgeInsets.all(12),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_error != null)
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error))
            else if (_ingredients.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(AppStrings.pantryNoIngredients),
              )
            else
              RadioGroup<String>(
                groupValue: _selected?.publicId,
                onChanged: _selectIngredient,
                child: Column(
                  children: [
                    for (final ingredient in _ingredients)
                      RadioListTile<String>(
                        value: ingredient.publicId,
                        title: Text(ingredient.displayName),
                        subtitle: ingredient.category == null
                            ? null
                            : Text(ingredient.category!.displayName),
                      ),
                  ],
                ),
              ),
            const SizedBox(height: 8),
            TextField(
              controller: _quantityController,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(labelText: AppStrings.pantryQuantity),
            ),
            TextField(
              controller: _unitController,
              decoration: const InputDecoration(labelText: AppStrings.pantryUnit),
            ),
            DropdownButtonFormField<String>(
              initialValue: _storage,
              decoration: const InputDecoration(labelText: AppStrings.pantryStorage),
              items: const [
                DropdownMenuItem(value: 'PANTRY', child: Text('Tủ bếp')),
                DropdownMenuItem(value: 'FRIDGE', child: Text('Tủ lạnh')),
                DropdownMenuItem(value: 'FREEZER', child: Text('Ngăn đông')),
                DropdownMenuItem(value: 'OTHER', child: Text('Khác')),
              ],
              onChanged: (value) => setState(() => _storage = value ?? 'FRIDGE'),
            ),
            TextField(
              controller: _expiryController,
              decoration: const InputDecoration(
                labelText: AppStrings.pantryExpiryDate,
                hintText: AppStrings.pantryDateFormat,
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 8),
              Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text(AppStrings.cancel),
      ),
      FilledButton(
        key: const ValueKey('pantry-save-item'),
        onPressed: _submit,
        child: const Text(AppStrings.pantrySave),
      ),
    ],
  );

  Future<void> _search(String query) async {
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await widget.controller.searchIngredients(query);
      if (!mounted) return;
      setState(() => _ingredients = page.content);
    } on Object {
      if (mounted) setState(() => _error = AppStrings.pantryRequestFailed);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _submit() {
    final selected = _selected;
    final quantity = double.tryParse(_quantityController.text.trim());
    final unit = _unitController.text.trim();
    final expiry = _expiryController.text.trim();
    if (selected == null ||
        quantity == null ||
        !quantity.isFinite ||
        quantity <= 0 ||
        unit.isEmpty) {
      setState(() => _error = AppStrings.pantryInvalidQuantity);
      return;
    }
    Navigator.pop(
      context,
      PantryCreateRequest(
        ingredientPublicId: selected.publicId,
        foodPublicId: null,
        quantity: quantity,
        unitCode: unit,
        storageLocation: _storage,
        acquiredOn: null,
        expiryDate: expiry.isEmpty ? null : expiry,
        expiryKind: expiry.isEmpty ? 'UNKNOWN' : 'USE_BY',
        expiryConfidence: expiry.isEmpty ? 'UNKNOWN' : 'LABELLED',
        note: null,
      ),
    );
  }

  void _selectIngredient(String? publicId) {
    if (publicId == null) return;
    for (final ingredient in _ingredients) {
      if (ingredient.publicId == publicId) {
        setState(() => _selected = ingredient);
        return;
      }
    }
  }
}

final class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('pantry-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}
