import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_controller.dart';
import 'package:smart_meal_planner/features/measurements/application/measurements_validation.dart';
import 'package:smart_meal_planner/features/measurements/data/measurement_models.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';
import 'package:smart_meal_planner/l10n/reference_localizations.dart';

class MeasurementsPage extends StatefulWidget {
  const MeasurementsPage({
    super.key,
    required this.sessionController,
    required this.measurementsController,
  });

  final SessionController sessionController;
  final MeasurementsController measurementsController;

  @override
  State<MeasurementsPage> createState() => _MeasurementsPageState();
}

class _MeasurementsPageState extends State<MeasurementsPage> {
  MeasurementsController get _controller => widget.measurementsController;

  bool _formOpen = false;
  BodyMeasurement? _editingMeasurement;
  DateTime? _formDate;
  DateTime? _filterFrom;
  DateTime? _filterTo;
  late final TextEditingController _filterFromController;
  late final TextEditingController _filterToController;

  @override
  void initState() {
    super.initState();
    _filterFromController = TextEditingController();
    _filterToController = TextEditingController();
    _controller.addListener(_onControllerChanged);
    _controller.load();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _filterFromController.dispose();
    _filterToController.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    if (!mounted) {
      return;
    }
    if (_controller.state.saveMessage != null && _formOpen) {
      _closeForm();
    }
    setState(() {});
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 4,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.state;
    if (!state.hasLoadedData && state.status == MeasurementsStatus.loading) {
      return Center(
        child: Semantics(
          label: AppStrings.measurementLoading,
          child: const CircularProgressIndicator(),
        ),
      );
    }
    if (!state.hasLoadedData && state.status == MeasurementsStatus.error) {
      return _MeasurementsLoadError(
        message: state.errorMessage ?? AppStrings.measurementLoadFailed,
        onRetry: _controller.reload,
      );
    }

    final busy = state.isBusy;
    return SingleChildScrollView(
      key: const ValueKey('measurements-scroll'),
      child: ResponsiveContent(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              AppStrings.measurements,
              key: const ValueKey('measurements-title'),
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.measurementsSubtitle),
            if (state.status == MeasurementsStatus.loading ||
                state.status == MeasurementsStatus.saving ||
                state.status == MeasurementsStatus.loadingMore) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
            ],
            if (state.saveMessage != null) ...[
              const SizedBox(height: 16),
              _MeasurementsBanner(message: state.saveMessage!, success: true),
            ],
            if (state.errorMessage != null) ...[
              const SizedBox(height: 16),
              _MeasurementsBanner(
                message: state.errorMessage!,
                action: TextButton(
                  onPressed: busy ? null : _controller.reload,
                  child: const Text(AppStrings.reload),
                ),
              ),
            ],
            const SizedBox(height: 20),
            _buildLatestSection(state),
            const SizedBox(height: 20),
            if (!_formOpen)
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  key: const ValueKey('add-measurement'),
                  onPressed: busy ? null : _openNewForm,
                  icon: const Icon(Icons.add),
                  label: const Text(AppStrings.addMeasurement),
                ),
              ),
            if (_formOpen) ...[
              _MeasurementForm(
                key: ValueKey(
                  _editingMeasurement == null
                      ? 'new-measurement-form'
                      : 'edit-measurement-form-${formatMeasurementDate(_editingMeasurement!.measuredOn)}',
                ),
                measurement: _editingMeasurement,
                initialDate: _formDate!,
                enabled: !busy,
                onCancel: _closeForm,
                onPickDate: _pickFormDate,
                onSubmit: _submitForm,
              ),
            ],
            const SizedBox(height: 20),
            _buildFilterSection(busy),
            const SizedBox(height: 20),
            _buildHistorySection(state, busy),
          ],
        ),
      ),
    );
  }

  Widget _buildLatestSection(MeasurementsState state) => Card(
    key: const ValueKey('latest-measurement-card'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            AppStrings.latestMeasurement,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          if (state.latest == null)
            const Text(AppStrings.noMeasurements)
          else
            _measurementSummary(state.latest!),
        ],
      ),
    ),
  );

  Widget _measurementSummary(BodyMeasurement measurement) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Text(
        formatDisplayDate(measurement.measuredOn),
        style: Theme.of(context).textTheme.titleMedium,
      ),
      const SizedBox(height: 8),
      _measurementValues(measurement),
      const SizedBox(height: 8),
      Text(
        '${AppStrings.source}: ${ReferenceLocalizations.measurementSourceName(measurement.source.wireValue, measurement.source.displayName)}',
      ),
      if (measurement.note != null && measurement.note!.isNotEmpty) ...[
        const SizedBox(height: 4),
        Text('${AppStrings.notes}: ${measurement.note}'),
      ],
    ],
  );

  Widget _measurementValues(BodyMeasurement measurement) => Wrap(
    spacing: 16,
    runSpacing: 6,
    children: [
      Text('${AppStrings.weightKg}: ${_number(measurement.weightKg)}'),
      if (measurement.bodyFatPercent != null)
        Text(
          '${AppStrings.bodyFatPercent}: ${_number(measurement.bodyFatPercent!)}',
        ),
      if (measurement.waistCm != null)
        Text('${AppStrings.waistCm}: ${_number(measurement.waistCm!)}'),
    ],
  );

  Widget _buildFilterSection(bool busy) => Card(
    key: const ValueKey('measurement-filter-section'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            AppStrings.measurementHistory,
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: 12),
          LayoutBuilder(
            builder: (context, constraints) {
              final compact = constraints.maxWidth < 520;
              final fields = [
                _filterDateField(
                  key: const ValueKey('measurement-filter-from'),
                  label: AppStrings.fromDate,
                  controller: _filterFromController,
                  onPick: () => _pickFilterDate(isFrom: true),
                ),
                _filterDateField(
                  key: const ValueKey('measurement-filter-to'),
                  label: AppStrings.toDate,
                  controller: _filterToController,
                  onPick: () => _pickFilterDate(isFrom: false),
                ),
              ];
              return compact
                  ? Column(children: fields)
                  : Row(
                      children: [
                        Expanded(child: fields[0]),
                        const SizedBox(width: 12),
                        Expanded(child: fields[1]),
                      ],
                    );
            },
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 8,
            children: [
              FilledButton(
                key: const ValueKey('apply-measurement-filter'),
                onPressed: busy ? null : _applyFilter,
                child: const Text(AppStrings.applyFilter),
              ),
              OutlinedButton(
                key: const ValueKey('clear-measurement-filter'),
                onPressed: busy ? null : _clearFilter,
                child: const Text(AppStrings.clearFilter),
              ),
            ],
          ),
        ],
      ),
    ),
  );

  Widget _filterDateField({
    required Key key,
    required String label,
    required TextEditingController controller,
    required VoidCallback onPick,
  }) => TextFormField(
    key: key,
    readOnly: true,
    controller: controller,
    onTap: onPick,
    decoration: InputDecoration(
      labelText: label,
      border: const OutlineInputBorder(),
      suffixIcon: const Icon(Icons.calendar_today),
    ),
  );

  Widget _buildHistorySection(MeasurementsState state, bool busy) {
    if (state.history.isEmpty) {
      return const SizedBox.shrink();
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final measurement in state.history) ...[
          _HistoryMeasurementCard(
            key: ValueKey(
              'measurement-${formatMeasurementDate(measurement.measuredOn)}',
            ),
            measurement: measurement,
            enabled: !busy,
            onEdit: () => _openEditForm(measurement),
          ),
          const SizedBox(height: 12),
        ],
        if (state.hasMore)
          Align(
            alignment: Alignment.center,
            child: OutlinedButton(
              key: const ValueKey('load-more-measurements'),
              onPressed: state.status == MeasurementsStatus.loadingMore
                  ? null
                  : _controller.loadMore,
              child: const Text(AppStrings.loadMore),
            ),
          ),
      ],
    );
  }

  Future<void> _submitForm(MeasurementFormValues values) async {
    final draft = MeasurementDraft(
      measuredOn: values.measuredOn,
      weightKg: MeasurementsValidation.parseLocalizedMeasurementNumber(
        values.weight,
      ),
      bodyFatPercent: MeasurementsValidation.parseLocalizedMeasurementNumber(
        values.bodyFatPercent,
      ),
      waistCm: MeasurementsValidation.parseLocalizedMeasurementNumber(
        values.waist,
      ),
      note: values.note,
    );
    if (_editingMeasurement == null) {
      await _controller.recordMeasurement(draft);
    } else {
      await _controller.updateMeasurement(draft);
    }
  }

  void _openNewForm() {
    setState(() {
      _editingMeasurement = null;
      _formDate = _dateOnly(backendUtcToday());
      _formOpen = true;
    });
  }

  void _openEditForm(BodyMeasurement measurement) {
    setState(() {
      _editingMeasurement = measurement;
      _formDate = measurement.measuredOn;
      _formOpen = true;
    });
  }

  void _closeForm() {
    if (!mounted) {
      return;
    }
    setState(() {
      _editingMeasurement = null;
      _formDate = null;
      _formOpen = false;
    });
  }

  Future<void> _pickFilterDate({required bool isFrom}) async {
    final picked = await _pickDate(isFrom ? _filterFrom : _filterTo);
    if (picked == null || !mounted) {
      return;
    }
    setState(() {
      if (isFrom) {
        _filterFrom = picked;
        _filterFromController.text = formatDisplayDate(picked);
      } else {
        _filterTo = picked;
        _filterToController.text = formatDisplayDate(picked);
      }
    });
  }

  Future<DateTime?> _pickFormDate(DateTime current) => _pickDate(current);

  Future<void> _applyFilter() async {
    if (_filterFrom == null || _filterTo == null) {
      _showMessage(AppStrings.measurementDateRangeRequired);
      return;
    }
    if (_filterFrom!.isAfter(_filterTo!)) {
      _showMessage(AppStrings.measurementDateRangeInvalid);
      return;
    }
    await _controller.load(from: _filterFrom, to: _filterTo);
  }

  Future<void> _clearFilter() async {
    setState(() {
      _filterFrom = null;
      _filterTo = null;
      _filterFromController.clear();
      _filterToController.clear();
    });
    await _controller.load();
  }

  Future<DateTime?> _pickDate(DateTime? initialDate) {
    final today = _dateOnly(backendUtcToday());
    return showDatePicker(
      context: context,
      initialDate: _dateOnly(initialDate ?? today),
      firstDate: DateTime(1900),
      lastDate: today,
      locale: const Locale('vi', 'VN'),
    );
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  static DateTime _dateOnly(DateTime value) =>
      DateTime(value.year, value.month, value.day);
}

