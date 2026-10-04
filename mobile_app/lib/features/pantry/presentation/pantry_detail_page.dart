import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_controller.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/presentation/pantry_localizations.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class PantryDetailPage extends StatefulWidget {
  const PantryDetailPage({
    super.key,
    required this.sessionController,
    required this.controller,
    required this.publicId,
  });

  final SessionController sessionController;
  final PantryController controller;
  final String publicId;

  @override
  State<PantryDetailPage> createState() => _PantryDetailPageState();
}

class _PantryDetailPageState extends State<PantryDetailPage> {
  PantryController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadDetail(widget.publicId);
    });
  }

  @override
  void didUpdateWidget(covariant PantryDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      _controller.addListener(_onChanged);
    }
    if (oldWidget.publicId != widget.publicId ||
        oldWidget.controller != widget.controller) {
      _controller.loadDetail(widget.publicId);
    }
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (!mounted) return;
    setState(() {});
    if (_controller.detailState.status == PantryLoadStatus.initial &&
        widget.sessionController.isAuthenticated) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && widget.sessionController.isAuthenticated) {
          _controller.loadDetail(widget.publicId);
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = _controller.detailState;
    return AuthenticatedShell(
      sessionController: widget.sessionController,
      selectedIndex: 6,
      content: SafeArea(
        child: ResponsiveContent(
          child: ListView(
            key: ValueKey('pantry-detail-${widget.publicId}'),
            children: [
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  key: const ValueKey('pantry-detail-back'),
                  onPressed: () => context.go('/pantry'),
                  icon: const Icon(Icons.arrow_back),
                  label: const Text(AppStrings.pantry),
                ),
              ),
              if (state.status == PantryLoadStatus.initial ||
                  state.status == PantryLoadStatus.loading)
                const Center(
                  child: CircularProgressIndicator(
                    key: ValueKey('pantry-detail-loading'),
                  ),
                )
              else if (state.status == PantryLoadStatus.error) ...[
                Center(
                  child: Text(
                    state.errorMessage ?? AppStrings.pantryDetailLoadFailed,
                  ),
                ),
                Center(
                  child: TextButton(
                    key: const ValueKey('pantry-detail-retry'),
                    onPressed: _controller.retryDetail,
                    child: const Text(AppStrings.retry),
                  ),
                ),
              ] else if (state.item != null)
                ..._details(context, state.item!),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _details(BuildContext context, PantryItem item) => [
    Text(
      item.ingredientName,
      key: const ValueKey('pantry-detail-name'),
      style: Theme.of(context).textTheme.headlineMedium,
    ),
    const SizedBox(height: 16),
    Align(
      alignment: Alignment.centerLeft,
      child: OutlinedButton.icon(
        key: const ValueKey('pantry-edit'),
        onPressed: () => context.go('/pantry/${item.publicId}/edit'),
        icon: const Icon(Icons.edit_outlined),
        label: const Text(AppStrings.pantryEditMetadata),
      ),
    ),
    const SizedBox(height: 12),
    _line(AppStrings.pantryFood, item.foodName),
    _line(
      AppStrings.pantryRemaining,
      '${item.quantityRemaining} ${PantryLocalizations.unit(item)}',
    ),
    _line(
      AppStrings.pantryInitial,
      '${item.quantityInitial} ${PantryLocalizations.unit(item)}',
    ),
    _line(
      AppStrings.pantryStorage,
      PantryLocalizations.storage(item.storageLocation),
    ),
    _line(
      AppStrings.pantryAcquiredOn,
      item.acquiredOn == null ? null : PantryDate.format(item.acquiredOn!),
    ),
    _line(
      AppStrings.pantryExpiryDate,
      item.expiryDate == null ? null : PantryDate.format(item.expiryDate!),
    ),
    _line(
      AppStrings.pantryExpiryKind,
      PantryLocalizations.expiryKind(item.expiryKind),
    ),
    _line(
      AppStrings.pantryExpiryConfidence,
      PantryLocalizations.expiryConfidence(item.expiryConfidence),
    ),
    _line(AppStrings.pantryStatus, PantryLocalizations.status(item.status)),
    _line(AppStrings.pantryNote, item.note),
    if (item.closedAt != null)
      _line(
        AppStrings.pantryClosedAt,
        PantryLocalizations.timestamp(item.closedAt!),
      ),
  ];

  Widget _line(String label, String? value) => Card(
    child: ListTile(
      title: Text(label),
      subtitle: Text(
        value == null || value.isEmpty ? AppStrings.pantryNotSet : value,
      ),
    ),
  );
}
