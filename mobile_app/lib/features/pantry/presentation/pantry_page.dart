import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
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
  void didUpdateWidget(covariant PantryPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      _controller.addListener(_onChanged);
      _controller.loadInitial();
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
    if (_controller.listState.status == PantryLoadStatus.initial &&
        widget.sessionController.isAuthenticated) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && widget.sessionController.isAuthenticated) {
          _controller.loadInitial();
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = _controller.listState;
    return AuthenticatedShell(
      sessionController: widget.sessionController,
      selectedIndex: 6,
      content: SafeArea(
        child: ResponsiveContent(
          child: ListView(
            key: const ValueKey('pantry-list'),
            children: [
              Text(
                AppStrings.pantry,
                style: Theme.of(context).textTheme.headlineMedium,
              ),
              const SizedBox(height: 8),
              Text(
                state.includeClosed
                    ? AppStrings.pantryHistorySubtitle
                    : AppStrings.pantrySubtitle,
              ),
              const SizedBox(height: 16),
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  key: const ValueKey('pantry-add'),
                  onPressed: () => context.go('/pantry/new'),
                  icon: const Icon(Icons.add),
                  label: const Text(AppStrings.pantryCreate),
                ),
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                key: const ValueKey('pantry-include-closed'),
                title: const Text(AppStrings.pantryIncludeClosed),
                value: state.includeClosed,
                onChanged: _controller.setIncludeClosed,
              ),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton.icon(
                  key: const ValueKey('pantry-refresh'),
                  onPressed: _controller.refreshList,
                  icon: const Icon(Icons.refresh),
                  label: const Text(AppStrings.reload),
                ),
              ),
              const SizedBox(height: 8),
              ..._body(state),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _body(PantryListState state) {
    if (state.status == PantryLoadStatus.initial ||
        state.status == PantryLoadStatus.loading) {
      return const [
        Center(
          child: CircularProgressIndicator(key: ValueKey('pantry-loading')),
        ),
      ];
    }
    if (state.status == PantryLoadStatus.error) {
      return [
        Center(child: Text(state.errorMessage ?? AppStrings.pantryLoadFailed)),
        Center(
          child: TextButton(
            key: const ValueKey('pantry-retry'),
            onPressed: _controller.retryList,
            child: const Text(AppStrings.retry),
          ),
        ),
      ];
    }
    if (state.items.isEmpty) {
      return [
        Center(
          child: Text(
            state.includeClosed
                ? AppStrings.pantryNoItems
                : AppStrings.pantryNoOpenItems,
          ),
        ),
      ];
    }
    return [for (final item in state.items) _itemTile(item)];
  }

  Widget _itemTile(PantryItem item) => Card(
    key: ValueKey('pantry-item-${item.publicId}'),
    child: ListTile(
      title: Text(item.ingredientName),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${AppStrings.pantryRemaining}: ${item.quantityRemaining} ${PantryLocalizations.unit(item)}',
          ),
          Text(PantryLocalizations.storage(item.storageLocation)),
          if (item.expiryDate != null)
            Text(
              '${AppStrings.pantryExpiryDate}: ${PantryDate.format(item.expiryDate!)}',
            ),
          Text(PantryLocalizations.status(item.status)),
        ],
      ),
      isThreeLine: true,
      trailing: const Icon(Icons.chevron_right),
      onTap: () => context.go('/pantry/${item.publicId}'),
    ),
  );
}
