import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/features/pantry/application/pantry_validation.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';

void main() {
  group('fixed-scale quantities', () {
    test('accepts valid positive values including exact maximum', () {
      for (final text in ['1', '1.0', '0.0001', '1.2345', '99999999.9999']) {
        expect(PantryValidation.isPositiveQuantity(text), isTrue, reason: text);
      }
      expect(PantryDecimal.parse('1.2500').toString(), '1.25');
      expect(
        PantryDecimal.parse('99999999.9999').scaledUnits,
        BigInt.from(999999999999),
      );
    });

    test('rejects invalid positive values and malformed text', () {
      for (final text in [
        '',
        '   ',
        '0',
        '-1',
        '99999999.99999',
        '100000000',
        '1.23456',
        'one',
        '1..2',
        '1e2',
        '1E-4',
        '+1',
        '.5',
        '1.',
      ]) {
        expect(
          PantryValidation.isPositiveQuantity(text),
          isFalse,
          reason: text,
        );
      }
    });

    test(
      'signed adjustments accept both signs but reject zero and overflow',
      () {
        expect(PantryValidation.isNonzeroAdjustment('0.0001'), isTrue);
        expect(PantryValidation.isNonzeroAdjustment('-1.2500'), isTrue);
        expect(PantryValidation.isNonzeroAdjustment('0'), isFalse);
        expect(PantryValidation.isNonzeroAdjustment('-0.0000'), isFalse);
        expect(PantryValidation.isNonzeroAdjustment('-100000000'), isFalse);
        expect(PantryDecimal.parse('-0.0001').scaledUnits, BigInt.from(-1));
      },
    );

    test('adjustment limits use exact scaled arithmetic', () {
      final initial = PantryDecimal.parse('1.0000');
      final remaining = PantryDecimal.parse('0.9999');
      expect(
        PantryValidation.canAdjust(
          initial: initial,
          remaining: remaining,
          quantityDelta: '0.0001',
        ),
        isTrue,
      );
      expect(
        PantryValidation.canAdjust(
          initial: initial,
          remaining: remaining,
          quantityDelta: '0.0002',
        ),
        isFalse,
      );
      expect(
        PantryValidation.canAdjust(
          initial: initial,
          remaining: remaining,
          quantityDelta: '-0.9998',
        ),
        isTrue,
      );
      expect(
        PantryValidation.canAdjust(
          initial: initial,
          remaining: remaining,
          quantityDelta: '-0.9999',
        ),
        isFalse,
      );
      expect(
        PantryValidation.canAdjust(
          initial: initial,
          remaining: remaining,
          quantityDelta: '0.0001',
          status: PantryItemStatus.reserved,
        ),
        isFalse,
      );
    });

    test('consumption compares scaled amounts exactly', () {
      final remaining = PantryDecimal.parse('0.0001');
      expect(
        PantryValidation.canConsume(remaining: remaining, quantity: '0.0001'),
        isTrue,
      );
      expect(
        PantryValidation.canConsume(remaining: remaining, quantity: '0.0002'),
        isFalse,
      );
      expect(
        PantryValidation.canConsume(remaining: remaining, quantity: '0'),
        isFalse,
      );
      expect(
        PantryValidation.canConsume(
          remaining: remaining,
          quantity: '0.0001',
          status: PantryItemStatus.reserved,
        ),
        isFalse,
      );
    });

    test('discard matches backend open statuses', () {
      expect(PantryValidation.canDiscard(PantryItemStatus.available), isTrue);
      expect(PantryValidation.canDiscard(PantryItemStatus.reserved), isTrue);
      for (final status in [
        PantryItemStatus.consumed,
        PantryItemStatus.discarded,
        PantryItemStatus.expired,
      ]) {
        expect(PantryValidation.canDiscard(status), isFalse);
      }
    });
  });

  group('expiry', () {
    final acquired = DateTime(2026, 10, 4);
    final earlier = DateTime(2026, 10, 3);
    final later = DateTime(2026, 10, 5);

    bool valid({
      DateTime? acquiredOn,
      DateTime? expiryDate,
      PantryExpiryKind kind = PantryExpiryKind.unknown,
      PantryExpiryConfidence confidence = PantryExpiryConfidence.unknown,
    }) => PantryValidation.isValidExpiry(
      acquiredOn: acquiredOn,
      expiryDate: expiryDate,
      expiryKind: kind,
      expiryConfidence: confidence,
    );

    test('unknown date requires both UNKNOWN values', () {
      expect(valid(), isTrue);
      expect(valid(kind: PantryExpiryKind.useBy), isFalse);
      expect(valid(confidence: PantryExpiryConfidence.estimated), isFalse);
    });

    test('dated expiry requires known kind and confidence', () {
      expect(valid(expiryDate: later), isFalse);
      expect(valid(expiryDate: later, kind: PantryExpiryKind.useBy), isFalse);
      expect(
        valid(expiryDate: later, confidence: PantryExpiryConfidence.labelled),
        isFalse,
      );
    });

    test('validates chronology without rejecting past or future dates', () {
      expect(
        valid(
          acquiredOn: acquired,
          expiryDate: earlier,
          kind: PantryExpiryKind.bestBefore,
          confidence: PantryExpiryConfidence.estimated,
        ),
        isFalse,
      );
      expect(
        valid(
          acquiredOn: acquired,
          expiryDate: acquired,
          kind: PantryExpiryKind.bestBefore,
          confidence: PantryExpiryConfidence.estimated,
        ),
        isTrue,
      );
      expect(
        valid(
          acquiredOn: acquired,
          expiryDate: later,
          kind: PantryExpiryKind.useBy,
          confidence: PantryExpiryConfidence.labelled,
        ),
        isTrue,
      );
      expect(valid(acquiredOn: DateTime(2030, 1, 1)), isTrue);
      expect(
        valid(
          expiryDate: DateTime(2020, 1, 1),
          kind: PantryExpiryKind.useBy,
          confidence: PantryExpiryConfidence.labelled,
        ),
        isTrue,
      );
    });
  });

  test('notes trim, clear blank text, and enforce 255 characters', () {
    expect(PantryNote.normalize('  use first  '), 'use first');
    expect(PantryNote.normalize('   '), isNull);
    expect(PantryValidation.isValidNote('  ${'x' * 255}  '), isTrue);
    expect(PantryValidation.isValidNote('x' * 256), isFalse);
  });

  test('unit codes accept any nonblank backend code', () {
    expect(PantryValidation.isValidUnitCode(' '), isFalse);
    expect(PantryValidation.isValidUnitCode('  custom_unit  '), isTrue);
    expect(PantryUnitCode.normalize('  custom_unit  '), 'custom_unit');
  });
}
