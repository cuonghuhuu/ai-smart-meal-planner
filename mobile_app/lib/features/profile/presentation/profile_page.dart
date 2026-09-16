import 'package:flutter/material.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/profile/application/profile_controller.dart';
import 'package:smart_meal_planner/features/profile/application/profile_validation.dart';
import 'package:smart_meal_planner/features/profile/data/profile_models.dart';

const _sexOptions = <String>['FEMALE', 'MALE', 'OTHER', 'PREFER_NOT_TO_SAY'];

class ProfilePage extends StatefulWidget {
  const ProfilePage({
    super.key,
    required this.sessionController,
    required this.profileController,
  });

  final SessionController sessionController;
  final ProfileController profileController;

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final _formKey = GlobalKey<FormState>();
  final _birthDate = TextEditingController();
  final _height = TextEditingController();
  final _targetWeight = TextEditingController();
  final _weeklyChange = TextEditingController();
  final _householdSize = TextEditingController();
  final _maxCookMinutes = TextEditingController();
  final _notes = TextEditingController();
  String? _sex;
  String? _activityLevel;
  String? _nutritionGoal;
  int _appliedRevision = -1;

  ProfileController get _controller => widget.profileController;

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onControllerChanged);
    _controller.load();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _birthDate.dispose();
    _height.dispose();
    _targetWeight.dispose();
    _weeklyChange.dispose();
    _householdSize.dispose();
    _maxCookMinutes.dispose();
    _notes.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    final state = _controller.state;
    if (state.isLoaded && state.revision != _appliedRevision) {
      _applyDraft(state.draft);
      _appliedRevision = state.revision;
    }
    if (mounted) {
      setState(() {});
    }
  }

  void _applyDraft(ProfileDraft draft) {
    _birthDate.text = _formatDate(draft.birthDate);
    _height.text = _formatNumber(draft.heightCm);
    _targetWeight.text = _formatNumber(draft.targetWeightKg);
    _weeklyChange.text = _formatNumber(draft.weeklyChangeKg);
    _householdSize.text = draft.householdSize.toString();
    _maxCookMinutes.text = draft.maxCookMinutes?.toString() ?? '';
    _notes.text = draft.notes ?? '';
    _sex = _sexOptions.contains(draft.sex) ? draft.sex : null;
    _activityLevel =
        _controller.state.activityLevels.any(
          (item) => item.code == draft.activityLevel,
        )
        ? draft.activityLevel
        : null;
    _nutritionGoal =
        _controller.state.nutritionGoals.any(
          (item) => item.code == draft.nutritionGoal,
        )
        ? draft.nutritionGoal
        : null;
  }

  Future<void> _chooseBirthDate() async {
    final today = DateTime.now();
    final current = _parseDate(_birthDate.text);
    final selected = await showDatePicker(
      context: context,
      initialDate: current != null && !current.isAfter(today)
          ? current
          : DateTime(today.year - 18, today.month, today.day),
      firstDate: DateTime(1900, 1, 2),
      lastDate: today,
    );
    if (selected != null) {
      _birthDate.text = _formatDate(selected);
    }
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    final draft = ProfileDraft(
      birthDate: _parseDate(_birthDate.text),
      sex: _sex,
      heightCm: double.tryParse(_height.text.trim()),
      activityLevel: _activityLevel,
      nutritionGoal: _nutritionGoal,
      targetWeightKg: double.tryParse(_targetWeight.text.trim()),
      weeklyChangeKg: _optionalDouble(_weeklyChange.text),
      householdSize: int.tryParse(_householdSize.text.trim()) ?? 0,
      maxCookMinutes: int.tryParse(_maxCookMinutes.text.trim()),
      notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
      version: _controller.state.draft.version,
    );
    await _controller.save(draft);
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 2,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.state;
    if (state.status == ProfileStatus.loading && !state.hasEditableProfile) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.status == ProfileStatus.error && !state.hasEditableProfile) {
      return _LoadError(
        message: state.errorMessage ?? 'The profile could not be loaded.',
        onRetry: _controller.reload,
      );
    }

    final saving = state.status == ProfileStatus.saving;
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 960),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    'Profile',
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    state.isNewProfile
                        ? 'Complete your profile to personalize your meal plans.'
                        : 'Keep your profile details up to date.',
                  ),
                  const SizedBox(height: 20),
                  if (state.saveMessage != null)
                    _MessageBanner(message: state.saveMessage!, success: true),
                  if (state.errorMessage != null)
                    _MessageBanner(
                      message: state.errorMessage!,
                      action: state.status == ProfileStatus.conflict
                          ? TextButton(
                              onPressed: _controller.reload,
                              child: const Text('Reload'),
                            )
                          : null,
                    ),
                  if (state.errorMessage != null) const SizedBox(height: 12),
                  LayoutBuilder(
                    builder: (context, constraints) {
                      final columns = constraints.maxWidth >= 680;
                      final fields = <Widget>[
                        _birthDateField(),
                        _sexField(),
                        _activityField(state),
                        _nutritionGoalField(state),
                        _numberField(
                          controller: _height,
                          label: 'Height (cm)',
                          keyboardType: const TextInputType.numberWithOptions(
                            decimal: true,
                          ),
                          validator: (value) => _numberError(
                            value,
                            (number) => number > 30 && number < 300,
                            'Height must be greater than 30 and less than 300 cm.',
                          ),
                        ),
                        _numberField(
                          controller: _targetWeight,
                          label: 'Target weight (kg)',
                          keyboardType: const TextInputType.numberWithOptions(
                            decimal: true,
                          ),
                          validator: (value) => _numberError(
                            value,
                            (number) => number > 2 && number < 700,
                            'Target weight must be greater than 2 and less than 700 kg.',
                          ),
                        ),
                        _numberField(
                          controller: _weeklyChange,
                          label: 'Weekly weight change goal (kg/week)',
                          helperText: 'Leave blank if you do not have a weekly weight-change goal.',
                          keyboardType: const TextInputType.numberWithOptions(
                            decimal: true,
                            signed: true,
                          ),
                          validator: ProfileValidation.validateWeeklyChangeText,
                        ),
                        _numberField(
                          controller: _householdSize,
                          label: 'Household size',
                          keyboardType: TextInputType.number,
                          required: true,
                          validator: (value) {
                            final number = int.tryParse(value?.trim() ?? '');
                            return number != null && number >= 1
                                ? null
                                : 'Household size must be at least 1.';
                          },
                        ),
                        _numberField(
                          controller: _maxCookMinutes,
                          label: 'Max cooking time (minutes)',
                          keyboardType: TextInputType.number,
                          validator: (value) {
                            if (value == null || value.trim().isEmpty) {
                              return null;
                            }
                            final number = int.tryParse(value.trim());
                            return number != null &&
                                    number >= 1 &&
                                    number <= 1440
                                ? null
                                : 'Cooking time must be between 1 and 1440 minutes.';
                          },
                        ),
                      ];
                      if (!columns) {
                        return Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            for (final field in fields) ...[
                              field,
                              const SizedBox(height: 16),
                            ],
                          ],
                        );
                      }
                      return GridView.count(
                        crossAxisCount: 2,
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        crossAxisSpacing: 16,
                        mainAxisSpacing: 16,
                        childAspectRatio: 4.2,
                        children: fields,
                      );
                    },
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _notes,
                    minLines: 4,
                    maxLines: 6,
                    maxLength: 500,
                    decoration: const InputDecoration(
                      labelText: 'Notes',
                      alignLabelWithHint: true,
                      border: OutlineInputBorder(),
                    ),
                    validator: (value) => value != null && value.length > 500
                        ? 'Notes must be 500 characters or fewer.'
                        : null,
                  ),
                  const SizedBox(height: 8),
                  Align(
                    alignment: Alignment.centerRight,
                    child: FilledButton.icon(
                      onPressed: saving ? null : _save,
                      icon: saving
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.save),
                      label: Text(saving ? 'Saving…' : 'Save profile'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _birthDateField() => TextFormField(
    controller: _birthDate,
    readOnly: true,
    onTap: _chooseBirthDate,
    decoration: const InputDecoration(
      labelText: 'Birth date',
      suffixIcon: Icon(Icons.calendar_today),
    ),
    validator: (value) {
      if (value == null || value.trim().isEmpty) return null;
      final date = _parseDate(value);
      if (date == null) return 'Enter a valid date.';
      final errors = ProfileValidation.validate(
        _controller.state.draft.copyWith(birthDate: date),
      );
      return errors['birthDate'];
    },
  );

  Widget _sexField() => DropdownButtonFormField<String>(
    key: ValueKey('sex-$_sex'),
    initialValue: _sex,
    decoration: const InputDecoration(labelText: 'Sex'),
    items: [
      for (final value in _sexOptions)
        DropdownMenuItem(value: value, child: Text(_sexLabel(value))),
    ],
    onChanged: (value) => setState(() => _sex = value),
  );

  Widget _activityField(ProfileState state) => DropdownButtonFormField<String>(
    key: ValueKey('activity-$_activityLevel'),
    initialValue: _activityLevel,
    decoration: const InputDecoration(labelText: 'Activity level'),
    items: [
      for (final item in state.activityLevels)
        DropdownMenuItem(value: item.code, child: Text(item.displayName)),
    ],
    onChanged: (value) => setState(() => _activityLevel = value),
  );

  Widget _nutritionGoalField(ProfileState state) =>
      DropdownButtonFormField<String>(
        key: ValueKey('goal-$_nutritionGoal'),
        initialValue: _nutritionGoal,
        decoration: const InputDecoration(labelText: 'Nutrition goal'),
        items: [
          for (final item in state.nutritionGoals)
            DropdownMenuItem(value: item.code, child: Text(item.displayName)),
        ],
        onChanged: (value) => setState(() => _nutritionGoal = value),
      );

  Widget _numberField({
    required TextEditingController controller,
    required String label,
    required TextInputType keyboardType,
    required FormFieldValidator<String> validator,
    String? helperText,
    bool required = false,
  }) => TextFormField(
    controller: controller,
    keyboardType: keyboardType,
    decoration: InputDecoration(labelText: label, helperText: helperText),
    validator: (value) {
      final text = value?.trim() ?? '';
      if (text.isEmpty) {
        return required ? '$label is required.' : null;
      }
      return validator(text);
    },
  );

  String? _numberError(
    String? value,
    bool Function(double number) valid,
    String message,
  ) {
    if (value == null || value.trim().isEmpty) return null;
    final number = double.tryParse(value.trim());
    return number != null && number.isFinite && valid(number) ? null : message;
  }
}

double? _optionalDouble(String value) {
  final text = value.trim();
  return text.isEmpty ? null : double.tryParse(text);
}

class _LoadError extends StatelessWidget {
  const _LoadError({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          FilledButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    ),
  );
}

class _MessageBanner extends StatelessWidget {
  const _MessageBanner({
    required this.message,
    this.success = false,
    this.action,
  });
  final String message;
  final bool success;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Card(
    color: success
        ? Theme.of(context).colorScheme.secondaryContainer
        : Theme.of(context).colorScheme.errorContainer,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      child: Row(
        children: [
          Expanded(child: Text(message)),
          ?action,
        ],
      ),
    ),
  );
}

String _sexLabel(String value) => switch (value) {
  'FEMALE' => 'Female',
  'MALE' => 'Male',
  'OTHER' => 'Other',
  'PREFER_NOT_TO_SAY' => 'Prefer not to say',
  _ => value,
};

DateTime? _parseDate(String value) {
  final match = RegExp(r'^([0-9]{4})-([0-9]{2})-([0-9]{2})$')
      .firstMatch(value.trim());
  if (match == null) return null;
  final date = DateTime.tryParse(value.trim());
  return date == null || _formatDate(date) != value.trim() ? null : date;
}

String _formatDate(DateTime? value) => value == null
    ? ''
    : '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';

String _formatNumber(double? value) {
  if (value == null) return '';
  return value == value.roundToDouble()
      ? value.toInt().toString()
      : value.toString();
}
