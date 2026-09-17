/// Presentation-only Vietnamese labels for stable backend reference codes.
/// Unknown codes deliberately fall back to the server-provided text.
final class ReferenceLocalizations {
  const ReferenceLocalizations._();

  static String activityName(String code, String fallback) =>
      _activityNames[code] ?? fallback;

  static String activityDescription(String code, String fallback) =>
      _activityDescriptions[code] ?? fallback;

  static String nutritionGoalName(String code, String fallback) =>
      _nutritionGoalNames[code] ?? fallback;

  static String nutritionGoalDescription(String code, String fallback) =>
      _nutritionGoalDescriptions[code] ?? fallback;

  static String dietaryName(String code, String fallback) =>
      _dietaryNames[code] ?? fallback;

  static String dietaryDescription(String code, String fallback) =>
      _dietaryDescriptions[code] ?? fallback;

  static String allergenName(String code, String fallback) =>
      _allergenNames[code] ?? fallback;

  static String allergenDescription(String code, String fallback) =>
      _allergenDescriptions[code] ?? fallback;

  static String reactionKindName(String wireValue, String fallback) =>
      _reactionKindNames[wireValue] ?? fallback;

  static const Map<String, String> _activityNames = {
    'SEDENTARY': 'Ít vận động',
    'LIGHT': 'Hoạt động nhẹ',
    'MODERATE': 'Hoạt động vừa phải',
    'VERY_ACTIVE': 'Rất năng động',
    'EXTRA_ACTIVE': 'Cực kỳ năng động',
  };

  static const Map<String, String> _activityDescriptions = {
    'SEDENTARY': 'Ít hoặc không tập luyện theo kế hoạch; chủ yếu ngồi.',
    'LIGHT': 'Tập thể dục hoặc chơi thể thao nhẹ từ một đến ba ngày mỗi tuần.',
    'MODERATE':
        'Tập thể dục hoặc chơi thể thao vừa phải từ ba đến năm ngày mỗi tuần.',
    'VERY_ACTIVE': 'Tập thể dục hoặc chơi thể thao cường độ cao từ sáu đến bảy ngày mỗi tuần.',
    'EXTRA_ACTIVE': 'Tập luyện rất nặng hằng ngày hoặc làm công việc đòi hỏi nhiều thể lực.',
  };

  static const Map<String, String> _nutritionGoalNames = {
    'LOSE_WEIGHT': 'Giảm cân',
    'MAINTAIN': 'Duy trì cân nặng',
    'GAIN_WEIGHT': 'Tăng cân',
    'BUILD_MUSCLE': 'Tăng cơ',
    'EAT_HEALTHIER': 'Ăn uống lành mạnh hơn',
    'REDUCE_WASTE': 'Giảm lãng phí thực phẩm',
  };

  static const Map<String, String> _nutritionGoalDescriptions = {
    'LOSE_WEIGHT': 'Hướng đến mức thâm hụt năng lượng vừa phải.',
    'MAINTAIN': 'Hướng đến việc cân bằng năng lượng nạp vào và tiêu hao.',
    'GAIN_WEIGHT': 'Hướng đến mức dư năng lượng vừa phải.',
    'BUILD_MUSCLE': 'Ưu tiên lượng đạm phù hợp cùng mức dư năng lượng nhỏ.',
    'EAT_HEALTHIER': 'Không đặt mục tiêu cân nặng; ưu tiên sự đa dạng và chất lượng dinh dưỡng.',
    'REDUCE_WASTE':
        'Ưu tiên sử dụng những thực phẩm hiện có trong tủ đựng thức ăn.',
  };

