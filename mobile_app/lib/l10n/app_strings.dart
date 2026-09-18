/// User-facing Vietnamese copy for the Flutter application.
///
/// API paths, JSON keys, backend codes, and enum wire values deliberately do
/// not belong in this presentation-only string layer.
final class AppStrings {
  const AppStrings._();

  static const productName = 'AI Smart Meal Planner';

  // Shared actions and navigation.
  static const foods = 'Thực phẩm';
  static const ingredients = 'Nguyên liệu';
  static const ingredientsComingSoon = 'Nguyên liệu (sắp có)';
  static const profile = 'Hồ sơ cá nhân';
  static const preferences = 'Sở thích ăn uống';
  static const signOut = 'Đăng xuất';
  static const save = 'Lưu';
  static const reload = 'Tải lại';
  static const retry = 'Thử lại';
  static const cancel = 'Hủy';
  static const loading = 'Đang tải...';
  static const loadingPreferences = 'Đang tải sở thích ăn uống…';

  // Catalog.
  static const catalogFoodsSubtitle =
      'Tra cứu thành phần dinh dưỡng của các thực phẩm Việt Nam.';
  static const catalogIngredientsSubtitle =
      'Các nguyên liệu dùng làm danh tính chuẩn cho công thức và kế hoạch bữa ăn.';
  static const catalogSearchHint = 'Tìm theo tên thực phẩm hoặc nguyên liệu';
  static const catalogSearchSubmit = 'Tìm kiếm';
  static const catalogAllCategories = 'Tất cả nhóm';
  static const catalogUncategorized = 'Chưa phân loại';
  static const catalogUnknownValue = 'Chưa xác định';
  static const catalogNoFoods = 'Không tìm thấy thực phẩm phù hợp.';
  static const catalogNoIngredients = 'Không tìm thấy nguyên liệu phù hợp.';
  static const catalogLoadFailed =
      'Không thể tải danh mục. Vui lòng thử lại.';
  static const catalogDetailLoadFailed =
      'Không thể tải thông tin chi tiết. Vui lòng thử lại.';
  static const catalogLoadMoreFailed =
      'Không thể tải thêm dữ liệu. Vui lòng thử lại.';
  static const catalogUnavailable = 'Danh mục hiện không khả dụng.';
  static const catalogCode = 'Mã';
  static const catalogDescription = 'Mô tả';
  static const catalogNutrition = 'Dinh dưỡng';
  static const catalogServings = 'Khẩu phần';
  static const catalogSource = 'Nguồn dữ liệu';
  static const catalogRevision = 'Phiên bản dữ liệu';
  static const catalogNoNutrients = 'Chưa có dữ liệu dinh dưỡng.';
  static const catalogNoServings = 'Chưa có thông tin khẩu phần.';
  static const catalogNoSource = 'Chưa có thông tin nguồn.';
  static const catalogNutritionPer100g =
      'Giá trị dinh dưỡng trên 100 g phần ăn được';
  static const catalogNutritionBasis = 'Cơ sở tính';
  static const catalogUnit = 'Đơn vị';
  static const catalogAmount = 'Lượng';
  static const catalogDataQuality = 'Chất lượng dữ liệu';
  static const catalogBrand = 'Nhãn hiệu';
  static const catalogDefault = 'Mặc định';
  static const ingredientDefaultUnit = 'Đơn vị mặc định';
  static const ingredientAliases = 'Tên gọi khác';
  static const ingredientFoodMappings = 'Thực phẩm liên kết';
  static const ingredientAllergens = 'Chất gây dị ứng';
  static const ingredientConversions = 'Quy đổi đơn vị';
  static const ingredientNoAliases = 'Chưa có tên gọi khác.';
  static const ingredientNoFoodMappings = 'Chưa có thực phẩm liên kết.';
  static const ingredientNoAllergens = 'Chưa có thông tin chất gây dị ứng.';
  static const ingredientNoConversions = 'Chưa có quy đổi đơn vị.';
  static const ingredientPreparation = 'Trạng thái chế biến';
  static const ingredientYield = 'Hệ số hao hụt';
  static const ingredientPrimary = 'Nguyên liệu chính';
  static const ingredientViewFoodNutrition = 'Xem thông tin dinh dưỡng';
  static const ingredientShelfLife = 'Thời hạn bảo quản';
  static const ingredientPieceWeight = 'Khối lượng mỗi phần';
  static const ingredientStaple = 'Thực phẩm thiết yếu';
  static const catalogDays = 'ngày';
  static const catalogBack = 'Quay lại';

