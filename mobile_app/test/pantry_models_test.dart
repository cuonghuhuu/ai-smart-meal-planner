import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';

const _itemId = '11111111-1111-4111-8111-111111111111';
const _ingredientId = '22222222-2222-4222-8222-222222222222';
const _foodId = '33333333-3333-4333-8333-333333333333';

final _completeItem = <String, Object?>{
  'publicId': _itemId,
  'ingredientPublicId': _ingredientId,
  'ingredientCode': 'tomato',
  'ingredientName': 'Tomato',
  'foodPublicId': _foodId,
  'foodCode': 'raw_tomato',
  'foodName': 'Raw tomato',
  'quantityInitial': 1.2500,
  'quantityRemaining': 0.0001,
  'unitCode': 'server_defined_unit',
  'unitDisplayName': 'Server defined unit',
  'storageLocation': 'FRIDGE',
  'acquiredOn': '2026-10-04',
  'expiryDate': '2026-10-06',
  'expiryKind': 'USE_BY',
  'expiryConfidence': 'LABELLED',
  'status': 'AVAILABLE',
  'closedAt': '2026-10-07T10:20:30.123456',
  'note': 'lot note',
  'version': 3,
  'createdAt': '2026-10-04T08:09:10.123456',
  'updatedAt': '2026-10-05T11:12:13.000001',
};