  static const Map<String, String> _dietaryNames = {
    'VEGETARIAN': 'Ăn chay',
    'VEGAN': 'Thuần chay',
    'PESCATARIAN': 'Ăn chay có cá',
    'HALAL': 'Halal',
    'KOSHER': 'Kosher',
    'GLUTEN_FREE': 'Không chứa gluten',
    'DAIRY_FREE': 'Không chứa sữa',
    'LOW_CARB': 'Ít tinh bột',
    'LOW_SODIUM': 'Ít natri',
    'HIGH_PROTEIN': 'Giàu đạm',
    'MEDITERRANEAN': 'Chế độ Địa Trung Hải',
  };

  static const Map<String, String> _dietaryDescriptions = {
    'VEGETARIAN': 'Loại trừ thịt, gia cầm và cá.',
    'VEGAN': 'Loại trừ mọi sản phẩm từ động vật.',
    'PESCATARIAN': 'Loại trừ thịt và gia cầm; bao gồm cá và hải sản.',
    'HALAL': 'Tuân theo các yêu cầu về chế độ ăn Halal.',
    'KOSHER': 'Tuân theo các yêu cầu về chế độ ăn Kosher.',
    'GLUTEN_FREE': 'Loại trừ các loại ngũ cốc chứa gluten.',
    'DAIRY_FREE': 'Loại trừ sữa và các nguyên liệu có nguồn gốc từ sữa.',
    'LOW_CARB': 'Ưu tiên món ăn có tỷ lệ carbohydrate thấp hơn.',
    'LOW_SODIUM': 'Ưu tiên món ăn có ít muối bổ sung hơn.',
    'HIGH_PROTEIN': 'Ưu tiên món ăn có tỷ lệ đạm cao hơn.',
    'MEDITERRANEAN': 'Ưu tiên rau củ, các loại đậu, cá và dầu ô liu.',
  };

  static const Map<String, String> _allergenNames = {
    'GLUTEN': 'Ngũ cốc chứa gluten',
    'CRUSTACEANS': 'Động vật giáp xác',
    'EGG': 'Trứng',
    'FISH': 'Cá',
    'PEANUT': 'Đậu phộng',
    'SOY': 'Đậu nành',
    'MILK': 'Sữa',
    'TREE_NUT': 'Hạt cây',
    'CELERY': 'Cần tây',
    'MUSTARD': 'Mù tạt',
    'SESAME': 'Mè',
    'SULPHITES': 'Lưu huỳnh đioxit và sulfit',
    'LUPIN': 'Đậu lupin',
    'MOLLUSCS': 'Động vật thân mềm',
  };

  static const Map<String, String> _reactionKindNames = {
    'ALLERGY': 'Dị ứng',
    'INTOLERANCE': 'Không dung nạp',
    'UNSPECIFIED': 'Chưa xác định',
  };

  static const Map<String, String> _allergenDescriptions = {
    'GLUTEN': 'Lúa mì, lúa mạch đen, lúa mạch, yến mạch, lúa mì spelt và các giống lai.',
    'CRUSTACEANS': 'Tôm, cua, tôm hùm và các loài tương tự.',
    'EGG': 'Trứng và các nguyên liệu có nguồn gốc từ trứng.',
    'FISH': 'Cá và các nguyên liệu có nguồn gốc từ cá.',
    'PEANUT': 'Đậu phộng và các nguyên liệu có nguồn gốc từ đậu phộng.',
    'SOY': 'Đậu nành và các nguyên liệu có nguồn gốc từ đậu nành.',
    'MILK': 'Sữa và các nguyên liệu có nguồn gốc từ sữa, gồm cả lactose.',
    'TREE_NUT': 'Các loại hạt cây và sản phẩm từ hạt cây.',
    'CELERY': 'Cần tây và cần tây củ.',
    'MUSTARD': 'Hạt mù tạt và mù tạt chế biến.',
    'SESAME': 'Hạt mè và dầu mè.',
    'SULPHITES': 'Vượt ngưỡng thường được quy định cho thực phẩm.',
    'LUPIN': 'Bột đậu lupin và hạt lupin.',
    'MOLLUSCS': 'Vẹm, mực, ốc và các loài tương tự.',
  };
}
