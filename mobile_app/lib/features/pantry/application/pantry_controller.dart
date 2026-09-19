import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_repository.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum PantryListStatus { initial, loading, loaded, error }

final class PantryListState {
  const PantryListState({
    required this.status,
    required this.items,
    required this.errorMessage,
  });

  factory PantryListState.initial() => const PantryListState(
    status: PantryListStatus.initial,
    items: <PantryItem>[],
    errorMessage: null,
  );

  final PantryListStatus status;
  final List<PantryItem> items;
  final String? errorMessage;

  bool get isInitialLoading =>
      status == PantryListStatus.loading && items.isEmpty;
}

final class PantryController extends ChangeNotifier {
  PantryController({
    required this.repository,
    required this.catalogRepository,
  }) : _state = PantryListState.initial();

  final PantryRepository repository;
  final CatalogRepository catalogRepository;

  PantryListState _state;
  bool _disposed = false;
  bool _actionInProgress = false;
  String? _actionErrorMessage;
  int _generation = 0;

  PantryListState get state => _state;
  bool get actionInProgress => _actionInProgress;
  String? get actionErrorMessage => _actionErrorMessage;

  Future<void> loadInitial() {
    if (_disposed || _state.status == PantryListStatus.loading) {
      return Future<void>.value();
    }
    return _load();
  }

  Future<void> reload() => _load();

  Future<IngredientCatalogPage> searchIngredients(String query) =>
      catalogRepository.getIngredients(query: query.trim(), page: 0, size: 20);

  Future<PantryItem?> create(PantryCreateRequest request) => _mutate(
    () => repository.createPantryItem(request),
  );

  Future<PantryItem?> updateMetadata(
    String publicId,
    PantryMetadataUpdate request,
  ) => _mutate(() => repository.updateMetadata(publicId, request));

  Future<PantryItem?> adjust(
    String publicId,
    PantryQuantityAdjustment request,
  ) => _mutate(() => repository.adjust(publicId, request));

  Future<PantryItem?> consume(
    String publicId,
    PantryQuantityConsumption request,
  ) => _mutate(() => repository.consume(publicId, request));

  Future<PantryItem?> discard(String publicId, {String? note}) => _mutate(
    () => repository.discard(publicId, note: note),
  );

  void resetForSessionChange() {
    if (_disposed) return;
    _generation++;
    _state = PantryListState.initial();
    _actionInProgress = false;
    _actionErrorMessage = null;
    _notify();
  }

  Future<void> _load() async {
    if (_disposed) return;
    final generation = ++_generation;
    _state = PantryListState(
      status: PantryListStatus.loading,
      items: _state.items,
      errorMessage: null,
    );
    _notify();
    try {
      final items = await repository.getPantry();
      if (!_isCurrent(generation)) return;
      _state = PantryListState(
        status: PantryListStatus.loaded,
        items: items,
        errorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = PantryListState(
        status: PantryListStatus.error,
        items: _state.items,
        errorMessage: _pantryErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  Future<PantryItem?> _mutate(Future<PantryItem> Function() operation) async {
    if (_disposed || _actionInProgress) return null;
    final generation = _generation;
    _actionInProgress = true;
    _actionErrorMessage = null;
    _notify();
    try {
      final result = await operation();
      if (!_isCurrent(generation)) return null;
      await _load();
      return result;
    } on Object catch (error) {
      if (!_disposed) {
        _actionErrorMessage = _pantryErrorMessage(error);
        _notify();
      }
      return null;
    } finally {
      if (!_disposed) {
        _actionInProgress = false;
        _notify();
      }
    }
  }

  bool _isCurrent(int generation) =>
      !_disposed && generation == _generation;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}

String _pantryErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return AppStrings.unableToReachService;
  }
  if (error is ApiHttpException && error.statusCode >= 500) {
    return AppStrings.serviceUnavailable;
  }
  return AppStrings.pantryRequestFailed;
}
