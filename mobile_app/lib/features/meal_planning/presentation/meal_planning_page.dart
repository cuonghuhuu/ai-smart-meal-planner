import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/core/ui/wellness_components.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_controller.dart';
import 'package:smart_meal_planner/features/meal_planning/application/meal_planning_prerequisites.dart';
import 'package:smart_meal_planner/features/meal_planning/data/meal_plan_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

String mealSlotLabel(MealSlotCode slot) => switch (slot) {
  MealSlotCode.breakfast => AppStrings.mealPlanBreakfast,
  MealSlotCode.morningSnack => AppStrings.mealPlanMorningSnack,
  MealSlotCode.lunch => AppStrings.mealPlanLunch,
  MealSlotCode.afternoonSnack => AppStrings.mealPlanAfternoonSnack,
  MealSlotCode.dinner => AppStrings.mealPlanDinner,
  MealSlotCode.eveningSnack => AppStrings.mealPlanEveningSnack,
};

IconData mealSlotIcon(MealSlotCode slot) => switch (slot) {
  MealSlotCode.breakfast => Icons.wb_sunny_outlined,
  MealSlotCode.morningSnack ||
  MealSlotCode.afternoonSnack => Icons.local_cafe_outlined,
  MealSlotCode.lunch => Icons.lunch_dining_outlined,
  MealSlotCode.dinner => Icons.dinner_dining_outlined,
  MealSlotCode.eveningSnack => Icons.nights_stay_outlined,
};

String unfilledReasonLabel(UnfilledSlotReasonCode reason) => switch (reason) {
  UnfilledSlotReasonCode.noEligibleRecipe =>
    AppStrings.mealPlanNoEligibleRecipe,
  UnfilledSlotReasonCode.hardConstraintConflict =>
    AppStrings.mealPlanHardConstraintConflict,
  UnfilledSlotReasonCode.unsupportedHardConstraint =>
    AppStrings.mealPlanUnsupportedConstraint,
  UnfilledSlotReasonCode.pantryInfeasible =>
    AppStrings.mealPlanPantryInfeasible,
  UnfilledSlotReasonCode.nutritionInfeasible =>
    AppStrings.mealPlanNutritionInfeasible,
  UnfilledSlotReasonCode.searchLimitReached => AppStrings.mealPlanSearchLimit,
};

class MealPlanningPage extends StatefulWidget {
  const MealPlanningPage({
    super.key,
    required this.sessionController,
    required this.mealPlanningController,
  });

  final SessionController sessionController;
  final MealPlanningController mealPlanningController;

  @override
  State<MealPlanningPage> createState() => _MealPlanningPageState();
}

class _MealPlanningPageState extends State<MealPlanningPage> {
  final _formKey = GlobalKey<FormState>();
  final _servings = TextEditingController(text: '2');
  final _maxMinutes = TextEditingController();
  final _slots = <MealSlotCode>{
    MealSlotCode.breakfast,
    MealSlotCode.lunch,
    MealSlotCode.dinner,
  };
  DateTime _startDate = DateUtils.dateOnly(DateTime.now());
  int _days = 7;
  String? _slotError;