class MeasurementFormValues {
  const MeasurementFormValues({
    required this.measuredOn,
    required this.weight,
    required this.bodyFatPercent,
    required this.waist,
    required this.note,
  });

  final DateTime measuredOn;
  final String weight;
  final String bodyFatPercent;
  final String waist;
  final String note;
}

class _MeasurementForm extends StatefulWidget {
  const _MeasurementForm({
    super.key,
    required this.measurement,
    required this.initialDate,
    required this.enabled,
    required this.onCancel,
    required this.onPickDate,
    required this.onSubmit,
  });

  final BodyMeasurement? measurement;
  final DateTime initialDate;
  final bool enabled;
  final VoidCallback onCancel;
  final Future<DateTime?> Function(DateTime current) onPickDate;
  final Future<void> Function(MeasurementFormValues values) onSubmit;

  @override
  State<_MeasurementForm> createState() => _MeasurementFormState();
}

class _MeasurementFormState extends State<_MeasurementForm> {
  final _formKey = GlobalKey<FormState>();
  late DateTime _measuredOn;
  late final TextEditingController _dateController;
  late final TextEditingController _weightController;
  late final TextEditingController _bodyFatController;
  late final TextEditingController _waistController;
  late final TextEditingController _noteController;

  @override
  void initState() {
    super.initState();
    final measurement = widget.measurement;
    _measuredOn = widget.initialDate;
    _dateController = TextEditingController(
      text: formatDisplayDate(_measuredOn),
    );
    _weightController = TextEditingController(
      text: measurement == null ? '' : _number(measurement.weightKg),
    );
    _bodyFatController = TextEditingController(
      text: measurement?.bodyFatPercent == null
          ? ''
          : _number(measurement!.bodyFatPercent!),
    );
    _waistController = TextEditingController(
      text: measurement?.waistCm == null ? '' : _number(measurement!.waistCm!),
    );
    _noteController = TextEditingController(text: measurement?.note ?? '');
  }

