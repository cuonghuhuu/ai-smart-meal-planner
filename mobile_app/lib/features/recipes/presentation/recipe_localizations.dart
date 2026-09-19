final class RecipeLocalizations {
  const RecipeLocalizations._();

  static String difficultyName(String code) => switch (code) {
    'EASY' => 'Dễ',
    'MEDIUM' => 'Vừa',
    'HARD' => 'Khó',
    _ => code,
  };

  static String sourceName(String code) => switch (code) {
    'CURATED' => 'Tuyển chọn',
    'IMPORTED' => 'Đã nhập',
    'USER_CREATED' => 'Người dùng tạo',
    _ => code,
  };
}