  MealPlanningController get _controller => widget.mealPlanningController;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onStateChanged);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _controller.checkPrerequisites();
    });
  }

  @override
  void dispose() {
    _controller.removeListener(_onStateChanged);
    _servings.dispose();
    _maxMinutes.dispose();
    super.dispose();
  }

  void _onStateChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _pickStartDate() async {
    final today = DateUtils.dateOnly(DateTime.now());
    final selected = await showDatePicker(
      context: context,
      initialDate: _startDate.isBefore(today) ? today : _startDate,
      firstDate: today,
      lastDate: DateTime(today.year + 2, today.month, today.day),
    );
    if (selected != null && mounted) setState(() => _startDate = selected);
  }

  Future<void> _submit() async {
    final state = _controller.state;
    if (state.isBusy || state.pendingMealPlanPublicId != null) return;
    final validFields = _formKey.currentState!.validate();
    setState(() {
      _slotError = _slots.isEmpty ? AppStrings.mealPlanSlotsRequired : null;
    });
    if (!validFields || _slots.isEmpty) return;
    await _controller.generate(
      MealPlanGenerationRequest(
        startDate: _startDate,
        days: _days,
        requestedMealSlots: MealSlotCode.values.where(_slots.contains).toList(),
        defaultServings: _parseServings(_servings.text)!,
        maxMinutesPerMeal: _maxMinutes.text.trim().isEmpty
            ? null
            : int.parse(_maxMinutes.text.trim()),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 5,
    content: SingleChildScrollView(
      key: const ValueKey('meal-planning-scroll'),
      child: ResponsiveContent(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const PageIntro(
              key: ValueKey('meal-planning-title'),
              eyebrow: AppStrings.planWorkspaceEyebrow,
              title: AppStrings.mealPlanning,
              subtitle: AppStrings.mealPlanningSubtitle,
              icon: Icons.calendar_month_outlined,
            ),
            _buildForm(),
            const SizedBox(height: 24),
            _buildOutcome(),
          ],
        ),
      ),
    ),
  );

  Widget _buildForm() {
    final state = _controller.state;
    final enabled = !state.isBusy && state.pendingMealPlanPublicId == null;
    return Form(
      key: _formKey,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final configuration = SectionSurface(
            title: AppStrings.mealPlanConfigurationTitle,
            subtitle: AppStrings.mealPlanConfigurationSubtitle,
            icon: Icons.tune_rounded,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  AppStrings.mealPlanScheduleTitle,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                LayoutBuilder(
                  builder: (context, width) {
                    final date = OutlinedButton.icon(
                      key: const ValueKey('meal-plan-start-date'),
                      onPressed: enabled ? _pickStartDate : null,
                      icon: const Icon(Icons.calendar_today_outlined),
                      label: Text(
                        '${AppStrings.mealPlanStartDate}: ${formatMealPlanDate(_startDate)}',
                      ),
                    );
                    final days = DropdownButtonFormField<int>(
                      key: const ValueKey('meal-plan-days'),
                      initialValue: _days,
                      decoration: const InputDecoration(
                        labelText: AppStrings.mealPlanDays,
                      ),
                      items: [
                        for (var day = 1; day <= 7; day++)
                          DropdownMenuItem(value: day, child: Text('$day')),
                      ],
                      onChanged: enabled
                          ? (value) => setState(() => _days = value!)
                          : null,
                    );
                    if (width.maxWidth < 470) {
                      return Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [date, const SizedBox(height: 12), days],
                      );
                    }
                    return Row(
                      children: [
                        Expanded(child: date),
                        const SizedBox(width: 12),
                        SizedBox(width: 125, child: days),
                      ],
                    );
                  },
                ),
                const SizedBox(height: 24),
                Text(
                  AppStrings.mealPlanSlots,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                Wrap(
                  spacing: 9,
                  runSpacing: 9,
                  children: [
                    for (final slot in MealSlotCode.values)
                      FilterChip(
                        key: ValueKey('meal-plan-slot-${slot.wireValue}'),
                        avatar: Icon(mealSlotIcon(slot), size: 18),
                        label: Text(mealSlotLabel(slot)),
                        selected: _slots.contains(slot),
                        onSelected: enabled
                            ? (selected) => setState(() {
                                if (selected) {
                                  _slots.add(slot);
                                } else {
                                  _slots.remove(slot);
                                }
                                _slotError = _slots.isEmpty
                                    ? AppStrings.mealPlanSlotsRequired
                                    : null;
                              })
                            : null,
                      ),
                  ],
                ),
                if (_slotError != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      _slotError!,
                      key: const ValueKey('meal-plan-slots-error'),
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                const SizedBox(height: 24),
                Text(
                  AppStrings.mealPlanPracticalDetails,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                LayoutBuilder(
                  builder: (context, width) {
                    final servings = TextFormField(
                      key: const ValueKey('meal-plan-servings'),
                      controller: _servings,
                      enabled: enabled,
                      keyboardType: const TextInputType.numberWithOptions(
                        decimal: true,
                      ),
                      onChanged: (_) => setState(() {}),
                      decoration: const InputDecoration(
                        labelText: AppStrings.mealPlanServings,
                        prefixIcon: Icon(Icons.people_outline),
                      ),
                      validator: (value) => _parseServings(value ?? '') == null
                          ? AppStrings.mealPlanServingsInvalid
                          : null,
                    );
                    final minutes = TextFormField(
                      key: const ValueKey('meal-plan-max-minutes'),
                      controller: _maxMinutes,
                      enabled: enabled,
                      keyboardType: TextInputType.number,
                      onChanged: (_) => setState(() {}),
                      decoration: const InputDecoration(
                        labelText: AppStrings.mealPlanMaxMinutes,
                        prefixIcon: Icon(Icons.timer_outlined),
                      ),
                      validator: (value) => _validMaxMinutes(value ?? '')
                          ? null
                          : AppStrings.mealPlanMaxMinutesInvalid,
                    );
                    if (width.maxWidth < 470) {
                      return Column(
                        children: [
                          servings,
                          const SizedBox(height: 12),
                          minutes,
                        ],
                      );
                    }
                    return Row(
                      children: [
                        Expanded(child: servings),
                        const SizedBox(width: 12),
                        Expanded(child: minutes),
                      ],
                    );
                  },
                ),
              ],
            ),
          );
          final summary = SectionSurface(
            title: AppStrings.mealPlanSummaryTitle,
            icon: Icons.assignment_outlined,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _summaryLine(
                  context,
                  Icons.calendar_today_outlined,
                  AppStrings.mealPlanStartDate,
                  formatMealPlanDate(_startDate),
                ),
                _summaryLine(
                  context,
                  Icons.date_range_outlined,
                  AppStrings.mealPlanDays,
                  '$_days',
                ),
                _summaryLine(
                  context,
                  Icons.restaurant_outlined,
                  AppStrings.mealPlanSlots,
                  '${_slots.length}',
                ),
                _summaryLine(
                  context,
                  Icons.people_outline,
                  AppStrings.mealPlanServings,
                  _servings.text,
                ),
                _summaryLine(
                  context,
                  Icons.timer_outlined,
                  AppStrings.mealPlanMaxMinutes,
                  _maxMinutes.text.trim().isEmpty
                      ? '—'
                      : _maxMinutes.text.trim(),
                ),
                const SizedBox(height: 16),
                FilledButton.icon(
                  key: const ValueKey('meal-plan-generate'),
                  onPressed: enabled ? _submit : null,
                  icon: const Icon(Icons.auto_awesome_rounded),
                  label: const Text(AppStrings.mealPlanGenerate),
                ),
              ],
            ),
          );
          if (constraints.maxWidth < 800) {
            return Column(
              children: [configuration, const SizedBox(height: 16), summary],
            );
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(flex: 3, child: configuration),
              const SizedBox(width: 20),
              Expanded(flex: 2, child: summary),
            ],
          );
        },
      ),
    );
  }

  Widget _summaryLine(
    BuildContext context,
    IconData icon,
    String label,
    String value,
  ) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 8),
    child: Row(
      children: [
        Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 10),
        Expanded(
          child: Text(label, style: Theme.of(context).textTheme.bodySmall),
        ),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
      ],
    ),
  );

  Widget _buildOutcome() {
    final state = _controller.state;
    return switch (state.status) {
      MealPlanningStatus.idle => const SizedBox.shrink(),
      MealPlanningStatus.checkingPrerequisites => const _ProgressMessage(
        'Đang kiểm tra hồ sơ, mục tiêu và dữ liệu công thức...',
      ),
      MealPlanningStatus.missingPrerequisite => _prerequisiteMessage(
        state.prerequisiteIssue!,
      ),
      MealPlanningStatus.generating => const _ProgressMessage(
        AppStrings.mealPlanGenerating,
      ),
      MealPlanningStatus.loadingPlan => const _ProgressMessage(
        AppStrings.mealPlanLoading,
      ),
      MealPlanningStatus.infeasible => const _MessageCard(
        key: ValueKey('meal-plan-infeasible'),
        message: AppStrings.mealPlanInfeasible,
      ),
      MealPlanningStatus.error => _MessageCard(
        key: const ValueKey('meal-plan-error'),
        message: state.canRetryLoad
            ? AppStrings.mealPlanReadFailed
            : state.errorMessage ?? AppStrings.mealPlanRequestFailed,
        detail: state.canRetryLoad ? state.errorMessage : null,
        action: state.canRetryLoad
            ? FilledButton(
                key: const ValueKey('meal-plan-retry-load'),
                onPressed: _controller.retryLoad,
                child: const Text(AppStrings.mealPlanRetryLoading),
              )
            : FilledButton(
                onPressed: _controller.checkPrerequisites,
                child: const Text('Kiểm tra lại'),
              ),
      ),
      MealPlanningStatus.loaded => Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _PlanResult(plan: state.plan!),
          const SizedBox(height: 24),
          _ShoppingListSection(state: state, controller: _controller),
        ],
      ),
    };
  }

  Widget _prerequisiteMessage(MealPlanningPrerequisiteIssue issue) =>
      switch (issue) {
        MealPlanningPrerequisiteIssue.profile => _MessageCard(
          key: const ValueKey('meal-plan-needs-profile'),
          message: 'Hoàn thiện hồ sơ trước khi lập thực đơn.',
          action: FilledButton(
            onPressed: () => context.go('/profile'),
            child: const Text('Hoàn thiện hồ sơ'),
          ),
        ),
        MealPlanningPrerequisiteIssue.measurement => _MessageCard(
          key: const ValueKey('meal-plan-needs-measurement'),
          message: 'Ghi cân nặng mới nhất để tính mục tiêu dinh dưỡng.',
          action: FilledButton(
            onPressed: () => context.go('/measurements'),
            child: const Text('Thêm số đo'),
          ),
        ),
        MealPlanningPrerequisiteIssue.nutritionTarget => _MessageCard(
          key: const ValueKey('meal-plan-needs-target'),
          message: 'Chưa có mục tiêu dinh dưỡng hiện tại.',
          action: FilledButton(
            onPressed: _controller.createCalculatedTarget,
            child: const Text('Tạo mục tiêu dinh dưỡng'),
          ),
        ),
        MealPlanningPrerequisiteIssue.recipes => const _MessageCard(
          key: ValueKey('meal-plan-needs-recipes'),
          message: 'Chưa có dữ liệu công thức. Hãy chạy lệnh import demo rồi kiểm tra lại.',
        ),
      };
}

