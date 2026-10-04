import 'package:flutter/material.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_validation.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum PantryQuantityAction { adjust, consume }

class PantryQuantityActionDialog extends StatefulWidget {
  const PantryQuantityActionDialog({
    super.key,
    required this.controller,
    required this.item,
    required this.action,
  });

  final PantryController controller;
  final PantryItem item;
  final PantryQuantityAction action;

  @override
  State<PantryQuantityActionDialog> createState() =>
      _PantryQuantityActionDialogState();
}

class _PantryQuantityActionDialogState
    extends State<PantryQuantityActionDialog> {
  final _formKey = GlobalKey<FormState>();
  final _quantity = TextEditingController();
  final _note = TextEditingController();
  bool _submitting = false;
  String? _error;

  PantryItem get _currentItem {
    final detail = widget.controller.detailState;
    return detail.publicId == widget.item.publicId && detail.item != null
        ? detail.item!
        : widget.item;
  }

  @override
  void dispose() {
    _quantity.dispose();
    _note.dispose();
    super.dispose();
  }

  String? _validateQuantity(String? value) {
    final item = _currentItem;
    final valid = widget.action == PantryQuantityAction.adjust
        ? PantryValidation.canAdjust(
            initial: item.quantityInitial,
            remaining: item.quantityRemaining,
            quantityDelta: value ?? '',
            status: item.status,
          )
        : PantryValidation.canConsume(
            remaining: item.quantityRemaining,
            quantity: value ?? '',
            status: item.status,
          );
    if (valid) return null;
    return widget.action == PantryQuantityAction.adjust
        ? AppStrings.pantryAdjustmentInvalid
        : AppStrings.pantryConsumeInvalid;
  }

  Future<void> _submit() async {
    if (_submitting || !(_formKey.currentState?.validate() ?? false)) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    final item = widget.item;
    final result = widget.action == PantryQuantityAction.adjust
        ? await widget.controller.adjustItem(
            item.publicId,
            AdjustPantryItemRequest(
              quantityDelta: _quantity.text.trim(),
              note: _note.text,
            ),
          )
        : await widget.controller.consumeItem(
            item.publicId,
            ConsumePantryItemRequest(
              quantity: _quantity.text.trim(),
              note: _note.text,
            ),
          );
    if (!mounted) return;
    if (result != null) {
      Navigator.of(context).pop(result);
    } else {
      setState(() {
        _submitting = false;
        _error = widget.controller.mutationState.errorMessage;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final item = _currentItem;
    final adjust = widget.action == PantryQuantityAction.adjust;
    return AlertDialog(
      title: Text(adjust ? AppStrings.pantryAdjust : AppStrings.pantryConsume),
      content: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${AppStrings.pantryRemaining}: '
                '${item.quantityRemaining} ${PantryLocalizations.unit(item)}',
              ),
              if (adjust) ...[
                const SizedBox(height: 8),
                const Text(AppStrings.pantryAdjustmentHint),
              ],
              const SizedBox(height: 16),
              TextFormField(
                key: const ValueKey('pantry-action-quantity'),
                controller: _quantity,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                  signed: true,
                ),
                decoration: InputDecoration(
                  labelText: adjust
                      ? AppStrings.pantryAdjustmentDelta
                      : AppStrings.pantryQuantity,
                ),
                validator: _validateQuantity,
              ),
              const SizedBox(height: 12),
              TextFormField(
                key: const ValueKey('pantry-action-note'),
                controller: _note,
                maxLines: 2,
                decoration: const InputDecoration(
                  labelText: AppStrings.pantryActionNote,
                ),
                validator: (value) => PantryValidation.isValidNote(value)
                    ? null
                    : AppStrings.pantryNoteTooLong,
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Text(_error!, key: const ValueKey('pantry-action-error')),
              ],
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: _submitting ? null : () => Navigator.of(context).pop(),
          child: const Text(AppStrings.cancel),
        ),
        FilledButton(
          key: const ValueKey('pantry-action-submit'),
          onPressed: _submitting ? null : _submit,
          child: Text(
            adjust ? AppStrings.pantryAdjust : AppStrings.pantryConsume,
          ),
        ),
      ],
    );
  }
}

class PantryDiscardDialog extends StatefulWidget {
  const PantryDiscardDialog({
    super.key,
    required this.controller,
    required this.item,
  });

  final PantryController controller;
  final PantryItem item;

  @override
  State<PantryDiscardDialog> createState() => _PantryDiscardDialogState();
}

class _PantryDiscardDialogState extends State<PantryDiscardDialog> {
  final _formKey = GlobalKey<FormState>();
  final _note = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _note.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_submitting || !(_formKey.currentState?.validate() ?? false)) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    final result = await widget.controller.discardItem(
      widget.item.publicId,
      DiscardPantryItemRequest(note: PantryNote.normalize(_note.text)),
    );
    if (!mounted) return;
    if (result != null) {
      Navigator.of(context).pop(result);
    } else {
      setState(() {
        _submitting = false;
        _error = widget.controller.mutationState.errorMessage;
      });
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text(AppStrings.pantryDiscard),
    content: SingleChildScrollView(
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(AppStrings.pantryDiscardWarning),
            const SizedBox(height: 8),
            Text(
              '${AppStrings.pantryRemaining}: '
              '${widget.item.quantityRemaining} '
              '${PantryLocalizations.unit(widget.item)}',
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('pantry-discard-note'),
              controller: _note,
              maxLines: 2,
              decoration: const InputDecoration(
                labelText: AppStrings.pantryActionNote,
              ),
              validator: (value) => PantryValidation.isValidNote(value)
                  ? null
                  : AppStrings.pantryNoteTooLong,
            ),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!, key: const ValueKey('pantry-discard-error')),
            ],
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: _submitting ? null : () => Navigator.of(context).pop(),
        child: const Text(AppStrings.cancel),
      ),
      FilledButton(
        key: const ValueKey('pantry-discard-confirm'),
        onPressed: _submitting ? null : _submit,
        child: const Text(AppStrings.pantryConfirmDiscard),
      ),
    ],
  );
}
