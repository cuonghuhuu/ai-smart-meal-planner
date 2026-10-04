import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_error_messages.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';

enum PantryLoadStatus { initial, loading, loaded, error }

enum PantryMutationStatus { idle, submitting, succeeded, error }

final class PantryMutationState {
  const PantryMutationState({
    this.status = PantryMutationStatus.idle,
    this.errorMessage,
    this.item,
  });

  final PantryMutationStatus status;
  final String? errorMessage;
  final PantryItem? item;
}

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
  PantryMutationState _mutationState = const PantryMutationState();
  int _listGeneration = 0;
  int _detailGeneration = 0;
  int _mutationGeneration = 0;
  bool _hasListSnapshot = false;
  String? _mutationTargetPublicId;
  bool _disposed = false;

  PantryListState get listState => _listState;
  PantryDetailState get detailState => _detailState;
  PantryMutationState get mutationState => _mutationState;

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
    final preserveSnapshot =
        _hasListSnapshot && _listState.includeClosed == includeClosed;
    if (!preserveSnapshot) _hasListSnapshot = false;
    final generation = ++_listGeneration;
    _listState = PantryListState(
      status: PantryLoadStatus.loading,
      includeClosed: includeClosed,
      items: preserveSnapshot ? _listState.items : const [],
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
      _hasListSnapshot = true;
    } on Object catch (error) {
      if (!_isListCurrent(generation)) return;
      _listState = PantryListState(
        status: PantryLoadStatus.error,
        includeClosed: includeClosed,
        items: _hasListSnapshot ? _listState.items : const [],
        errorMessage: pantryReadErrorMessage(error),
      );
    }
    notifyListeners();
  }

  Future<void> loadDetail(String publicId) async {
    if (_disposed) return;
    if (_mutationTargetPublicId != null &&
        _mutationTargetPublicId != publicId) {
      _mutationGeneration++;
      _mutationTargetPublicId = null;
      _mutationState = const PantryMutationState();
    }
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

  void clearMutationFeedback() {
    if (_disposed || _mutationState.status == PantryMutationStatus.submitting) {
      return;
    }
    _mutationState = const PantryMutationState();
    notifyListeners();
  }

  Future<PantryItem?> createItem(CreatePantryItemRequest request) async {
    if (_disposed || _mutationState.status == PantryMutationStatus.submitting) {
      return null;
    }
    final generation = ++_mutationGeneration;
    _mutationTargetPublicId = null;
    _mutationState = const PantryMutationState(
      status: PantryMutationStatus.submitting,
    );
    notifyListeners();
    try {
      final item = await repository.create(request);
      if (!_isMutationCurrent(generation)) return null;
      _reconcileList(item);
      _mutationState = PantryMutationState(
        status: PantryMutationStatus.succeeded,
        item: item,
      );
      notifyListeners();
      return item;
    } on Object catch (error) {
      if (!_isMutationCurrent(generation)) return null;
      _mutationState = PantryMutationState(
        status: PantryMutationStatus.error,
        errorMessage: pantryMutationErrorMessage(error),
      );
      notifyListeners();
      return null;
    }
  }

  Future<PantryItem?> updateMetadata(
    String publicId,
    UpdatePantryMetadataRequest request,
  ) async {
    if (_disposed || _mutationState.status == PantryMutationStatus.submitting) {
      return null;
    }
    final generation = ++_mutationGeneration;
    final detailGeneration = _detailGeneration;
    _mutationTargetPublicId = publicId;
    _mutationState = const PantryMutationState(
      status: PantryMutationStatus.submitting,
    );
    notifyListeners();
    try {
      final item = await repository.updateMetadata(publicId, request);
      if (!_isMutationCurrent(generation) ||
          detailGeneration != _detailGeneration ||
          _detailState.publicId != publicId) {
        return null;
      }
      _detailState = PantryDetailState(
        status: PantryLoadStatus.loaded,
        publicId: publicId,
        item: item,
      );
      _reconcileList(item);
      _mutationTargetPublicId = null;
      _mutationState = PantryMutationState(
        status: PantryMutationStatus.succeeded,
        item: item,
      );
      notifyListeners();
      return item;
    } on Object catch (error) {
      if (!_isMutationCurrent(generation) ||
          detailGeneration != _detailGeneration ||
          _detailState.publicId != publicId) {
        return null;
      }
      _mutationTargetPublicId = null;
      _mutationState = PantryMutationState(
        status: PantryMutationStatus.error,
        errorMessage: pantryMutationErrorMessage(error),
      );
      notifyListeners();
      return null;
    }
  }

  void _reconcileList(PantryItem item) {
    _listGeneration++;
    if (!_hasListSnapshot) {
      _listState = PantryListState(includeClosed: _listState.includeClosed);
      return;
    }
    final items = [
      for (final existing in _listState.items)
        if (existing.publicId != item.publicId) existing,
      if (_listState.includeClosed || _isOpen(item.status)) item,
    ]..sort(_compareLots);
    _listState = PantryListState(
      status: PantryLoadStatus.loaded,
      includeClosed: _listState.includeClosed,
      items: List.unmodifiable(items),
    );
  }

  static bool _isOpen(PantryItemStatus status) =>
      status == PantryItemStatus.available ||
      status == PantryItemStatus.reserved;

  static int _compareLots(PantryItem a, PantryItem b) {
    final aExpiry = a.expiryDate;
    final bExpiry = b.expiryDate;
    if (aExpiry == null && bExpiry != null) return 1;
    if (aExpiry != null && bExpiry == null) return -1;
    if (aExpiry != null && bExpiry != null) {
      final dateOrder = PantryDate.format(aExpiry)
          .compareTo(PantryDate.format(bExpiry));
      if (dateOrder != 0) return dateOrder;
    }
    final createdOrder = a.createdAt.compareTo(b.createdAt);
    return createdOrder != 0 ? createdOrder : a.publicId.compareTo(b.publicId);
  }

  bool _isMutationCurrent(int generation) =>
      !_disposed && generation == _mutationGeneration;

  void resetForSessionChange() {
    if (_disposed) return;
    _listGeneration++;
    _detailGeneration++;
    _mutationGeneration++;
    _mutationTargetPublicId = null;
    _hasListSnapshot = false;
    _listState = const PantryListState();
    _detailState = const PantryDetailState();
    _mutationState = const PantryMutationState();
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
    _mutationGeneration++;
    super.dispose();
  }
}