double? _parseServings(String text) {
  final trimmed = text.trim();
  if (!RegExp(r'^\d{1,2}(?:[.,]\d{1,2})?$').hasMatch(trimmed)) return null;
  final value = double.tryParse(trimmed.replaceAll(',', '.'));
  return value != null && value > 0 && value <= 50 ? value : null;
}

bool _validMaxMinutes(String text) {
  final trimmed = text.trim();
  if (trimmed.isEmpty) return true;
  if (!RegExp(r'^\d{1,4}$').hasMatch(trimmed)) return false;
  final value = int.parse(trimmed);
  return value >= 1 && value <= 1440;
}

class _ProgressMessage extends StatelessWidget {
  const _ProgressMessage(this.message);
  final String message;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      const LinearProgressIndicator(),
      const SizedBox(height: 12),
      Text(message),
    ],
  );
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({
    super.key,
    required this.message,
    this.detail,
    this.action,
  });
  final String message;
  final String? detail;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(message),
          if (detail != null) ...[const SizedBox(height: 8), Text(detail!)],
          if (action != null) ...[const SizedBox(height: 12), action!],
        ],
      ),
    ),
  );
}

class _PlanResult extends StatelessWidget {
  const _PlanResult({required this.plan});
  final PersistedMealPlan plan;

