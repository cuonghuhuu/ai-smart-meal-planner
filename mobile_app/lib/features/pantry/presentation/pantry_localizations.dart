final class PantryLocalizations {
  const PantryLocalizations._();

  static String storageName(String code) => switch (code) {
    'PANTRY' => 'Tủ bếp',
    'FRIDGE' => 'Tủ lạnh',
    'FREEZER' => 'Ngăn đông',
    'OTHER' => 'Khác',
    _ => code,
  };

  static String statusName(String code) => switch (code) {
    'AVAILABLE' => 'Đang có',
    'RESERVED' => 'Đã giữ',
    'CONSUMED' => 'Đã dùng hết',
    'DISCARDED' => 'Đã bỏ',
    'EXPIRED' => 'Đã hết hạn',
    _ => code,
  };

  static String expiryName(String code) => switch (code) {
    'USE_BY' => 'Hạn sử dụng',
    'BEST_BEFORE' => 'Nên dùng trước',
    'UNKNOWN' => 'Chưa xác định',
    _ => code,
  };
}
