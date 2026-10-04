import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_error_messages.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';

enum PantryLoadStatus { initial, loading, loaded, error }

final class PantryListState {
  const PantryListState({
    this.status = PantryLoadStatus.initial,
    this.includeClosed = false,
    this.items = const [],
    this.errorMessage,
  });

  final PantryLoadStatus status;
  final bool includeClosed;
  final List<PantryItem> items;
  final String? errorMessage;
}

final class PantryDetailState {
  const PantryDetailState({
    this.status = PantryLoadStatus.initial,
    this.publicId,
    this.item,
    this.errorMessage,
    this.notFound = false,
  });

  final PantryLoadStatus status;
  final String? publicId;
  final PantryItem? item;
  final String? errorMessage;
  final bool notFound;
}

/// Holds only the current session's Pantry reads. Reset invalidates pending work.
final class PantryController extends ChangeNotifier {
  PantryController({required this.repository});

  final PantryRepository repository;
  PantryListState _listState = const PantryListState();
  PantryDetailState _detailState = const PantryDetailState();
  int _listGeneration = 0;
  int _detailGeneration = 0;
  bool _disposed = false;

  PantryListState get listState => _listState;
  PantryDetailState get detailState => _detailState;

  Future<void> loadInitial() {
    if (_disposed || _listState.status != PantryLoadStatus.initial) {
      return Future<void>.value();
    }
    return _loadList(_listState.includeClosed);
  }

  Future<void> setIncludeClosed(bool includeClosed) {
    if (_disposed || _listState.includeClosed == includeClosed) {
      return Future<void>.value();
    }
    return _loadList(includeClosed);
  }

  Future<void> refreshList() => _loadList(_listState.includeClosed);

  Future<void> retryList() => refreshList();

  Future<void> _loadList(bool includeClosed) async {
    if (_disposed) return;
    final generation = ++_listGeneration;
    _listState = PantryListState(
      status: PantryLoadStatus.loading,
      includeClosed: includeClosed,
    );
    notifyListeners();
    try {
      final items = await repository.list(includeClosed: includeClosed);
      if (!_isListCurrent(generation)) return;
      _listState = PantryListState(
        status: PantryLoadStatus.loaded,
        includeClosed: includeClosed,
        items: List.unmodifiable(items),
      );
    } on Object catch (error) {
      if (!_isListCurrent(generation)) return;
      _listState = PantryListState(
        status: PantryLoadStatus.error,
        includeClosed: includeClosed,
        errorMessage: pantryReadErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<void> loadDetail(String publicId) async {
    if (_disposed) return;
    final generation = ++_detailGeneration;
    _detailState = PantryDetailState(
      status: PantryLoadStatus.loading,
      publicId: publicId,
    );
    notifyListeners();
    try {
      final item = await repository.getByPublicId(publicId);
      if (!_isDetailCurrent(generation)) return;
      _detailState = PantryDetailState(
        status: PantryLoadStatus.loaded,
        publicId: publicId,
        item: item,
      );
    } on Object catch (error) {
      if (!_isDetailCurrent(generation)) return;
      _detailState = PantryDetailState(
        status: PantryLoadStatus.error,
        publicId: publicId,
        errorMessage: pantryReadErrorMessage(error, detail: true),
        notFound: error is ApiHttpException && error.statusCode == 404,
      );
    }
    notifyListeners();
  }

  Future<void> retryDetail() {
    final publicId = _detailState.publicId;
    return publicId == null ? Future<void>.value() : loadDetail(publicId);
  }

  void resetForSessionChange() {
    if (_disposed) return;
    _listGeneration++;
    _detailGeneration++;
    _listState = const PantryListState();
    _detailState = const PantryDetailState();
    notifyListeners();
  }

  bool _isListCurrent(int generation) =>
      !_disposed && generation == _listGeneration;

  bool _isDetailCurrent(int generation) =>
      !_disposed && generation == _detailGeneration;

  @override
  void dispose() {
    _disposed = true;
    _listGeneration++;
    _detailGeneration++;
    super.dispose();
  }
}
