import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum _PickerStatus { loading, loaded, error }

/// Searches the existing ingredient catalog without owning the form draft.
class PantryIngredientPicker extends StatefulWidget {
  const PantryIngredientPicker({
    super.key,
    required this.repository,
    required this.onSelected,
  });

  final CatalogRepository repository;
  final ValueChanged<IngredientCatalogItem> onSelected;

  @override
  State<PantryIngredientPicker> createState() => _PantryIngredientPickerState();
}

class _PantryIngredientPickerState extends State<PantryIngredientPicker> {
  final _queryController = TextEditingController();
  _PickerStatus _status = _PickerStatus.loading;
  List<IngredientCatalogItem> _items = const [];
  String _query = '';
  int _page = 0;
  int _totalPages = 0;
  int _generation = 0;
  bool _loadingMore = false;
  String? _errorMessage;
  String? _loadMoreError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _search('');
    });
  }

  @override
  void dispose() {
    _generation++;
    _queryController.dispose();
    super.dispose();
  }

  Future<void> _search(String query) async {
    final generation = ++_generation;
    final normalized = query.trim();
    setState(() {
      _query = normalized;
      _status = _PickerStatus.loading;
      _items = const [];
      _page = 0;
      _totalPages = 0;
      _errorMessage = null;
      _loadMoreError = null;
      _loadingMore = false;
    });
    try {
      final result = await widget.repository.getIngredients(
        query: normalized,
        page: 0,
      );
      if (!_current(generation)) return;
      setState(() {
        _items = result.content;
        _page = result.page;
        _totalPages = result.totalPages;
        _status = _PickerStatus.loaded;
      });
    } on Object catch (error) {
      if (!_current(generation)) return;
      setState(() {
        _status = _PickerStatus.error;
        _errorMessage = _safeError(error);
      });
    }
  }

  Future<void> _loadMore() async {
    if (_loadingMore ||
        _status != _PickerStatus.loaded ||
        _page + 1 >= _totalPages) {
      return;
    }
    final generation = _generation;
    final nextPage = _page + 1;
    setState(() {
      _loadingMore = true;
      _loadMoreError = null;
    });
    try {
      final result = await widget.repository.getIngredients(
        query: _query,
        page: nextPage,
      );
      if (!_current(generation)) return;
      final ids = {for (final item in _items) item.publicId};
      setState(() {
        _items = [
          ..._items,
          for (final item in result.content)
            if (ids.add(item.publicId)) item,
        ];
        _page = result.page;
        _totalPages = result.totalPages;
        _loadingMore = false;
      });
    } on Object catch (error) {
      if (!_current(generation)) return;
      setState(() {
        _loadingMore = false;
        _loadMoreError = _safeError(error);
      });
    }
  }

  bool _current(int generation) => mounted && generation == _generation;

  String _safeError(Object error) {
    if (error is ApiTransportException) return AppStrings.unableToReachService;
    if (error is ApiHttpException && error.statusCode >= 500) {
      return AppStrings.serviceUnavailable;
    }
    return AppStrings.pantryIngredientSearchFailed;
  }

  @override
  Widget build(BuildContext context) => Card(
    key: const ValueKey('pantry-ingredient-picker'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            AppStrings.pantrySelectIngredient,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          TextField(
            key: const ValueKey('pantry-ingredient-search'),
            controller: _queryController,
            textInputAction: TextInputAction.search,
            decoration: const InputDecoration(
              labelText: AppStrings.pantryIngredientSearch,
              border: OutlineInputBorder(),
            ),
            onSubmitted: _search,
          ),
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              key: const ValueKey('pantry-ingredient-search-submit'),
              onPressed: () => _search(_queryController.text),
              icon: const Icon(Icons.search),
              label: const Text(AppStrings.catalogSearchSubmit),
            ),
          ),
          if (_status == _PickerStatus.loading)
            const Center(
              child: CircularProgressIndicator(
                key: ValueKey('pantry-picker-loading'),
              ),
            )
          else if (_status == _PickerStatus.error) ...[
            Text(_errorMessage ?? AppStrings.pantryIngredientSearchFailed),
            TextButton(
              key: const ValueKey('pantry-picker-retry'),
              onPressed: () => _search(_query),
              child: const Text(AppStrings.retry),
            ),
          ] else if (_items.isEmpty)
            const Text(AppStrings.pantryIngredientNoResults)
          else ...[
            for (final item in _items)
              ListTile(
                key: ValueKey('pantry-pick-${item.publicId}'),
                title: Text(item.displayName),
                onTap: () => widget.onSelected(item),
              ),
            if (_loadMoreError != null) Text(_loadMoreError!),
            if (_page + 1 < _totalPages)
              TextButton(
                key: const ValueKey('pantry-picker-more'),
                onPressed: _loadingMore ? null : _loadMore,
                child: Text(
                  _loadingMore ? AppStrings.loading : AppStrings.loadMore,
                ),
              ),
          ],
        ],
      ),
    ),
  );
}
