import 'package:flutter/foundation.dart';
import 'package:smart_meal_planner/core/api/api_exception.dart';
import 'package:smart_meal_planner/features/profile/application/profile_validation.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';
import 'package:smart_meal_planner/features/profile/data/profile_repository.dart';
import 'package:smart_meal_planner/features/profile/data/reference_data_repository.dart';

enum ProfileStatus {
  initial,
  loading,
  loadedExisting,
  loadedNew,
  saving,
  conflict,
  error,
}

final class ProfileState {
  ProfileState({
    required this.status,
    required this.draft,
    required List<ActivityLevelReference> activityLevels,
    required List<NutritionGoalReference> nutritionGoals,
    required this.revision,
    this.profile,
    this.errorMessage,
    this.saveMessage,
  }) : activityLevels = List.unmodifiable(activityLevels),
       nutritionGoals = List.unmodifiable(nutritionGoals);

  factory ProfileState.initial() => ProfileState(
    status: ProfileStatus.initial,
    draft: const ProfileDraft.empty(),
    activityLevels: const [],
    nutritionGoals: const [],
    revision: 0,
  );

  final ProfileStatus status;
  final ProfileDraft draft;
  final List<ActivityLevelReference> activityLevels;
  final List<NutritionGoalReference> nutritionGoals;
  final Profile? profile;
  final String? errorMessage;
  final String? saveMessage;
  final int revision;

  bool get isLoaded =>
      status == ProfileStatus.loadedExisting ||
      status == ProfileStatus.loadedNew ||
      status == ProfileStatus.saving ||
      status == ProfileStatus.conflict;
  bool get hasEditableProfile =>
      isLoaded || (status == ProfileStatus.error && revision > 0);
  bool get isNewProfile => status == ProfileStatus.loadedNew;
}

final class ProfileController extends ChangeNotifier {
  ProfileController({
    required this.profileRepository,
    required this.referenceDataRepository,
  }) : _state = ProfileState.initial();

  final ProfileRepository profileRepository;
  final ReferenceDataRepository referenceDataRepository;
  ProfileState _state;
  int _revision = 0;

  ProfileState get state => _state;

  Future<void> load() async {
    final previous = _state;
    _state = ProfileState(
      status: ProfileStatus.loading,
      draft: previous.draft,
      activityLevels: previous.activityLevels,
      nutritionGoals: previous.nutritionGoals,
      profile: previous.profile,
      revision: previous.revision,
    );
    notifyListeners();

    try {
      final results = await Future.wait<Object?>([
        _profileOrNull(),
        referenceDataRepository.getActivityLevels(),
        referenceDataRepository.getNutritionGoals(),
      ]);
      final profile = results[0] as Profile?;
      final activityLevels = results[1] as List<ActivityLevelReference>;
      final nutritionGoals = results[2] as List<NutritionGoalReference>;
      _revision++;
      _state = ProfileState(
        status: profile == null
            ? ProfileStatus.loadedNew
            : ProfileStatus.loadedExisting,
        draft: profile?.toDraft() ?? const ProfileDraft.empty(),
        activityLevels: activityLevels,
        nutritionGoals: nutritionGoals,
        profile: profile,
        revision: _revision,
      );
    } on Object catch (error) {
      _state = ProfileState(
        status: ProfileStatus.error,
        draft: previous.draft,
        activityLevels: previous.activityLevels,
        nutritionGoals: previous.nutritionGoals,
        profile: previous.profile,
        errorMessage: profileErrorMessage(error),
        revision: previous.revision,
      );
    }
    notifyListeners();
  }

  Future<void> reload() => load();

  Future<void> save(ProfileDraft draft) async {
    if (_state.status == ProfileStatus.saving) {
      return;
    }
    final validationErrors = ProfileValidation.validate(draft);
    if (validationErrors.isNotEmpty) {
      _state = ProfileState(
        status: ProfileStatus.error,
        draft: draft,
        activityLevels: _state.activityLevels,
        nutritionGoals: _state.nutritionGoals,
        profile: _state.profile,
        errorMessage: 'Please correct the highlighted profile fields.',
        revision: _state.revision,
      );
      notifyListeners();
      return;
    }

    _state = ProfileState(
      status: ProfileStatus.saving,
      draft: draft,
      activityLevels: _state.activityLevels,
      nutritionGoals: _state.nutritionGoals,
      profile: _state.profile,
      revision: _state.revision,
    );
    notifyListeners();

    try {
      final updated = await profileRepository.updateProfile(draft);
      _revision++;
      _state = ProfileState(
        status: ProfileStatus.loadedExisting,
        draft: updated.toDraft(),
        activityLevels: _state.activityLevels,
        nutritionGoals: _state.nutritionGoals,
        profile: updated,
        saveMessage: 'Profile saved successfully.',
        revision: _revision,
      );
    } on ApiHttpException catch (error) {
      _state = ProfileState(
        status: error.statusCode == 409
            ? ProfileStatus.conflict
            : ProfileStatus.error,
        draft: draft,
        activityLevels: _state.activityLevels,
        nutritionGoals: _state.nutritionGoals,
        profile: _state.profile,
        errorMessage: profileErrorMessage(error),
        revision: _state.revision,
      );
    } on Object catch (error) {
      _state = ProfileState(
        status: ProfileStatus.error,
        draft: draft,
        activityLevels: _state.activityLevels,
        nutritionGoals: _state.nutritionGoals,
        profile: _state.profile,
        errorMessage: profileErrorMessage(error),
        revision: _state.revision,
      );
    }
    notifyListeners();
  }

  Future<Profile?> _profileOrNull() async {
    try {
      return await profileRepository.getProfile();
    } on ProfileNotFoundException {
      return null;
    }
  }
}

String profileErrorMessage(Object error) {
  if (error is ApiTransportException) {
    return 'Unable to reach the service. Please try again.';
  }
  if (error is ApiHttpException) {
    if (error.statusCode == 409) {
      return 'Your profile was changed elsewhere. Reload the latest version before saving.';
    }
    if (error.statusCode >= 500) {
      return 'The service could not complete this request. Please try again.';
    }
    if (error.problem?.detail != null && error.statusCode == 400) {
      return error.problem!.detail!;
    }
  }
  return 'The profile could not be loaded or saved. Please try again.';
}