  @override
  void dispose() {
    _dateController.dispose();
    _weightController.dispose();
    _bodyFatController.dispose();
    _waistController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Card(
    key: const ValueKey('measurement-form-card'),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              widget.measurement == null
                  ? AppStrings.addMeasurement
                  : AppStrings.editMeasurement,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('measurement-date'),
              readOnly: true,
              controller: _dateController,
              onTap: widget.enabled && widget.measurement == null
                  ? () async {
                      final picked = await widget.onPickDate(_measuredOn);
                      if (picked == null || !mounted) {
                        return;
                      }
                      setState(() {
                        _measuredOn = picked;
                        _dateController.text = formatDisplayDate(picked);
                      });
                    }
                  : null,
              decoration: const InputDecoration(
                labelText: AppStrings.measuredDate,
                border: OutlineInputBorder(),
                suffixIcon: Icon(Icons.calendar_today),
              ),
              validator: (_) =>
                  MeasurementsValidation.validateMeasuredOn(_measuredOn),
            ),
            if (widget.measurement != null) ...[
              const SizedBox(height: 4),
              const Text(AppStrings.measuredDateImmutable),
            ],
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('measurement-weight'),
              controller: _weightController,
              enabled: widget.enabled,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.,+-]')),
              ],
              decoration: const InputDecoration(
                labelText: AppStrings.weightKg,
                border: OutlineInputBorder(),
              ),
              validator: MeasurementsValidation.validateWeightText,
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('measurement-body-fat'),
              controller: _bodyFatController,
              enabled: widget.enabled,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.,+-]')),
              ],
              decoration: const InputDecoration(
                labelText: AppStrings.bodyFatPercent,
                border: OutlineInputBorder(),
              ),
              validator: MeasurementsValidation.validateOptionalBodyFatText,
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('measurement-waist'),
              controller: _waistController,
              enabled: widget.enabled,
              keyboardType: const TextInputType.numberWithOptions(
                decimal: true,
              ),
              inputFormatters: [
                FilteringTextInputFormatter.allow(RegExp(r'[0-9.,+-]')),
              ],
              decoration: const InputDecoration(
                labelText: AppStrings.waistCm,
                border: OutlineInputBorder(),
              ),
              validator: MeasurementsValidation.validateOptionalWaistText,
            ),
            const SizedBox(height: 12),
            TextFormField(
              key: const ValueKey('measurement-note'),
              controller: _noteController,
              enabled: widget.enabled,
              maxLength: 255,
              maxLengthEnforcement: MaxLengthEnforcement.none,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: AppStrings.notes,
                alignLabelWithHint: true,
                border: OutlineInputBorder(),
              ),
              validator: MeasurementsValidation.validateNote,
            ),
            const SizedBox(height: 4),
            const Text(AppStrings.measurementUpsertHelper),
            const SizedBox(height: 16),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              children: [
                FilledButton(
                  key: const ValueKey('save-measurement'),
                  onPressed: widget.enabled ? _submit : null,
                  child: Text(
                    widget.measurement == null
                        ? AppStrings.saveMeasurement
                        : AppStrings.updateMeasurement,
                  ),
                ),
                OutlinedButton(
                  key: const ValueKey('cancel-measurement'),
                  onPressed: widget.enabled ? widget.onCancel : null,
                  child: const Text(AppStrings.cancel),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) {
      return;
    }
    await widget.onSubmit(
      MeasurementFormValues(
        measuredOn: _measuredOn,
        weight: _weightController.text,
        bodyFatPercent: _bodyFatController.text,
        waist: _waistController.text,
        note: _noteController.text,
      ),
    );
  }
}

