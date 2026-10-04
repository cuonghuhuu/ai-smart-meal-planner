import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';

const testPantryId = '00000000-0000-4000-8000-000000000201';
const testPantryIdTwo = '00000000-0000-4000-8000-000000000202';

PantryItem pantryItem({
  String publicId = testPantryId,
  String ingredientName = 'Gạo',
  String unitCode = 'bag',
  String unitDisplayName = 'bao',
  String quantityInitial = '1.2345',
  String quantityRemaining = '0.0001',
  PantryItemStatus status = PantryItemStatus.available,
  PantryStorageLocation storageLocation = PantryStorageLocation.fridge,
  DateTime? expiryDate,
  DateTime? closedAt,
  String? foodName,
  String? note,
  bool acquiredOnPresent = true,
  DateTime? acquiredOnOverride,
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
  quantityInitial: PantryDecimal.parse(quantityInitial),
  quantityRemaining: PantryDecimal.parse(quantityRemaining),
  unitCode: unitCode,
  unitDisplayName: unitDisplayName,
  storageLocation: storageLocation,
  acquiredOn: acquiredOnPresent
      ? (acquiredOnOverride ?? DateTime(2026, 1, 1))
      : null,
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
  Future<PantryItem> Function(CreatePantryItemRequest request)? onCreate;
  Future<PantryItem> Function(
    String publicId,
    UpdatePantryMetadataRequest request,
  )?
  onUpdateMetadata;
  Future<PantryItem> Function(String, AdjustPantryItemRequest)? onAdjust;
  Future<PantryItem> Function(String, ConsumePantryItemRequest)? onConsume;
  Future<PantryItem> Function(String, DiscardPantryItemRequest)? onDiscard;
  Object? createError;
  Object? updateError;
  Object? adjustError;
  Object? consumeError;
  Object? discardError;
  final createRequests = <CreatePantryItemRequest>[];
  final updateRequests = <(String, UpdatePantryMetadataRequest)>[];
  final adjustRequests = <(String, AdjustPantryItemRequest)>[];
  final consumeRequests = <(String, ConsumePantryItemRequest)>[];
  final discardRequests = <(String, DiscardPantryItemRequest)>[];
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
  Future<PantryItem> create(CreatePantryItemRequest request) {
    createRequests.add(request);
    if (onCreate != null) return onCreate!(request);
    if (createError != null) return Future.error(createError!);
    return Future.value(pantryItem(publicId: testPantryIdTwo));
  }

  @override
  Future<PantryItem> updateMetadata(
    String publicId,
    UpdatePantryMetadataRequest request,
  ) {
    updateRequests.add((publicId, request));
    if (onUpdateMetadata != null) return onUpdateMetadata!(publicId, request);
    if (updateError != null) return Future.error(updateError!);
    return Future.value(detail ?? items.first);
  }

  @override
  Future<PantryItem> adjust(String publicId, AdjustPantryItemRequest request) {
    adjustRequests.add((publicId, request));
    if (onAdjust != null) return onAdjust!(publicId, request);
    if (adjustError != null) return Future.error(adjustError!);
    return Future.value(detail ?? items.first);
  }

  @override
  Future<PantryItem> consume(
    String publicId,
    ConsumePantryItemRequest request,
  ) {
    consumeRequests.add((publicId, request));
    if (onConsume != null) return onConsume!(publicId, request);
    if (consumeError != null) return Future.error(consumeError!);
    return Future.value(detail ?? items.first);
  }

  @override
  Future<PantryItem> discard(
    String publicId,
    DiscardPantryItemRequest request,
  ) {
    discardRequests.add((publicId, request));
    if (onDiscard != null) return onDiscard!(publicId, request);
    if (discardError != null) return Future.error(discardError!);
    return Future.value(detail ?? items.first);
  }
}