  @override
  Widget build(BuildContext context) {
    final dates = <String>{
      for (final entry in plan.entries) formatMealPlanDate(entry.planDate),
      for (final slot in plan.unfilledSlots) formatMealPlanDate(slot.planDate),
    }.toList()..sort();
    return Column(
      key: const ValueKey('meal-plan-result'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Icon(
              plan.status == MealPlanGenerationStatus.degraded
                  ? Icons.info_outline_rounded
                  : Icons.check_circle_outline_rounded,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                plan.status == MealPlanGenerationStatus.degraded
                    ? AppStrings.mealPlanDegraded
                    : AppStrings.mealPlanSucceeded,
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          '${formatMealPlanDate(plan.startDate)} – ${formatMealPlanDate(plan.endDate)}',
        ),
        if (plan.status == MealPlanGenerationStatus.degraded)
          const Padding(
            padding: EdgeInsets.only(top: 8),
            child: Text(AppStrings.mealPlanDegradedHint),
          ),
        const SizedBox(height: 16),
        for (final date in dates)
          Padding(
            key: ValueKey('meal-plan-day-$date'),
            padding: const EdgeInsets.only(bottom: 14),
            child: SectionSurface(
              title: date,
              icon: Icons.today_outlined,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final entry in plan.entries.where(
                    (entry) => formatMealPlanDate(entry.planDate) == date,
                  ))
                    _planEntry(context, entry),
                  for (final slot in plan.unfilledSlots.where(
                    (slot) => formatMealPlanDate(slot.planDate) == date,
                  ))
                    _unfilledEntry(context, slot),
                ],
              ),
            ),
          ),
      ],
    );
  }

  Widget _planEntry(BuildContext context, MealPlanEntry entry) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        CircleAvatar(
          backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: Icon(mealSlotIcon(entry.mealSlotCode), size: 20),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                mealSlotLabel(entry.mealSlotCode),
                style: Theme.of(context).textTheme.labelMedium,
              ),
              TextButton(
                key: ValueKey('meal-plan-recipe-${entry.recipePublicId}'),
                onPressed: () =>
                    context.push('/recipes/${entry.recipePublicId}'),
                child: Text(entry.recipeTitle),
              ),
              Text(
                '${_servingsLabel(entry.servings)} ${AppStrings.recipeServings}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ],
    ),
  );

  Widget _unfilledEntry(
    BuildContext context,
    MealPlanUnfilledSlot slot,
  ) => Container(
    margin: const EdgeInsets.only(bottom: 10),
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.errorContainer.withValues(alpha: .5),
      borderRadius: BorderRadius.circular(16),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(Icons.info_outline_rounded, size: 21),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${mealSlotLabel(slot.mealSlotCode)} · ${AppStrings.mealPlanUnfilled}',
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 4),
              Text(
                slot.explanation?.trim().isNotEmpty == true
                    ? slot.explanation!.trim()
                    : unfilledReasonLabel(slot.reasonCode),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _ShoppingListSection extends StatelessWidget {
  const _ShoppingListSection({required this.state, required this.controller});

  final MealPlanningState state;
  final MealPlanningController controller;

  @override
  Widget build(BuildContext context) => Column(
    key: state.shoppingListStatus == ShoppingListLoadStatus.loaded
        ? const ValueKey('shopping-list-result')
        : null,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        AppStrings.shoppingListTitle,
        style: Theme.of(context).textTheme.titleLarge,
      ),
      if (state.plan!.status == MealPlanGenerationStatus.degraded)
        const Padding(
          padding: EdgeInsets.only(top: 8),
          child: Text(AppStrings.shoppingListDegradedHint),
        ),
      const SizedBox(height: 12),
      switch (state.shoppingListStatus) {
        ShoppingListLoadStatus.idle => Align(
          alignment: Alignment.centerLeft,
          child: FilledButton.icon(
            key: const ValueKey('shopping-list-load'),
            onPressed: controller.loadShoppingList,
            icon: const Icon(Icons.shopping_cart_outlined),
            label: const Text(AppStrings.shoppingListLoad),
          ),
        ),
        ShoppingListLoadStatus.loading => Row(
          key: const ValueKey('shopping-list-loading'),
          children: const [
            SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            SizedBox(width: 12),
            Flexible(child: Text(AppStrings.shoppingListLoading)),
          ],
        ),
        ShoppingListLoadStatus.error => _MessageCard(
          key: const ValueKey('shopping-list-error'),
          message: AppStrings.shoppingListLoadFailed,
          detail: state.shoppingListErrorMessage,
          action: FilledButton(
            key: const ValueKey('shopping-list-retry'),
            onPressed: controller.retryShoppingList,
            child: const Text(AppStrings.shoppingListRetry),
          ),
        ),
        ShoppingListLoadStatus.loaded => _ShoppingListItems(
          shoppingList: state.shoppingList!,
        ),
      },
    ],
  );
}

