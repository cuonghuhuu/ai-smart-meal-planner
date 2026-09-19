import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';

final class MealPlanItem {
  const MealPlanItem({
    required this.publicId,
    required this.title,
    required this.startDate,
    required this.endDate,
    required this.dayCount,
    required this.mealsPerDayTarget,
    required this.defaultServings,
    required this.status,
    required this.createdAt,
  });

  factory MealPlanItem.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return MealPlanItem(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      title: CatalogJson.requiredString(json, 'title'),
      startDate: CatalogJson.requiredString(json, 'startDate'),
      endDate: CatalogJson.requiredString(json, 'endDate'),
      dayCount: CatalogJson.requiredInt(json, 'dayCount'),
      mealsPerDayTarget: CatalogJson.requiredInt(json, 'mealsPerDayTarget'),
      defaultServings: CatalogJson.requiredInt(json, 'defaultServings'),
      status: CatalogJson.requiredString(json, 'status'),
      createdAt: CatalogJson.requiredString(json, 'createdAt'),
    );
  }

  final String publicId;
  final String title;
  final String startDate;
  final String endDate;
  final int dayCount;
  final int mealsPerDayTarget;
  final int defaultServings;
  final String status;
  final String createdAt;
}

final class MealPlanEntry {
  const MealPlanEntry({
    required this.planDate,
    required this.mealSlotCode,
    required this.mealSlotDisplayName,
    required this.position,
    required this.recipePublicId,
    required this.recipeTitle,
    required this.servings,
    required this.provenance,
    required this.consumptionStatus,
    required this.recommendationTotalScore,
    required this.recommendationExplanation,
  });

  factory MealPlanEntry.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return MealPlanEntry(
      planDate: CatalogJson.requiredString(json, 'planDate'),
      mealSlotCode: CatalogJson.requiredString(json, 'mealSlotCode'),
      mealSlotDisplayName: CatalogJson.requiredString(
        json,
        'mealSlotDisplayName',
      ),
      position: CatalogJson.requiredInt(json, 'position'),
      recipePublicId: CatalogJson.requiredString(json, 'recipePublicId'),
      recipeTitle: CatalogJson.requiredString(json, 'recipeTitle'),
      servings: CatalogJson.requiredDouble(json, 'servings'),
      provenance: CatalogJson.requiredString(json, 'provenance'),
      consumptionStatus: CatalogJson.requiredString(
        json,
        'consumptionStatus',
      ),
      recommendationTotalScore: CatalogJson.optionalDouble(
        json,
        'recommendationTotalScore',
      ),
      recommendationExplanation: CatalogJson.optionalString(
        json,
        'recommendationExplanation',
      ),
    );
  }

  final String planDate;
  final String mealSlotCode;
  final String mealSlotDisplayName;
  final int position;
  final String recipePublicId;
  final String recipeTitle;
  final double servings;
  final String provenance;
  final String consumptionStatus;
  final double? recommendationTotalScore;
  final String? recommendationExplanation;
}

final class MealPlanDetail {
  const MealPlanDetail({
    required this.publicId,
    required this.title,
    required this.startDate,
    required this.endDate,
    required this.dayCount,
    required this.mealsPerDayTarget,
    required this.defaultServings,
    required this.status,
    required this.acceptedAt,
    required this.createdAt,
    required this.entries,
  });

  factory MealPlanDetail.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return MealPlanDetail(
      publicId: CatalogJson.requiredString(json, 'publicId'),
      title: CatalogJson.requiredString(json, 'title'),
      startDate: CatalogJson.requiredString(json, 'startDate'),
      endDate: CatalogJson.requiredString(json, 'endDate'),
      dayCount: CatalogJson.requiredInt(json, 'dayCount'),
      mealsPerDayTarget: CatalogJson.requiredInt(json, 'mealsPerDayTarget'),
      defaultServings: CatalogJson.requiredInt(json, 'defaultServings'),
      status: CatalogJson.requiredString(json, 'status'),
      acceptedAt: CatalogJson.optionalString(json, 'acceptedAt'),
      createdAt: CatalogJson.requiredString(json, 'createdAt'),
      entries: CatalogJson.list(json['entries'], MealPlanEntry.fromJson),
    );
  }

  final String publicId;
  final String title;
  final String startDate;
  final String endDate;
  final int dayCount;
  final int mealsPerDayTarget;
  final int defaultServings;
  final String status;
  final String? acceptedAt;
  final String createdAt;
  final List<MealPlanEntry> entries;
}

final class MealPlanGeneration {
  const MealPlanGeneration({required this.plan, required this.warnings});

  factory MealPlanGeneration.fromJson(Object? value) {
    final json = CatalogJson.map(value);
    return MealPlanGeneration(
      plan: MealPlanDetail.fromJson(json['plan']),
      warnings: CatalogJson.strings(json['warnings']),
    );
  }

  final MealPlanDetail plan;
  final List<String> warnings;
}

final class MealPlanGenerationRequest {
  const MealPlanGenerationRequest({
    required this.startDate,
    required this.days,
    required this.mealSlotCodes,
    required this.defaultServings,
    this.maxMinutesPerMeal,
  });

  final String startDate;
  final int days;
  final List<String> mealSlotCodes;
  final int defaultServings;
  final int? maxMinutesPerMeal;

  Map<String, Object?> toJson() => {
    'startDate': startDate,
    'days': days,
    'mealSlotCodes': mealSlotCodes,
    'defaultServings': defaultServings,
    if (maxMinutesPerMeal != null) 'maxMinutesPerMeal': maxMinutesPerMeal,
  };
}
