final class MealPlanLocalizations {
  const MealPlanLocalizations._();

  static String statusName(String code) => switch (code) {
    'DRAFT' => 'Bản nháp',
    'ACCEPTED' => 'Đã chấp nhận',
    'ACTIVE' => 'Đang thực hiện',
    'COMPLETED' => 'Đã hoàn tất',
    'ABANDONED' => 'Đã dừng',
    _ => code,
  };

  static String consumptionName(String code) => switch (code) {
    'PLANNED' => 'Đã lên kế hoạch',
    'EATEN' => 'Đã ăn',
    'SKIPPED' => 'Đã bỏ qua',
    'REPLACED' => 'Đã thay thế',
    _ => code,
  };
}