  static const catalogCategoryLabels = <String, String>{
    'BEVERAGES': 'Đồ uống',
    'GRAINS_BREAD': 'Bánh mì và ngũ cốc',
    'DAIRY_CHEESE': 'Phô mai',
    'DAIRY': 'Sữa và chế phẩm',
    'PROTEIN_EGG': 'Trứng',
    'FATS_OILS': 'Dầu và chất béo',
    'PROTEIN_SEAFOOD': 'Cá và hải sản',
    'FRUITS': 'Trái cây',
    'GRAINS': 'Ngũ cốc',
    'SEASONING_HERB': 'Rau thơm và gia vị',
    'VEG_LEAFY': 'Rau lá',
    'PROTEIN_LEGUME': 'Đậu',
    'PROTEIN_MEAT': 'Thịt',
    'DAIRY_MILK': 'Sữa',
    'PROTEIN_NUT': 'Hạt',
    'VEG_ALLIUM': 'Rau họ hành',
    'OTHER': 'Khác',
    'GRAINS_PASTA': 'Mì và pasta',
    'PROTEIN_POULTRY': 'Gia cầm',
    'PREPARED': 'Thực phẩm chế biến',
    'PROTEIN': 'Thực phẩm giàu đạm',
    'GRAINS_RICE': 'Gạo',
    'VEG_ROOT': 'Củ và rễ',
    'SEASONING_SAUCE': 'Nước xốt',
    'SEASONINGS': 'Gia vị',
    'VEGETABLES': 'Rau củ',
  };

  static const catalogNutrientLabels = <String, String>{
    'ENERGY': 'Năng lượng',
    'PROTEIN': 'Chất đạm',
    'WATER': 'Nước',
    'FAT_TOTAL': 'Chất béo tổng số',
    'CARBOHYDRATE': 'Carbohydrate',
    'CALCIUM': 'Canxi',
    'IRON': 'Sắt',
    'VITAMIN_C': 'Vitamin C',
    'VITAMIN_A': 'Vitamin A',
    'VITAMIN_D': 'Vitamin D',
  };

  static const catalogEnumLabels = <String, String>{
    'PER_100_G': 'Trên 100 g phần ăn được',
    'PER_100_ML': 'Trên 100 ml',
    'IMPORTED': 'Dữ liệu nhập khẩu',
    'CURATED': 'Dữ liệu được biên soạn',
    'USER_SUBMITTED': 'Người dùng cung cấp',
    'ANALYTICAL': 'Phân tích',
    'CALCULATED': 'Tính toán',
    'ESTIMATED': 'Ước tính/tham khảo',
    'RAW': 'Tươi/sống',
    'COOKED': 'Đã nấu chín',
    'DRIED': 'Đã sấy khô',
    'CANNED': 'Đóng hộp',
    'FROZEN': 'Đông lạnh',
    'UNSPECIFIED': 'Chưa phân loại trạng thái chế biến',
    'CONTAINS': 'Có chứa',
    'MAY_CONTAIN': 'Có thể chứa',
    'FREE_FROM': 'Không chứa',
    'MEASURED': 'Đo lường',
    'REFERENCE': 'Tham khảo',
  };