class _ShoppingListItems extends StatelessWidget {
  const _ShoppingListItems({required this.shoppingList});

  final MealPlanShoppingList shoppingList;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      if (shoppingList.items.isEmpty)
        const Text(AppStrings.shoppingListNoQuantifiedItems),
      for (final item in shoppingList.items)
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.ingredientDisplayName,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 8),
                Text(
                  '${AppStrings.shoppingListRequired}: '
                  '${_shoppingQuantity(item.requiredQuantity, item.unitCode)}',
                ),
                Text(
                  '${AppStrings.shoppingListPantryCovered}: '
                  '${_shoppingQuantity(item.pantryCoveredQuantity, item.unitCode)}',
                ),
                const SizedBox(height: 4),
                Text(
                  '${AppStrings.shoppingListToBuy}: '
                  '${_shoppingQuantity(item.quantityToBuy, item.unitCode)}',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
      if (shoppingList.unquantifiedItems.isNotEmpty) ...[
        const SizedBox(height: 16),
        Text(
          AppStrings.shoppingListUnquantifiedTitle,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        for (final item in shoppingList.unquantifiedItems)
          Card(
            child: ListTile(
              title: Text(item.ingredientDisplayName),
              subtitle: const Text(AppStrings.shoppingListAsNeeded),
            ),
          ),
      ],
    ],
  );
}

String _shoppingQuantity(double value, String unitCode) =>
    '${value.toStringAsFixed(4).replaceFirst(RegExp(r'0+$'), '').replaceFirst(RegExp(r'\.$'), '')} $unitCode';

String _servingsLabel(double value) => value == value.roundToDouble()
    ? value.toStringAsFixed(0)
    : value.toString();
