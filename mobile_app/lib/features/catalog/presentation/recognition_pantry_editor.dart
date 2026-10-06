import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_validation.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_ingredient_picker.dart';

/// Human-reviewed handoff from YOLO labels to real catalog-backed pantry lots.
class RecognitionPantryEditor extends StatefulWidget {
  const RecognitionPantryEditor({
    super.key,
    required this.result,
    required this.catalogRepository,
    required this.pantryController,
  });

  final IngredientRecognitionResult result;
  final CatalogRepository catalogRepository;
  final PantryController pantryController;

  @override
  State<RecognitionPantryEditor> createState() =>
      _RecognitionPantryEditorState();
}

class _RecognitionDraft {
  _RecognitionDraft(this.detection)
    : quantity = TextEditingController(text: '100'),
      unit = TextEditingController(text: 'g');

  final IngredientRecognitionDetection? detection;
  final TextEditingController quantity;
  final TextEditingController unit;
  IngredientCatalogItem? ingredient;
  bool confirmed = false;
  bool searching = false;
  bool unitEdited = false;
  List<String> suggestedUnits = const ['g', 'kg', 'ml', 'piece'];

  void dispose() {
    quantity.dispose();
    unit.dispose();
  }
}

class _RecognitionPantryEditorState extends State<RecognitionPantryEditor> {
  late final List<_RecognitionDraft> _drafts;
  int _revision = 0;
  bool _saving = false;
  String? _error;
  String? _success;

  @override
  void initState() {
    super.initState();
    _drafts = widget.result.detections.map(_RecognitionDraft.new).toList();
    for (final draft in _drafts) {
      _resolve(draft);
    }
  }

  @override
  void dispose() {
    _revision++;
    for (final draft in _drafts) {
      draft.dispose();
    }
    super.dispose();
  }

  Future<void> _resolve(_RecognitionDraft draft) async {
    final detection = draft.detection;
    if (detection == null) return;
    final revision = _revision;
    try {
      final page = await widget.catalogRepository.getIngredients(
        query: detection.nameVi,
        size: 100,
      );
      if (!mounted || revision != _revision || !_drafts.contains(draft)) return;
      final matches = page.content.where(
        (item) =>
            item.displayName.trim().toLowerCase() ==
            detection.nameVi.trim().toLowerCase(),
      );
      if (matches.length == 1 && draft.ingredient == null) {
        await _select(draft, matches.single);
      }
    } on Object {
      // Catalog search remains available when automatic resolution fails.
    }
  }

  Future<void> _select(
    _RecognitionDraft draft,
    IngredientCatalogItem item,
  ) async {
    setState(() {
      draft.ingredient = item;
      draft.searching = false;
      _error = null;
    });
    try {
      final detail = await widget.catalogRepository.getIngredient(
        item.publicId,
      );
      if (!mounted || !_drafts.contains(draft) || draft.ingredient != item) {
        return;
      }
      if (detail.defaultUnitCode != null && !draft.unitEdited) {
        setState(() => draft.unit.text = detail.defaultUnitCode!);
      }
      setState(() {
        draft.suggestedUnits = {
          'g',
          'kg',
          'ml',
          'piece',
          if (detail.defaultUnitCode != null) detail.defaultUnitCode!,
          for (final conversion in detail.unitConversions) ...[
            conversion.fromUnitCode,
            conversion.toUnitCode,
          ],
        }.toList();
      });
    } on Object {
      // The user can still choose a valid unit manually.
    }
  }

  void _remove(_RecognitionDraft draft) {
    setState(() => _drafts.remove(draft));
    draft.dispose();
  }