  // Auth.
  static const welcomeBack = 'Chào mừng bạn quay lại';
  static const signInSubtitle = 'Đăng nhập để tiếp tục lập kế hoạch bữa ăn.';
  static const passwordResetSuccess =
      'Đã đặt lại mật khẩu. Bạn có thể đăng nhập ngay.';
  static const email = 'Email';
  static const password = 'Mật khẩu';
  static const confirmPassword = 'Xác nhận mật khẩu';
  static const enterPassword = 'Nhập mật khẩu';
  static const signIn = 'Đăng nhập';
  static const forgotPassword = 'Quên mật khẩu?';
  static const createAccountLink = 'Tạo tài khoản';
  static const createYourAccount = 'Tạo tài khoản';
  static const registrationSubtitle =
      'Bạn cần xác minh email trước khi đăng nhập.';
  static const displayName = 'Tên hiển thị';
  static const createAccount = 'Tạo tài khoản';
  static const alreadyHaveAccount = 'Đã có tài khoản? Đăng nhập';
  static const verifyYourEmail = 'Xác minh email';
  static const verifyEmailSubtitle =
      'Mở liên kết xác minh trong email hoặc yêu cầu gửi lại liên kết.';
  static const invalidVerificationLink =
      'Liên kết xác minh không hợp lệ hoặc chưa đầy đủ.';
  static const emailVerified =
      'Email đã được xác minh. Bạn có thể đăng nhập ngay.';
  static const emailForNewLink = 'Email để nhận liên kết mới';
  static const resendVerificationEmail = 'Gửi lại email xác minh';
  static const backToSignIn = 'Quay lại đăng nhập';
  static const verificationEmailSent =
      'Nếu tài khoản cần xác minh, email mới đã được gửi.';
  static const resetYourPassword = 'Đặt lại mật khẩu';
  static const resetPasswordSubtitle =
      'Nhập email; nếu tài khoản phù hợp, chúng tôi sẽ gửi hướng dẫn đặt lại mật khẩu.';
  static const resetInstructionsSent =
      'Nếu email khớp với một tài khoản, hướng dẫn đặt lại mật khẩu đã được gửi.';
  static const sendResetInstructions = 'Gửi hướng dẫn đặt lại';
  static const chooseNewPassword = 'Chọn mật khẩu mới';
  static const passwordRequirements =
      'Mật khẩu phải có ít nhất 12 ký tự và không quá 72 byte UTF-8.';
  static const resetPassword = 'Đặt lại mật khẩu';
  static const passwordHelper = '12–72 byte UTF-8';
  static const showPassword = 'Hiện mật khẩu';
  static const hidePassword = 'Ẩn mật khẩu';

  // Profile.
  static const completeProfileSubtitle =
      'Hoàn tất hồ sơ để cá nhân hóa kế hoạch bữa ăn.';
  static const updateProfileSubtitle = 'Cập nhật thông tin hồ sơ của bạn.';
  static const birthDate = 'Ngày sinh';
  static const sex = 'Giới tính';
  static const female = 'Nữ';
  static const male = 'Nam';
  static const other = 'Khác';
  static const preferNotToSay = 'Không muốn cung cấp';
  static const heightCm = 'Chiều cao (cm)';
  static const activityLevel = 'Mức độ hoạt động';
  static const nutritionGoal = 'Mục tiêu dinh dưỡng';
  static const targetWeightKg = 'Cân nặng mục tiêu (kg)';
  static const weeklyChangeKg = 'Mục tiêu thay đổi cân nặng mỗi tuần (kg/tuần)';
  static const weeklyChangeHelper =
      'Để trống nếu bạn không đặt mục tiêu tăng hoặc giảm cân theo tuần.';
  static const householdSize = 'Số người trong hộ gia đình';
  static const maxCookingTime = 'Thời gian nấu tối đa (phút)';
  static const notes = 'Ghi chú';
  static const saveProfile = 'Lưu hồ sơ';
  static const saving = 'Đang lưu…';
  static const profileSaved = 'Đã lưu hồ sơ.';
  static const profileConflict =
      'Dữ liệu hồ sơ đã được thay đổi ở nơi khác. Vui lòng tải lại trước khi lưu.';
  static const profileValidationSummary =
      'Vui lòng kiểm tra các trường được đánh dấu.';
  static const invalidDate = 'Vui lòng nhập ngày hợp lệ.';

