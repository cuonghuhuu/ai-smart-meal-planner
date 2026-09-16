import 'package:flutter_test/flutter_test.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';

void main() {
  test(
    'load transitions from initial to loading to existing profile',
    () async {
      final repository = FakeProfileRepository(profile: _profile(version: 4));
      final controller = _controller(repository);
      final statuses = <ProfileStatus>[];
      controller.addListener(() => statuses.add(controller.state.status));

      await controller.load();

      expect(statuses, [ProfileStatus.loading, ProfileStatus.loadedExisting]);
      expect(controller.state.draft.version, 4);
    },
  );

  test('404 loads a new profile with safe defaults', () async {
    final controller = _controller(FakeProfileRepository(notFound: true));

    await controller.load();

    expect(controller.state.status, ProfileStatus.loadedNew);
    expect(controller.state.draft.householdSize, 1);
    expect(controller.state.draft.version, 0);
    expect(controller.state.draft.heightCm, isNull);
  });

  test('existing profile save sends its current version', () async {
    final repository = FakeProfileRepository(profile: _profile(version: 9));
    final controller = _controller(repository);
    await controller.load();
    final draft = controller.state.draft.copyWith(heightCm: 175.0);

    await controller.save(draft);

    expect(repository.lastDraft?.version, 9);
  });

  test('new profile first save sends version zero', () async {
    final repository = FakeProfileRepository(notFound: true);
    final controller = _controller(repository);
    await controller.load();

    await controller.save(controller.state.draft.copyWith(heightCm: 175.0));

    expect(repository.lastDraft?.version, 0);
  });

  test('successful save replaces the local model and version', () async {
    final repository = FakeProfileRepository(
      profile: _profile(version: 2),
      updatedProfile: _profile(version: 3, heightCm: 181),
    );
    final controller = _controller(repository);
    await controller.load();

    await controller.save(controller.state.draft.copyWith(heightCm: 181.0));

    expect(controller.state.status, ProfileStatus.loadedExisting);
    expect(controller.state.profile?.version, 3);
    expect(controller.state.draft.version, 3);
    expect(controller.state.draft.heightCm, 181);
  });

  test('409 creates conflict and does not silently retry', () async {
    final repository = FakeProfileRepository(
      profile: _profile(version: 5),
      updateError: const ApiHttpException(409),
    );
    final controller = _controller(repository);
    await controller.load();
    final draft = controller.state.draft.copyWith(heightCm: 185.0);

    await controller.save(draft);

    expect(controller.state.status, ProfileStatus.conflict);
    expect(repository.updateCalls, 1);
    expect(controller.state.draft.heightCm, 185);
  });

  test('explicit reload replaces stale data with latest profile', () async {
    final repository = FakeProfileRepository(
      profiles: [_profile(version: 1), _profile(version: 2, heightCm: 183)],
    );
    final controller = _controller(repository);
    await controller.load();
    expect(controller.state.draft.version, 1);

    await controller.reload();

    expect(controller.state.draft.version, 2);
    expect(controller.state.draft.heightCm, 183);
  });

  test('recoverable save error preserves editable values', () async {
    final repository = FakeProfileRepository(
      profile: _profile(version: 6),
      updateError: const ApiTransportException(ApiTransportFailureKind.network),
    );
    final controller = _controller(repository);
    await controller.load();
    final draft = controller.state.draft.copyWith(
      heightCm: 188.0,
      notes: 'Keep these edits.',
    );

    await controller.save(draft);

    expect(controller.state.status, ProfileStatus.error);
    expect(controller.state.draft.heightCm, 188);
    expect(controller.state.draft.notes, 'Keep these edits.');
    expect(controller.state.draft.version, 6);
    expect(controller.state.hasEditableProfile, isTrue);
  });
}

ProfileController _controller(FakeProfileRepository repository) =>
    ProfileController(
      profileRepository: repository,
      referenceDataRepository: FakeReferenceDataRepository(),
    );

Profile _profile({int version = 1, double heightCm = 170}) => Profile(
  birthDate: DateTime(1990, 1, 2),
  sex: 'OTHER',
  heightCm: heightCm,
  activityLevel: 'MODERATE',
  nutritionGoal: 'MAINTAIN',
  targetWeightKg: 70,
  weeklyChangeKg: 0,
  householdSize: 1,
  maxCookMinutes: 30,
  notes: 'Notes',
  version: version,
  createdAt: DateTime(2026, 1, 1),
  updatedAt: DateTime(2026, 1, 2),
);

final class FakeProfileRepository implements ProfileRepository {
  FakeProfileRepository({
    this.profile,
    this.updatedProfile,
    this.notFound = false,
    this.updateError,
    this.profiles,
  });

  Profile? profile;
  Profile? updatedProfile;
  final bool notFound;
  final Object? updateError;
  final List<Profile>? profiles;
  var getCalls = 0;
  var updateCalls = 0;
  ProfileDraft? lastDraft;

  @override
  Future<Profile> getProfile() async {
    getCalls++;
    if (profiles != null && getCalls <= profiles!.length) {
      return profiles![getCalls - 1];
    }
    if (notFound) throw const ProfileNotFoundException();
    return profile!;
  }

  @override
  Future<Profile> updateProfile(ProfileDraft draft) async {
    updateCalls++;
    lastDraft = draft;
    if (updateError != null) throw updateError!;
    return updatedProfile ?? profile!;
  }
}

final class FakeReferenceDataRepository implements ReferenceDataRepository {
  @override
  Future<List<ActivityLevelReference>> getActivityLevels() async => const [
    ActivityLevelReference(
      code: 'MODERATE',
      displayName: 'Moderately Active',
      description: 'Some movement',
      energyFactor: 1.55,
      displayOrder: 1,
    ),
  ];

  @override
  Future<List<NutritionGoalReference>> getNutritionGoals() async => const [
    NutritionGoalReference(
      code: 'MAINTAIN',
      displayName: 'Maintain Weight',
      description: 'Keep weight stable',
      displayOrder: 1,
    ),
  ];
}
