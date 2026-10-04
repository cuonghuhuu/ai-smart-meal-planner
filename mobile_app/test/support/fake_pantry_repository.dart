import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';

const testPantryId = '00000000-0000-4000-8000-000000000201';
const testPantryIdTwo = '00000000-0000-4000-8000-000000000202';

PantryItem pantryItem({
  String publicId = testPantryId,
  String ingredientName = 'Gạo',
  String unitCode = 'bag',
  String unitDisplayName = 'bao',
  PantryItemStatus status = PantryItemStatus.available,
  DateTime? expiryDate,
  DateTime? closedAt,
  String? foodName,
  String? note,
  bool acquiredOnPresent = true,
}) => PantryItem(
  publicId: publicId,
  ingredientPublicId: '00000000-0000-4000-8000-000000000101',
  ingredientCode: 'RICE',
  ingredientName: ingredientName,
  foodPublicId: foodName == null
      ? null
      : '00000000-0000-4000-8000-000000000102',
  foodCode: foodName == null ? null : 'FOOD_RICE',
  foodName: foodName,
  quantityInitial: PantryDecimal.parse('1.2345'),
  quantityRemaining: PantryDecimal.parse('0.0001'),
  unitCode: unitCode,
  unitDisplayName: unitDisplayName,
  storageLocation: PantryStorageLocation.fridge,
  acquiredOn: acquiredOnPresent ? DateTime(2026, 1, 1) : null,
  expiryDate: expiryDate,
  expiryKind: expiryDate == null
      ? PantryExpiryKind.unknown
      : PantryExpiryKind.useBy,
  expiryConfidence: expiryDate == null
      ? PantryExpiryConfidence.unknown
      : PantryExpiryConfidence.labelled,
  status: status,
  closedAt: closedAt,
  note: note,
  version: 1,
  createdAt: DateTime(2026, 1, 1, 10),
  updatedAt: DateTime(2026, 1, 1, 11),
);

final class FakePantryRepository implements PantryRepository {
  List<PantryItem> items = [pantryItem()];
  PantryItem? detail;
  Object? listError;
  Object? detailError;
  Future<List<PantryItem>> Function(bool includeClosed)? onList;
  Future<PantryItem> Function(String publicId)? onDetail;
  final listRequests = <bool>[];
  final detailRequests = <String>[];

  @override
  Future<List<PantryItem>> list({bool includeClosed = false}) {
    listRequests.add(includeClosed);
    if (onList != null) return onList!(includeClosed);
    if (listError != null) return Future.error(listError!);
    return Future.value(items);
  }

  @override
  Future<PantryItem> getByPublicId(String publicId) {
    detailRequests.add(publicId);
    if (onDetail != null) return onDetail!(publicId);
    if (detailError != null) return Future.error(detailError!);
    return Future.value(detail ?? items.first);
  }

  @override
  Future<PantryItem> create(CreatePantryItemRequest request) =>
      throw UnimplementedError('Read-only fake');

  @override
  Future<PantryItem> updateMetadata(
    String publicId,
    UpdatePantryMetadataRequest request,
  ) => throw UnimplementedError('Read-only fake');

  @override
  Future<PantryItem> adjust(String publicId, AdjustPantryItemRequest request) =>
      throw UnimplementedError('Read-only fake');

  @override
  Future<PantryItem> consume(
    String publicId,
    ConsumePantryItemRequest request,
  ) => throw UnimplementedError('Read-only fake');

  @override
  Future<PantryItem> discard(
    String publicId,
    DiscardPantryItemRequest request,
  ) => throw UnimplementedError('Read-only fake');
}