class _HistoryMeasurementCard extends StatelessWidget {
  const _HistoryMeasurementCard({
    super.key,
    required this.measurement,
    required this.enabled,
    required this.onEdit,
  });

  final BodyMeasurement measurement;
  final bool enabled;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            formatDisplayDate(measurement.measuredOn),
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 16,
            runSpacing: 6,
            children: [
              Text('${AppStrings.weightKg}: ${_number(measurement.weightKg)}'),
              if (measurement.bodyFatPercent != null)
                Text(
                  '${AppStrings.bodyFatPercent}: ${_number(measurement.bodyFatPercent!)}',
                ),
              if (measurement.waistCm != null)
                Text('${AppStrings.waistCm}: ${_number(measurement.waistCm!)}'),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            '${AppStrings.source}: ${ReferenceLocalizations.measurementSourceName(measurement.source.wireValue, measurement.source.displayName)}',
          ),
          if (measurement.note != null && measurement.note!.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text('${AppStrings.notes}: ${measurement.note}'),
          ],
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton(
              key: ValueKey(
                'edit-measurement-${formatMeasurementDate(measurement.measuredOn)}',
              ),
              onPressed: enabled ? onEdit : null,
              child: const Text(AppStrings.editMeasurement),
            ),
          ),
        ],
      ),
    ),
  );
}

class _MeasurementsLoadError extends StatelessWidget {
  const _MeasurementsLoadError({required this.message, required this.onRetry});

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
          FilledButton(onPressed: onRetry, child: const Text(AppStrings.retry)),
        ],
      ),
    ),
  );
}

class _MeasurementsBanner extends StatelessWidget {
  const _MeasurementsBanner({
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

String formatDisplayDate(DateTime value) =>
    '${value.day.toString().padLeft(2, '0')}/'
    '${value.month.toString().padLeft(2, '0')}/'
    '${value.year.toString().padLeft(4, '0')}';

String _number(double value) => value.toString().replaceFirst('.', ',');
