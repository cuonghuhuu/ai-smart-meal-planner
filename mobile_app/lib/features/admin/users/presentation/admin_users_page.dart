import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/admin/users/application/admin_user_controller.dart';
import 'package:smart_meal_planner/features/admin/users/data/admin_user_models.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class AdminUsersPage extends StatefulWidget {
  const AdminUsersPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final AdminUserController controller;

  @override
  State<AdminUsersPage> createState() => _AdminUsersPageState();
}

class _AdminUsersPageState extends State<AdminUsersPage> {
  late final TextEditingController _searchController;

  AdminUserController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(text: _controller.state.searchQuery);
    _controller.addListener(_onChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.loadInitial();
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 8,
    content: _content(context),
  );

  Widget _content(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('admin-users-list'),
          children: [
            Text(
              AppStrings.adminUsers,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.adminUsersSubtitle),
            const SizedBox(height: 20),
            TextField(
              key: const ValueKey('admin-users-search'),
              controller: _searchController,
              textInputAction: TextInputAction.search,
              onSubmitted: _controller.search,
              decoration: InputDecoration(
                labelText: AppStrings.adminSearch,
                suffixIcon: IconButton(
                  key: const ValueKey('admin-users-search-submit'),
                  onPressed: () => _controller.search(_searchController.text),
                  icon: const Icon(Icons.search),
                ),
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              key: const ValueKey('admin-users-status-filter'),
              initialValue: state.statusFilter ?? '',
              decoration: const InputDecoration(
                labelText: AppStrings.adminStatusFilter,
              ),
              items: const [
                DropdownMenuItem(value: '', child: Text(AppStrings.adminAllStatuses)),
                DropdownMenuItem(value: 'ACTIVE', child: Text(AppStrings.adminActive)),
                DropdownMenuItem(value: 'SUSPENDED', child: Text(AppStrings.adminSuspended)),
                DropdownMenuItem(
                  value: 'PENDING_VERIFICATION',
                  child: Text(AppStrings.adminPendingVerification),
                ),
                DropdownMenuItem(
                  value: 'DEACTIVATED',
                  child: Text(AppStrings.adminDeactivated),
                ),
              ],
              onChanged: (value) => _controller.setStatus(value),
            ),
            const SizedBox(height: 20),
            ..._body(context, state),
          ],
        ),
      ),
    );
  }

  List<Widget> _body(BuildContext context, AdminUserListState state) {
    if (state.isInitialLoading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == AdminUserListStatus.error) {
      return [
        _AdminUsersErrorPanel(
          message: state.errorMessage ?? AppStrings.adminUserLoadFailed,
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        Padding(
          padding: EdgeInsets.symmetric(vertical: 32),
          child: Center(child: Text(AppStrings.adminUsersEmpty)),
        ),
      ];
    }
    return [
      if (_controller.actionErrorMessage != null)
        _AdminMessage(text: _controller.actionErrorMessage!),
      for (final user in state.items)
        _AdminUserCard(
          key: ValueKey('admin-user-item-${user.publicId}'),
          user: user,
          busy: _controller.mutatingPublicId == user.publicId,
          allowStatusChange:
              user.publicId != widget.sessionController.identity?.publicId,
          onChangeStatus: () => _confirmStatus(context, user),
        ),
      if (state.loadMoreErrorMessage != null)
        _AdminMessage(text: state.loadMoreErrorMessage!),
      if (state.hasMore)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: OutlinedButton(
            key: const ValueKey('admin-users-load-more'),
            onPressed: state.isLoadingMore ? null : _controller.loadMore,
            child: state.isLoadingMore
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Text(AppStrings.loadMore),
          ),
        ),
    ];
  }

  Future<void> _confirmStatus(BuildContext context, AdminUserItem user) async {
    final isActive = user.accountStatus == 'ACTIVE';
    final nextStatus = isActive ? 'SUSPENDED' : 'ACTIVE';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(isActive ? AppStrings.adminSuspend : AppStrings.adminReactivate),
        content: Text(
          isActive
              ? AppStrings.adminSuspendConfirmation
              : AppStrings.adminReactivateConfirmation,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text(AppStrings.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text(isActive ? AppStrings.adminSuspend : AppStrings.adminReactivate),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await _controller.changeStatus(user.publicId, nextStatus);
    }
  }
}

final class _AdminUserCard extends StatelessWidget {
  const _AdminUserCard({
    super.key,
    required this.user,
    required this.busy,
    required this.allowStatusChange,
    required this.onChangeStatus,
  });

  final AdminUserItem user;
  final bool busy;
  final bool allowStatusChange;
  final VoidCallback onChangeStatus;

  @override
  Widget build(BuildContext context) {
    final canChange = allowStatusChange &&
        (user.accountStatus == 'ACTIVE' ||
            user.accountStatus == 'SUSPENDED');
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(user.displayName, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(user.email),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                Chip(label: Text(_statusLabel(user.accountStatus))),
                for (final role in user.roles) Chip(label: Text(role)),
              ],
            ),
            const SizedBox(height: 8),
            Text('${AppStrings.adminCreatedAt}: ${user.createdAt}'),
            if (canChange)
              Align(
                alignment: Alignment.centerRight,
                child: OutlinedButton(
                  key: ValueKey(
                    user.accountStatus == 'ACTIVE'
                        ? 'admin-user-suspend-${user.publicId}'
                        : 'admin-user-reactivate-${user.publicId}',
                  ),
                  onPressed: busy ? null : onChangeStatus,
                  child: busy
                      ? const SizedBox.square(
                          dimension: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : Text(
                          user.accountStatus == 'ACTIVE'
                              ? AppStrings.adminSuspend
                              : AppStrings.adminReactivate,
                        ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  static String _statusLabel(String status) => switch (status) {
    'ACTIVE' => AppStrings.adminActive,
    'SUSPENDED' => AppStrings.adminSuspended,
    'PENDING_VERIFICATION' => AppStrings.adminPendingVerification,
    'DEACTIVATED' => AppStrings.adminDeactivated,
    _ => status,
  };
}

final class _AdminUsersErrorPanel extends StatelessWidget {
  const _AdminUsersErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Column(
    children: [
      Text(message),
      const SizedBox(height: 12),
      OutlinedButton(
        key: const ValueKey('admin-users-retry'),
        onPressed: onRetry,
        child: const Text(AppStrings.retry),
      ),
    ],
  );
}

final class _AdminMessage extends StatelessWidget {
  const _AdminMessage({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Text(text),
  );
}
