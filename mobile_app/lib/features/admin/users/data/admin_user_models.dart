import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

final class AdminUserItem {
  const AdminUserItem({
    required this.publicId,
    required this.email,
    required this.displayName,
    required this.accountStatus,
    required this.roles,
    required this.emailVerifiedAt,
    required this.lastLoginAt,
    required this.createdAt,
  });

  factory AdminUserItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return AdminUserItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      email: CatalogJson.requiredString(json, 'email'),
      displayName: CatalogJson.requiredString(json, 'displayName'),
      accountStatus: CatalogJson.requiredString(json, 'accountStatus'),
      roles: CatalogJson.strings(json['roles']),
      emailVerifiedAt: CatalogJson.optionalString(json, 'emailVerifiedAt'),
      lastLoginAt: CatalogJson.optionalString(json, 'lastLoginAt'),
      createdAt: CatalogJson.requiredString(json, 'createdAt'),
    );
  }

  final String publicId;
  final String email;
  final String displayName;
  final String accountStatus;
  final List<String> roles;
  final String? emailVerifiedAt;
  final String? lastLoginAt;
  final String createdAt;
}

final class AdminUserPage {
  const AdminUserPage({
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.content,
  });

  factory AdminUserPage.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return AdminUserPage(
      page: CatalogJson.requiredInt(json, 'page'),
      size: CatalogJson.requiredInt(json, 'size'),
      totalElements: CatalogJson.requiredInt(json, 'totalElements'),
      totalPages: CatalogJson.requiredInt(json, 'totalPages'),
      content: CatalogJson.list(json['content'], AdminUserItem.fromJson),
    );
  }

  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final List<AdminUserItem> content;
}