  // Preferences and allergens.
  static const preferencesSubtitle =
      'Chọn các sở thích ăn uống mà hệ thống sẽ sử dụng khi lập kế hoạch bữa ăn.';
  static const dietaryPreferences = 'Sở thích ăn uống';
  static const dietaryPreferencesDescription =
      'Chọn các sở thích ăn uống mà bạn muốn hệ thống tuân theo.';
  static const noDietaryPreferences = 'Hiện chưa có sở thích ăn uống nào.';
  static const exclusionaryPreference =
      'Sở thích này sẽ loại trừ các lựa chọn không phù hợp.';
  static const saveDietaryPreferences = 'Lưu sở thích ăn uống';
  static const dietaryPreferencesSaved = 'Đã lưu sở thích ăn uống.';
  static const allergens = 'Chất gây dị ứng';
  static const allergenSafety =
      'Các chất gây dị ứng đã chọn sẽ luôn bị loại khỏi gợi ý bữa ăn. '
      'Loại phản ứng và ghi chú chỉ dùng để mô tả thêm.';
  static const noAllergens = 'Hiện chưa có chất gây dị ứng nào.';
  static const reactionKind = 'Loại phản ứng';
  static const optionalNote = 'Ghi chú (không bắt buộc)';
  static const saveAllergens = 'Lưu thông tin dị ứng';
  static const allergensSaved = 'Đã lưu thông tin dị ứng.';

  // Disliked ingredients and personal avoidance preferences.
  static const dislikedIngredients = 'Nguyên liệu không thích / muốn tránh';
  static const dislikedIngredientsDescription =
      'Ghi lại nguyên liệu bạn không thích hoặc muốn tránh. Đây là sở thích ăn uống, không thay thế thông tin dị ứng.';
  static const dislikedIngredientsStored =
      'Các lựa chọn này được lưu để dùng cho việc lập kế hoạch bữa ăn sau này.';
  static const dislikedIngredientStrengthDescription =
      '“Không thích” thể hiện sở thích không muốn ăn; “Tránh dùng” là mức tránh mạnh hơn theo lựa chọn cá nhân.';
  static const dislikedIngredientsEmpty =
      'Bạn chưa thêm nguyên liệu nào vào danh sách này.';
  static const dislikedIngredientAdd = 'Thêm nguyên liệu';
  static const dislikedIngredientSave = 'Lưu nguyên liệu không thích';
  static const dislikedIngredientRemove = 'Xóa nguyên liệu';
  static const dislikedIngredientStrength = 'Mức độ ưu tiên tránh';
  static const dislikedIngredientNote = 'Ghi chú (không bắt buộc)';
  static const dislikedIngredientNoteTooLong =
      'Ghi chú nguyên liệu không được vượt quá 255 ký tự.';
  static const dislikedIngredientIdBlank =
      'Nguyên liệu không được thiếu mã định danh.';
  static const dislikedIngredientDuplicate =
      'Không được chọn trùng nguyên liệu.';
  static const dislikedIngredientDislike = 'Không thích';
  static const dislikedIngredientAvoid = 'Tránh dùng';
  static const dislikedIngredientsSaved =
      'Đã lưu danh sách nguyên liệu không thích.';
  static const dislikedIngredientsLoadFailed =
      'Không thể tải danh sách nguyên liệu. Vui lòng thử lại.';
  static const dislikedIngredientsSaveFailed =
      'Không thể lưu danh sách nguyên liệu. Vui lòng thử lại.';
  static const dislikedIngredientPickerTitle = 'Chọn nguyên liệu';
  static const dislikedIngredientPickerSearch = 'Tìm nguyên liệu';
  static const dislikedIngredientPickerNoResults =
      'Không tìm thấy nguyên liệu phù hợp.';
  static const dislikedIngredientPickerSelected = 'Đã chọn';
  static const dislikedIngredientPickerLoadFailed =
      'Không thể tải danh sách nguyên liệu để chọn. Vui lòng thử lại.';
  static const dislikedIngredientPickerLoadMoreFailed =
      'Không thể tải thêm nguyên liệu. Vui lòng thử lại.';
  static const dislikedIngredientPickerRetry = 'Thử lại';
  static const dislikedIngredientPickerClose = 'Đóng';
  static const dislikedIngredientStrengthLabels = <String, String>{
    'DISLIKE': dislikedIngredientDislike,
    'AVOID': dislikedIngredientAvoid,
  };