void main() {
  group('Pantry response', () {
    test('parses every field, quantities, dates, and local timestamps', () {
      final item = PantryItem.fromJson(_completeItem);
      expect(item.publicId, _itemId);
      expect(item.ingredientPublicId, _ingredientId);
      expect(item.ingredientCode, 'tomato');
      expect(item.ingredientName, 'Tomato');
      expect(item.foodPublicId, _foodId);
      expect(item.foodCode, 'raw_tomato');
      expect(item.foodName, 'Raw tomato');
      expect(item.quantityInitial.toString(), '1.25');
      expect(item.quantityRemaining.toString(), '0.0001');
      expect(item.unitCode, 'server_defined_unit');
      expect(item.unitDisplayName, 'Server defined unit');
      expect(item.storageLocation, PantryStorageLocation.fridge);
      expect(PantryDate.format(item.acquiredOn!), '2026-10-04');
      expect(PantryDate.format(item.expiryDate!), '2026-10-06');
      expect(item.expiryKind, PantryExpiryKind.useBy);
      expect(item.expiryConfidence, PantryExpiryConfidence.labelled);
      expect(item.status, PantryItemStatus.available);
      expect(item.closedAt!.isUtc, isFalse);
      expect(item.closedAt!.microsecond, 456);
      expect(item.note, 'lot note');
      expect(item.version, 3);
      expect(item.createdAt.isUtc, isFalse);
      expect(item.createdAt.microsecond, 456);
      expect(item.updatedAt.isUtc, isFalse);
      expect(item.updatedAt.microsecond, 1);
    });

    test('nullable food and optional metadata remain null', () {
      final item = PantryItem.fromJson({
        ..._completeItem,
        'foodPublicId': null,
        'foodCode': null,
        'foodName': null,
        'acquiredOn': null,
        'expiryDate': null,
        'expiryKind': 'UNKNOWN',
        'expiryConfidence': 'UNKNOWN',
        'closedAt': null,
        'note': null,
      });
      expect(item.foodPublicId, isNull);
      expect(item.foodCode, isNull);
      expect(item.foodName, isNull);
      expect(item.acquiredOn, isNull);
      expect(item.expiryDate, isNull);
      expect(item.closedAt, isNull);
      expect(item.note, isNull);
    });

    test('rejects malformed response fields', () {
      expect(
        () => PantryItem.fromJson({..._completeItem, 'quantityRemaining': '1'}),
        throwsA(isA<ApiResponseFormatException>()),
      );
      expect(
        () =>
            PantryItem.fromJson({..._completeItem, 'expiryDate': '2026-02-30'}),
        throwsA(isA<ApiResponseFormatException>()),
      );
      expect(
        () => PantryItem.fromJson({..._completeItem, 'status': 'NEW_STATUS'}),
        throwsA(isA<ApiResponseFormatException>()),
      );
      expect(
        () => PantryItem.fromJson({..._completeItem, 'createdAt': false}),
        throwsA(isA<ApiResponseFormatException>()),
      );
    });
  });

  test('all enum wire values round-trip exactly', () {
    for (final value in PantryStorageLocation.values) {
      expect(PantryStorageLocation.fromWireValue(value.wireValue), value);
    }
    expect(
      PantryStorageLocation.values.map((value) => value.wireValue).toList(),
      ['PANTRY', 'FRIDGE', 'FREEZER', 'OTHER'],
    );
    for (final value in PantryItemStatus.values) {
      expect(PantryItemStatus.fromWireValue(value.wireValue), value);
    }
    expect(PantryItemStatus.values.map((value) => value.wireValue).toList(), [
      'AVAILABLE',
      'RESERVED',
      'CONSUMED',
      'DISCARDED',
      'EXPIRED',
    ]);
    for (final value in PantryExpiryKind.values) {
      expect(PantryExpiryKind.fromWireValue(value.wireValue), value);
    }
    expect(PantryExpiryKind.values.map((value) => value.wireValue).toList(), [
      'USE_BY',
      'BEST_BEFORE',
      'UNKNOWN',
    ]);
    for (final value in PantryExpiryConfidence.values) {
      expect(PantryExpiryConfidence.fromWireValue(value.wireValue), value);
    }
    expect(
      PantryExpiryConfidence.values.map((value) => value.wireValue).toList(),
      ['LABELLED', 'ESTIMATED', 'UNKNOWN'],
    );
  });

  test('date-only parsing and serialization preserve calendar fields', () {
    final date = PantryDate.parseOptional('2026-10-04')!;
    expect(date.isUtc, isFalse);
    expect(PantryDate.format(date), '2026-10-04');
    expect(PantryDate.format(DateTime.utc(2026, 10, 4, 23)), '2026-10-04');
  });

  test('JSON numbers retain minimum and maximum four-place quantity', () {
    expect(
      jsonEncode({'quantity': PantryDecimal.parse('0.0001').toJsonNumber()}),
      '{"quantity":0.0001}',
    );
    expect(
      jsonEncode({
        'quantity': PantryDecimal.parse('99999999.9999').toJsonNumber(),
      }),
      '{"quantity":99999999.9999}',
    );
  });

  group('Pantry requests', () {
    test('create serializes full body, defaults, and normalized note', () {
      const request = CreatePantryItemRequest(
        ingredientPublicId: _ingredientId,
        quantity: '1.2500',
        unitCode: '  tbsp  ',
        storageLocation: PantryStorageLocation.pantry,
        note: '  new lot  ',
      );
      expect(request.toJson(), {
        'ingredientPublicId': _ingredientId,
        'foodPublicId': null,
        'quantity': 1.25,
        'unitCode': 'tbsp',
        'storageLocation': 'PANTRY',
        'acquiredOn': null,
        'expiryDate': null,
        'expiryKind': 'UNKNOWN',
        'expiryConfidence': 'UNKNOWN',
        'note': 'new lot',
      });
    });

    test('create serializes mapped food and date-only fields', () {
      final request = CreatePantryItemRequest(
        ingredientPublicId: _ingredientId,
        foodPublicId: _foodId,
        quantity: '1',
        unitCode: 'piece',
        storageLocation: PantryStorageLocation.fridge,
        acquiredOn: DateTime(2026, 10, 4),
        expiryDate: DateTime(2026, 10, 6),
        expiryKind: PantryExpiryKind.bestBefore,
        expiryConfidence: PantryExpiryConfidence.estimated,
      );
      expect(request.toJson()['foodPublicId'], _foodId);
      expect(request.toJson()['acquiredOn'], '2026-10-04');
      expect(request.toJson()['expiryDate'], '2026-10-06');
      expect(request.toJson()['expiryKind'], 'BEST_BEFORE');
      expect(request.toJson()['expiryConfidence'], 'ESTIMATED');
    });

    test('metadata PUT serializes only complete editable metadata', () {
      final request = UpdatePantryMetadataRequest(
        storageLocation: PantryStorageLocation.freezer,
        acquiredOn: DateTime(2026, 10, 4),
        expiryDate: DateTime(2026, 10, 6),
        expiryKind: PantryExpiryKind.useBy,
        expiryConfidence: PantryExpiryConfidence.labelled,
        note: '  frozen  ',
      );
      expect(request.toJson(), {
        'storageLocation': 'FREEZER',
        'acquiredOn': '2026-10-04',
        'expiryDate': '2026-10-06',
        'expiryKind': 'USE_BY',
        'expiryConfidence': 'LABELLED',
        'note': 'frozen',
      });
      for (final forbidden in [
        'quantity',
        'quantityInitial',
        'quantityRemaining',
        'ingredientPublicId',
        'foodPublicId',
        'unitCode',
        'status',
        'version',
      ]) {
        expect(request.toJson().containsKey(forbidden), isFalse);
      }
    });

    test('metadata PUT explicitly clears nullable values', () {
      expect(
        const UpdatePantryMetadataRequest(
          storageLocation: PantryStorageLocation.other,
          note: '  ',
        ).toJson(),
        {
          'storageLocation': 'OTHER',
          'acquiredOn': null,
          'expiryDate': null,
          'expiryKind': 'UNKNOWN',
          'expiryConfidence': 'UNKNOWN',
          'note': null,
        },
      );
    });

    test('quantity actions serialize exact bodies', () {
      expect(
        const AdjustPantryItemRequest(
          quantityDelta: '-0.2500',
          note: '  corrected  ',
        ).toJson(),
        {'quantityDelta': -0.25, 'note': 'corrected'},
      );
      expect(
        const ConsumePantryItemRequest(
          quantity: '0.0001',
          note: '  used  ',
        ).toJson(),
        {'quantity': 0.0001, 'note': 'used'},
      );
      expect(const DiscardPantryItemRequest().toJson(), isNull);
      expect(const DiscardPantryItemRequest(note: '  spoiled  ').toJson(), {
        'note': 'spoiled',
      });
      expect(const DiscardPantryItemRequest(note: '  ').toJson(), {
        'note': null,
      });
    });
  });
}
