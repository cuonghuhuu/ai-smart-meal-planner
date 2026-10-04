import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:smart_meal_planner/core/api/api_client.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/auth/domain/auth_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_models.dart';
import 'package:smart_meal_planner/features/pantry/data/pantry_repository.dart';

const _itemId = '11111111-1111-4111-8111-111111111111';
const _ingredientId = '22222222-2222-4222-8222-222222222222';

final _item = <String, Object?>{
  'publicId': _itemId,
  'ingredientPublicId': _ingredientId,
  'ingredientCode': 'tomato',
  'ingredientName': 'Tomato',
  'foodPublicId': null,
  'foodCode': null,
  'foodName': null,
  'quantityInitial': 1.25,
  'quantityRemaining': 1.25,
  'unitCode': 'tbsp',
  'unitDisplayName': 'tablespoon',
  'storageLocation': 'FRIDGE',
  'acquiredOn': null,
  'expiryDate': null,
  'expiryKind': 'UNKNOWN',
  'expiryConfidence': 'UNKNOWN',
  'status': 'AVAILABLE',
  'closedAt': null,
  'note': null,
  'version': 0,
  'createdAt': '2026-10-04T08:09:10',
  'updatedAt': '2026-10-04T08:09:10',
};

void main() {
  group('Pantry reads', () {
    test('default and history lists use exact URLs without CSRF', () async {
      final sent = <http.Request>[];
      var csrfCalls = 0;
      final repository = _repository(
        sent,
        csrfTokenProvider: () async {
          csrfCalls++;
          throw StateError('GET must not request CSRF');
        },
        respond: (_) => http.Response(jsonEncode([_item]), 200),
      );

      final open = await repository.list();
      final history = await repository.list(includeClosed: true);

      expect(open.single.publicId, _itemId);
      expect(history.single.unitCode, 'tbsp');
      expect(sent.map((request) => request.url.toString()), [
        'https://backend.test/api/v1/me/pantry',
        'https://backend.test/api/v1/me/pantry?includeClosed=true',
      ]);
      expect(sent.every((request) => request.method == 'GET'), isTrue);
      expect(sent.every(_isAuthenticated), isTrue);
      expect(
        sent.every((request) => !request.headers.containsKey('x-csrf-token')),
        isTrue,
      );
      expect(csrfCalls, 0);
    });

    test('detail uses public UUID and parses 200 item', () async {
      final sent = <http.Request>[];
      final repository = _repository(sent);
      final item = await repository.getByPublicId(_itemId);
      expect(sent.single.url.path, '/api/v1/me/pantry/$_itemId');
      expect(sent.single.method, 'GET');
      expect(_isAuthenticated(sent.single), isTrue);
      expect(item.quantityInitial.toString(), '1.25');
      expect(item.status, PantryItemStatus.available);
    });
  });

  group('Pantry mutations', () {
    test('native-style Bearer create and PUT omit CSRF when no callback is configured', () async {
      final sent = <http.Request>[];
      final repository = _repository(sent);
      await repository.create(
        const CreatePantryItemRequest(
          ingredientPublicId: _ingredientId,
          quantity: '0.0001',
          unitCode: 'tbsp',
          storageLocation: PantryStorageLocation.fridge,
        ),
      );
      await repository.updateMetadata(
        _itemId,
        const UpdatePantryMetadataRequest(
          storageLocation: PantryStorageLocation.freezer,
        ),
      );
      expect(sent.map((request) => request.method), ['POST', 'PUT']);
      expect(sent.every(_isAuthenticated), isTrue);
      expect(
        sent.every((request) => !request.headers.containsKey('x-csrf-token')),
        isTrue,
      );
      expect(jsonDecode(sent.first.body)['quantity'], 0.0001);
      expect(
        (jsonDecode(sent.last.body) as Map).containsKey('quantity'),
        isFalse,
      );
    });

    test('create sends complete JSON, auth and mutation CSRF', () async {
      final sent = <http.Request>[];
      var csrfCalls = 0;
      final repository = _repository(
        sent,
        csrfTokenProvider: () async {
          csrfCalls++;
          return const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');
        },
      );
      final item = await repository.create(
        const CreatePantryItemRequest(
          ingredientPublicId: _ingredientId,
          quantity: '1.2500',
          unitCode: ' tbsp ',
          storageLocation: PantryStorageLocation.fridge,
          note: '  acquired  ',
        ),
      );
      expect(item.publicId, _itemId);
      expect(sent.single.method, 'POST');
      expect(sent.single.url.path, '/api/v1/me/pantry');
      expect(_isAuthenticated(sent.single), isTrue);
      expect(sent.single.headers['x-csrf-token'], 'csrf');
      expect(jsonDecode(sent.single.body), {
        'ingredientPublicId': _ingredientId,
        'foodPublicId': null,
        'quantity': 1.25,
        'unitCode': 'tbsp',
        'storageLocation': 'FRIDGE',
        'acquiredOn': null,
        'expiryDate': null,
        'expiryKind': 'UNKNOWN',
        'expiryConfidence': 'UNKNOWN',
        'note': 'acquired',
      });
      expect(csrfCalls, 1);
    });

    test(
      'metadata PUT sends complete nullable state and no quantity',
      () async {
        final sent = <http.Request>[];
        var csrfCalls = 0;
        final repository = _repository(
          sent,
          csrfTokenProvider: () async {
            csrfCalls++;
            return const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');
          },
        );
        await repository.updateMetadata(
          _itemId,
          const UpdatePantryMetadataRequest(
            storageLocation: PantryStorageLocation.freezer,
            note: ' ',
          ),
        );
        expect(sent.single.method, 'PUT');
        expect(sent.single.url.path, '/api/v1/me/pantry/$_itemId');
        expect(_isAuthenticated(sent.single), isTrue);
        expect(sent.single.headers['x-csrf-token'], 'csrf');
        expect(jsonDecode(sent.single.body), {
          'storageLocation': 'FREEZER',
          'acquiredOn': null,
          'expiryDate': null,
          'expiryKind': 'UNKNOWN',
          'expiryConfidence': 'UNKNOWN',
          'note': null,
        });
        expect(
          (jsonDecode(sent.single.body) as Map).containsKey('quantity'),
          isFalse,
        );
        expect(csrfCalls, 1);
      },
    );

    test(
      'adjust, consume and discard use exact endpoints and bodies',
      () async {
        final sent = <http.Request>[];
        var csrfCalls = 0;
        final repository = _repository(
          sent,
          csrfTokenProvider: () async {
            csrfCalls++;
            return const CsrfToken(headerName: 'X-CSRF-TOKEN', value: 'csrf');
          },
        );

        await repository.adjust(
          _itemId,
          const AdjustPantryItemRequest(
            quantityDelta: '-0.25',
            note: '  fix  ',
          ),
        );
        await repository.consume(
          _itemId,
          const ConsumePantryItemRequest(quantity: '0.5', note: '  used  '),
        );
        await repository.discard(
          _itemId,
          const DiscardPantryItemRequest(note: '  spoiled  '),
        );
        await repository.discard(_itemId, const DiscardPantryItemRequest());

        expect(sent.map((request) => request.url.path), [
          '/api/v1/me/pantry/$_itemId/adjust',
          '/api/v1/me/pantry/$_itemId/consume',
          '/api/v1/me/pantry/$_itemId/discard',
          '/api/v1/me/pantry/$_itemId/discard',
        ]);
        expect(sent.every((request) => request.method == 'POST'), isTrue);
        expect(sent.every(_isAuthenticated), isTrue);
        expect(
          sent.every((request) => request.headers['x-csrf-token'] == 'csrf'),
          isTrue,
        );
        expect(jsonDecode(sent[0].body), {
          'quantityDelta': -0.25,
          'note': 'fix',
        });
        expect(jsonDecode(sent[1].body), {'quantity': 0.5, 'note': 'used'});
        expect(jsonDecode(sent[2].body), {'note': 'spoiled'});
        expect(sent[3].body, isEmpty);
        expect(csrfCalls, 4);
      },
    );

    test(
      'mutation works without optional CSRF callback for native client',
      () async {
        final sent = <http.Request>[];
        final repository = _repository(sent);
        await repository.consume(
          _itemId,
          const ConsumePantryItemRequest(quantity: '0.25'),
        );
        expect(_isAuthenticated(sent.single), isTrue);
        expect(sent.single.headers.containsKey('x-csrf-token'), isFalse);
      },
    );
  });

  test('shared HTTP/problem error is preserved', () async {
    final sent = <http.Request>[];
    final repository = _repository(
      sent,
      respond: (_) => http.Response(
        jsonEncode({
          'status': 409,
          'code': 'ITEM_NOT_OPEN',
          'detail': 'The request conflicts with the current resource state.',
          'requestId': 'request-id',
        }),
        409,
        headers: {'content-type': 'application/problem+json'},
      ),
    );
    await expectLater(
      repository.consume(
        _itemId,
        const ConsumePantryItemRequest(quantity: '1'),
      ),
      throwsA(
        isA<ApiHttpException>()
            .having((error) => error.statusCode, 'statusCode', 409)
            .having((error) => error.problem?.code, 'code', 'ITEM_NOT_OPEN'),
      ),
    );
  });

  test('malformed list and item responses use shared format failure', () async {
    final sent = <http.Request>[];
    final malformedList = _repository(
      sent,
      respond: (_) => http.Response(jsonEncode({'content': []}), 200),
    );
    await expectLater(
      malformedList.list(),
      throwsA(isA<ApiResponseFormatException>()),
    );

    final malformedItem = _repository(
      sent,
      respond: (_) =>
          http.Response(jsonEncode({..._item, 'version': '0'}), 200),
    );
    await expectLater(
      malformedItem.getByPublicId(_itemId),
      throwsA(isA<ApiResponseFormatException>()),
    );
  });
}

HttpPantryRepository _repository(
  List<http.Request> sent, {
  Future<CsrfToken> Function()? csrfTokenProvider,
  http.Response Function(http.Request)? respond,
}) {
  final client =
      ApiClient(
        baseUrl: 'https://backend.test',
        httpClient: MockClient((request) async {
          sent.add(request);
          return respond?.call(request) ??
              http.Response(jsonEncode(_item), 200);
        }),
      )..configureAuthentication(
        accessTokenProvider: () => 'access',
        refreshAccessToken: () async => false,
      );
  return HttpPantryRepository(client, csrfTokenProvider: csrfTokenProvider);
}

bool _isAuthenticated(http.Request request) =>
    request.headers['authorization'] == 'Bearer access';
