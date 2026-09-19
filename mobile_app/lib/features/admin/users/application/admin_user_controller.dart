import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/admin/users/data/admin_user_models.dart';
import 'package:smart_meal_planner/features/admin/users/data/admin_user_repository.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

enum AdminUserListStatus { initial, loading, loaded, loadingMore, error }

final class AdminUserListState {
  const AdminUserListState({
    required this.status,
    required this.searchQuery,
    required this.statusFilter,
    required this.items,
    required this.page,
    required this.totalElements,
    required this.totalPages,
    required this.errorMessage,
    required this.loadMoreErrorMessage,
  });

  factory AdminUserListState.initial() => const AdminUserListState(
    status: AdminUserListStatus.initial,
    searchQuery: '',
    statusFilter: null,
    items: <AdminUserItem>[],
    page: 0,
    totalElements: 0,
    totalPages: 0,
    errorMessage: null,
    loadMoreErrorMessage: null,
  );

  final AdminUserListStatus status;
  final String searchQuery;
  final String? statusFilter;
  final List<AdminUserItem> items;
  final int page;
  final int totalElements;
  final int totalPages;
  final String? errorMessage;
  final String? loadMoreErrorMessage;

  bool get hasMore => page + 1 < totalPages;
  bool get isInitialLoading =>
      status == AdminUserListStatus.loading && items.isEmpty;
  bool get isLoadingMore => status == AdminUserListStatus.loadingMore;
}

final class AdminUserController extends ChangeNotifier {
  AdminUserController({required this.repository, this.pageSize = 20})
    : _state = AdminUserListState.initial();

  final AdminUserRepository repository;
  final int pageSize;

  AdminUserListState _state;
  bool _disposed = false;
  int _generation = 0;
  String? _mutatingPublicId;
  String? _actionErrorMessage;

  AdminUserListState get state => _state;
  String? get mutatingPublicId => _mutatingPublicId;
  String? get actionErrorMessage => _actionErrorMessage;

  Future<void> loadInitial() {
    if (_disposed || _state.status == AdminUserListStatus.loading) {
      return Future<void>.value();
    }
    return _loadFirstPage(_state.searchQuery, _state.statusFilter);
  }

  Future<void> reload() =>
      _loadFirstPage(_state.searchQuery, _state.statusFilter);

  Future<void> search(String query) =>
      _loadFirstPage(query, _state.statusFilter);

  Future<void> setStatus(String? status) =>
      _loadFirstPage(_state.searchQuery, status);

  Future<void> loadMore() async {
    if (_disposed ||
        _state.status != AdminUserListStatus.loaded ||
        !_state.hasMore) {
      return;
    }
    final generation = _generation;
    final nextPage = _state.page + 1;
    _state = AdminUserListState(
      status: AdminUserListStatus.loadingMore,
      searchQuery: _state.searchQuery,
      statusFilter: _state.statusFilter,
      items: _state.items,
      page: _state.page,
      totalElements: _state.totalElements,
      totalPages: _state.totalPages,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getUsers(
        query: _state.searchQuery,
        status: _state.statusFilter,
        page: nextPage,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      final ids = {for (final item in _state.items) item.publicId};
      final additions = [
        for (final item in result.content)
          if (ids.add(item.publicId)) item,
      ];
      _state = AdminUserListState(
        status: AdminUserListStatus.loaded,
        searchQuery: _state.searchQuery,
        statusFilter: _state.statusFilter,
        items: [..._state.items, ...additions],
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = AdminUserListState(
        status: AdminUserListStatus.loaded,
        searchQuery: _state.searchQuery,
        statusFilter: _state.statusFilter,
        items: _state.items,
        page: _state.page,
        totalElements: _state.totalElements,
        totalPages: _state.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: _adminUserErrorMessage(error),
      );
    }
    if (_isCurrent(generation)) _notify();
  }

  Future<bool> changeStatus(String publicId, String status) async {
    if (_disposed || _mutatingPublicId != null) return false;
    final generation = _generation;
    _mutatingPublicId = publicId;
    _actionErrorMessage = null;
    _notify();
    try {
      final updated = await repository.changeStatus(publicId, status);
      if (!_isCurrent(generation)) return false;
      final index = _state.items.indexWhere(
        (item) => item.publicId == updated.publicId,
      );
      if (index >= 0) {
        final items = [..._state.items]..[index] = updated;
        _state = AdminUserListState(
          status: _state.status,
          searchQuery: _state.searchQuery,
          statusFilter: _state.statusFilter,
          items: items,
          page: _state.page,
          totalElements: _state.totalElements,
          totalPages: _state.totalPages,
          errorMessage: _state.errorMessage,
          loadMoreErrorMessage: _state.loadMoreErrorMessage,
        );
      }
      return true;
    } on Object catch (error) {
      if (!_disposed) {
        _actionErrorMessage = _adminUserErrorMessage(error);
        _notify();
      }
      return false;
    } finally {
      if (!_disposed) {
        _mutatingPublicId = null;
        _notify();
      }
    }
  }

  void resetForSessionChange() {
    if (_disposed) return;
    _generation++;
    _state = AdminUserListState.initial();
    _mutatingPublicId = null;
    _actionErrorMessage = null;
    _notify();
  }

  Future<void> _loadFirstPage(String query, String? status) async {
    if (_disposed) return;
    final generation = ++_generation;
    final normalizedQuery = query.trim();
    final normalizedStatus = status?.trim();
    _state = AdminUserListState(
      status: AdminUserListStatus.loading,
      searchQuery: normalizedQuery,
      statusFilter: normalizedStatus == null || normalizedStatus.isEmpty
          ? null
          : normalizedStatus,
      items: const <AdminUserItem>[],
      page: 0,
      totalElements: 0,
      totalPages: 0,
      errorMessage: null,
      loadMoreErrorMessage: null,
    );
    _notify();
    try {
      final result = await repository.getUsers(
        query: normalizedQuery,
        status: _state.statusFilter,
        page: 0,
        size: pageSize,
      );
      if (!_isCurrent(generation)) return;
      _state = AdminUserListState(
        status: AdminUserListStatus.loaded,
        searchQuery: normalizedQuery,
        statusFilter: _state.statusFilter,
        items: result.content,
        page: result.page,
        totalElements: result.totalElements,
        totalPages: result.totalPages,
        errorMessage: null,
        loadMoreErrorMessage: null,
      );
    } on Object catch (error) {
      if (!_isCurrent(generation)) return;
      _state = AdminUserListState(
        status: AdminUserListStatus.error,
        searchQuery: normalizedQuery,
        statusFilter: _state.statusFilter,
        items: const <AdminUserItem>[],
        page: 0,
        totalElements: 0,
        totalPages: 0,
        errorMessage: _adminUserErrorMessage(error),
        loadMoreErrorMessage: null,
      );
    }
    if (_isCurrent(generation)) _notify();
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

String _adminUserErrorMessage(Object error) {
  if (error is ApiTransportException) return AppStrings.unableToReachService;
  if (error is ApiHttpException) {
    if (error.statusCode == 403) return AppStrings.adminForbidden;
    if (error.statusCode == 404) return AppStrings.adminNotFound;
    if (error.statusCode == 409) return AppStrings.adminConflict;
    if (error.statusCode >= 500) return AppStrings.serviceUnavailable;
  }
  return AppStrings.adminUserRequestFailed;
}