  Future<void> _save() async {
    if (_saving) return;
    final selected = _drafts.where((draft) => draft.confirmed).toList();
    if (selected.isEmpty) {
      setState(() => _error = 'Hãy xác nhận ít nhất một nguyên liệu.');
      return;
    }
    for (final draft in selected) {
      if (draft.ingredient == null ||
          !PantryValidation.isPositiveQuantity(draft.quantity.text) ||
          !PantryValidation.isValidUnitCode(draft.unit.text)) {
        setState(
          () => _error = 'Chọn nguyên liệu trong danh mục và nhập số lượng, đơn vị hợp lệ.',
        );
        return;
      }
    }
    setState(() {
      _saving = true;
      _error = null;
      _success = null;
    });
    var created = 0;
    for (final draft in selected) {
      final item = await widget.pantryController.createItem(
        CreatePantryItemRequest(
          ingredientPublicId: draft.ingredient!.publicId,
          quantity: draft.quantity.text.trim(),
          unitCode: draft.unit.text.trim(),
          storageLocation: PantryStorageLocation.fridge,
        ),
      );
      if (!mounted) return;
      if (item == null) {
        setState(
          () => _error =
              widget.pantryController.mutationState.errorMessage ??
              'Không thể thêm nguyên liệu vào kho.',
        );
        break;
      }
      created++;
      _remove(draft);
    }
    if (created > 0) {
      await widget.pantryController.refreshList();
    }
    if (!mounted) return;
    setState(() {
      _saving = false;
      if (created > 0) _success = 'Đã thêm $created nguyên liệu vào kho.';
      if (widget.pantryController.listState.status == PantryLoadStatus.error) {
        _error = 'Đã lưu nguyên liệu nhưng chưa tải lại được kho. Mở kho và thử tải lại.';
      }
    });
  }

  @override
  Widget build(BuildContext context) => Card(
    key: const ValueKey('recognition-pantry-editor'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Xác nhận nguyên liệu',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const Text('AI chỉ gợi ý. Kiểm tra từng món trước khi thêm vào kho.'),
          if (_drafts.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'Không có nhận diện đáng tin cậy. Hãy tìm nguyên liệu trong danh mục.',
              ),
            ),
          for (final draft in _drafts) _buildDraft(draft),
          TextButton.icon(
            key: const ValueKey('recognition-add-manual'),
            onPressed: _saving
                ? null
                : () => setState(() => _drafts.add(_RecognitionDraft(null))),
            icon: const Icon(Icons.add),
            label: const Text('Tìm nguyên liệu thủ công'),
          ),
          if (_error != null)
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          if (_success != null) Text(_success!),
          FilledButton(
            key: const ValueKey('recognition-add-to-pantry'),
            onPressed: _saving ? null : _save,
            child: Text(_saving ? 'Đang thêm...' : 'Thêm vào kho'),
          ),
          if (_success != null)
            TextButton(
              key: const ValueKey('recognition-open-pantry'),
              onPressed: () => context.go('/pantry'),
              child: const Text('Xem kho của tôi'),
            ),
        ],
      ),
    ),
  );

  Widget _buildDraft(_RecognitionDraft draft) {
    final detection = draft.detection;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (detection != null)
              Text(
                '${detection.nameVi} (${detection.code}) · '
                '${(detection.confidence * 100).toStringAsFixed(1)}%',
              ),
            Row(
              children: [
                Expanded(
                  child: Text(
                    draft.ingredient?.displayName ??
                        'Chưa khớp danh mục — hãy chọn nguyên liệu',
                  ),
                ),
                IconButton(
                  tooltip: 'Bỏ nhận diện',
                  onPressed: _saving ? null : () => _remove(draft),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            OutlinedButton(
              onPressed: _saving
                  ? null
                  : () => setState(() => draft.searching = !draft.searching),
              child: Text(
                draft.ingredient == null
                    ? 'Tìm trong danh mục'
                    : 'Sửa nguyên liệu',
              ),
            ),
            if (draft.searching)
              PantryIngredientPicker(
                repository: widget.catalogRepository,
                onSelected: (item) => _select(draft, item),
              ),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: draft.quantity,
                    enabled: !_saving,
                    keyboardType: const TextInputType.numberWithOptions(
                      decimal: true,
                    ),
                    decoration: const InputDecoration(labelText: 'Số lượng'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: draft.unit,
                    enabled: !_saving,
                    onChanged: (_) => draft.unitEdited = true,
                    decoration: const InputDecoration(
                      labelText: 'Đơn vị (g, kg, piece...)',
                    ),
                  ),
                ),
              ],
            ),
            Wrap(
              spacing: 8,
              children: [
                for (final code in draft.suggestedUnits)
                  ActionChip(
                    label: Text(code),
                    onPressed: _saving
                        ? null
                        : () => setState(() {
                            draft.unitEdited = true;
                            draft.unit.text = code;
                          }),
                  ),
              ],
            ),
            CheckboxListTile(
              value: draft.confirmed,
              onChanged: _saving
                  ? null
                  : (value) => setState(() => draft.confirmed = value ?? false),
              title: const Text('Tôi xác nhận nguyên liệu này'),
              controlAffinity: ListTileControlAffinity.leading,
            ),
          ],
        ),
      ),
    );
  }
}