  // Body measurements.
  static const measurements = 'Số đo cơ thể';
  static const measurementsSubtitle =
      'Theo dõi cân nặng và các chỉ số cơ thể theo thời gian.';
  static const latestMeasurement = 'Số đo mới nhất';
  static const measurementHistory = 'Lịch sử số đo';
  static const noMeasurements = 'Bạn chưa có số đo nào.';
  static const addMeasurement = 'Thêm số đo';
  static const editMeasurement = 'Chỉnh sửa';
  static const saveMeasurement = 'Lưu số đo';
  static const updateMeasurement = 'Cập nhật số đo';
  static const measurementSaved = 'Đã lưu số đo.';
  static const measurementUpdated = 'Đã cập nhật số đo.';
  static const measurementLoading = 'Đang tải số đo…';
  static const measurementSaving = 'Đang lưu…';
  static const measuredDate = 'Ngày đo';
  static const weightKg = 'Cân nặng (kg)';
  static const bodyFatPercent = 'Tỷ lệ mỡ cơ thể (%)';
  static const waistCm = 'Vòng eo (cm)';
  static const source = 'Nguồn dữ liệu';
  static const userEntered = 'Người dùng nhập';
  static const imported = 'Đã nhập từ nguồn khác';
  static const corrected = 'Đã hiệu chỉnh';
  static const measurementUpsertHelper =
      'Nếu ngày này đã có số đo, dữ liệu hiện có sẽ được cập nhật.';
  static const measuredDateImmutable =
      'Ngày đo không thể thay đổi khi chỉnh sửa.';
  static const fromDate = 'Từ ngày';
  static const toDate = 'Đến ngày';
  static const applyFilter = 'Áp dụng';
  static const clearFilter = 'Xóa bộ lọc';
  static const loadMore = 'Xem thêm';
  static const measurementDateRangeRequired =
      'Vui lòng chọn cả ngày bắt đầu và ngày kết thúc.';
  static const measurementDateRangeInvalid =
      'Ngày bắt đầu không được sau ngày kết thúc.';
  static const measurementLoadFailed = 'Không thể tải số đo. Vui lòng thử lại.';
  static const measurementSaveFailed = 'Không thể lưu số đo. Vui lòng thử lại.';
  static const measurementsUnavailable = 'Số đo cơ thể hiện không khả dụng.';

  // Foundation and routing fallback pages.
  static const foundationReady = 'Nền tảng Flutter Web đã sẵn sàng.';
  static const runtimeConfiguration = 'Cấu hình khi chạy';
  static const environment = 'Môi trường';
  static const apiUrl = 'URL API';
  static const backendConnection = 'Kết nối máy chủ';
  static const checking = 'Đang kiểm tra…';
  static const connectedUp = 'Đã kết nối / ĐANG HOẠT ĐỘNG';
  static const backendUnavailable = 'Máy chủ không khả dụng hoặc đã xảy ra lỗi';
  static const restoringSession = 'Đang khôi phục phiên đăng nhập';
  static const profileUnavailable = 'Hồ sơ cá nhân hiện không khả dụng.';
  static const preferencesUnavailable = 'Sở thích ăn uống hiện không khả dụng.';
  static const pageNotFound = 'Không tìm thấy trang';
  static const goToFoods = 'Đến thực phẩm';
  static const foodsPlaceholder = 'Danh mục thực phẩm đang được hoàn thiện.';

  // Validation messages.
  static const enterEmail = 'Vui lòng nhập email.';
  static const validEmail = 'Vui lòng nhập email hợp lệ.';
  static const enterPasswordError = 'Vui lòng nhập mật khẩu.';
  static const displayNameRequired = 'Vui lòng nhập tên hiển thị.';
  static const displayNameTooLong =
      'Tên hiển thị không được vượt quá 100 ký tự.';
  static const passwordTooShort = 'Mật khẩu phải có ít nhất 12 ký tự.';
  static const passwordTooLong = 'Mật khẩu không được vượt quá 72 byte UTF-8.';
  static const birthDateTooEarly = 'Ngày sinh phải sau 01/01/1900.';
  static const birthDateInFuture = 'Ngày sinh không được ở tương lai.';
  static const heightInvalid =
      'Chiều cao phải lớn hơn 30 cm và nhỏ hơn 300 cm.';
  static const targetWeightInvalid =
      'Cân nặng mục tiêu phải lớn hơn 2 kg và nhỏ hơn 700 kg.';
  static const weeklyChangeInvalid =
      'Mức thay đổi cân nặng mỗi tuần phải lớn hơn -5 kg và nhỏ hơn 5 kg.';
  static const weeklyChangeInvalidWithUnit =
      'Mức thay đổi cân nặng mỗi tuần phải lớn hơn -5 kg và nhỏ hơn 5 kg.';
  static const householdSizeInvalid =
      'Số người trong hộ gia đình phải ít nhất là 1.';
  static const householdSizeRequired =
      'Vui lòng nhập số người trong hộ gia đình.';
  static const maxCookingTimeInvalid =
      'Thời gian nấu tối đa phải từ 1 đến 1440 phút.';
  static const notesTooLong = 'Ghi chú không được vượt quá 500 ký tự.';
  static const allergenNoteTooLong =
      'Ghi chú về chất gây dị ứng không được vượt quá 255 ký tự.';
  static const dietaryCodeBlank = 'Mã sở thích ăn uống không được để trống.';
  static const dietaryDuplicate = 'Không được chọn trùng sở thích ăn uống.';
  static const allergenCodeBlank = 'Mã chất gây dị ứng không được để trống.';
  static const allergenDuplicate = 'Không được chọn trùng chất gây dị ứng.';
  static const measuredDateRequired = 'Vui lòng chọn ngày đo.';
  static const measuredDateInFuture = 'Ngày đo không được ở tương lai.';
  static const weightRequired = 'Vui lòng nhập cân nặng.';
  static const weightInvalid = 'Cân nặng phải lớn hơn 2 kg và nhỏ hơn 700 kg.';
  static const weightPrecision =
      'Cân nặng chỉ được có tối đa 2 chữ số thập phân.';
  static const bodyFatInvalid = 'Tỷ lệ mỡ cơ thể phải từ 0% đến 100%.';
  static const bodyFatPrecision =
      'Tỷ lệ mỡ cơ thể chỉ được có tối đa 1 chữ số thập phân.';
  static const waistInvalid = 'Vòng eo phải lớn hơn 10 cm và nhỏ hơn 400 cm.';
  static const waistPrecision =
      'Vòng eo chỉ được có tối đa 1 chữ số thập phân.';
  static const measurementNoteTooLong =
      'Ghi chú không được vượt quá 255 ký tự.';
  static const measurementInvalidNumber = 'Vui lòng nhập một số hợp lệ.';

  // Safe, user-facing error copy. Backend problem details are intentionally
  // not returned directly to the UI.
  static const incorrectCredentials = 'Email hoặc mật khẩu không chính xác.';
  static const emailAlreadyRegistered = 'Địa chỉ email này đã được đăng ký.';
  static const sessionUserUnavailable =
      'Đã đăng nhập nhưng không thể tải thông tin tài khoản. Vui lòng thử lại.';
  static const invalidResetLink =
      'Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.';
  static const invalidPassword =
      'Mật khẩu phải có ít nhất 12 ký tự và không quá 72 byte UTF-8.';
  static const requestFailed = 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.';
  static const serviceUnavailable =
      'Dịch vụ không thể hoàn tất yêu cầu. Vui lòng thử lại.';
  static const unableToReachService =
      'Không thể kết nối đến dịch vụ. Vui lòng thử lại.';
  static const genericError = 'Đã xảy ra lỗi. Vui lòng thử lại.';
  static const profileLoadSaveFailed =
      'Không thể tải hoặc lưu hồ sơ. Vui lòng thử lại.';
  static const preferencesLoadSaveFailed =
      'Không thể tải hoặc lưu sở thích ăn uống. Vui lòng thử lại.';
}
