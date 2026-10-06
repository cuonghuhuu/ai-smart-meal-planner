import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/application/ingredient_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/data/ingredient_recognition_repository.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_widgets.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class IngredientCatalogPage extends StatefulWidget {
  const IngredientCatalogPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final IngredientCatalogController controller;

  @override
  State<IngredientCatalogPage> createState() => _IngredientCatalogPageState();
}

class _IngredientCatalogPageState extends State<IngredientCatalogPage> {
  late final TextEditingController _searchController;
  final ImagePicker _imagePicker = ImagePicker();
  Uint8List? _pickedImageBytes;
  String? _pickerError;

  IngredientCatalogController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController(
      text: _controller.state.searchQuery,
    );
    _controller.addListener(_onControllerChanged);
    _controller.loadInitial();
  }

  @override
  void dispose() {
    _controller.removeListener(_onControllerChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onControllerChanged() {
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _pickAndRecognize() async {
    try {
      final file = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        maxWidth: 1920,
        imageQuality: 90,
      );
      if (file == null || !mounted) {
        return;
      }
      final bytes = await file.readAsBytes();
      if (!mounted) {
        return;
      }
      setState(() {
        _pickedImageBytes = bytes;
        _pickerError = null;
      });
      await _controller.recognizeImage(bytes, _contentTypeFor(file));
    } on Object {
      if (mounted) {
        setState(() => _pickerError = AppStrings.genericError);
      }
    }
  }

  String _contentTypeFor(XFile file) {
    final mimeType = file.mimeType?.toLowerCase();
    if (mimeType == 'image/png' ||
        mimeType == 'image/webp' ||
        mimeType == 'image/jpeg') {
      return mimeType!;
    }
    final path = file.path.toLowerCase();
    if (path.endsWith('.png')) return 'image/png';
    if (path.endsWith('.webp')) return 'image/webp';
    return 'image/jpeg';
  }

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 1,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('ingredient-list'),
          children: [
            Text(
              AppStrings.ingredients,
              key: const ValueKey('ingredient-catalog-title'),
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: 8),
            const Text(AppStrings.catalogIngredientsSubtitle),
            if (_controller.recognitionRepository != null) ...[
              const SizedBox(height: 20),
              _RecognitionCard(
                state: _controller.recognitionState,
                imageBytes: _pickedImageBytes,
                pickerError: _pickerError,
                onPickImage: _pickAndRecognize,
              ),
            ],
            const SizedBox(height: 20),
            CatalogSearchBar(
              controller: _searchController,
              fieldKey: const ValueKey('ingredient-search-field'),
              submitKey: const ValueKey('ingredient-search-submit'),
              onSubmitted: _controller.search,
            ),
            const SizedBox(height: 12),
            CatalogCategoryFilter(
              categories: state.categories,
              selectedCategoryCode: state.selectedCategoryCode,
              filterKey: const ValueKey('ingredient-category-filter'),
              onChanged: _controller.setCategory,
            ),
            const SizedBox(height: 20),
            ..._bodyChildren(state),
          ],
        ),
      ),
    );
  }

  List<Widget> _bodyChildren(
    CatalogListState<IngredientCatalogItem> state,
  ) {
    if (state.isInitialLoading) {
      return const [
        Padding(
          padding: EdgeInsets.all(32),
          child: Center(child: CircularProgressIndicator()),
        ),
      ];
    }
    if (state.status == CatalogListStatus.error) {
      return [
        CatalogErrorPanel(
          message: state.errorMessage ?? AppStrings.catalogLoadFailed,
          retryKey: const ValueKey('ingredient-retry'),
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return const [
        CatalogEmptyPanel(message: AppStrings.catalogNoIngredients),
      ];
    }

    return [
      for (final item in state.items)
        IngredientCatalogListItem(
          key: ValueKey('ingredient-item-${item.publicId}'),
          item: item,
        ),
      if (state.hasMore)
        CatalogLoadMoreFooter(
          loading: state.isLoadingMore,
          errorMessage: state.loadMoreErrorMessage,
          loadMoreKey: const ValueKey('ingredient-load-more'),
          retryKey: const ValueKey('ingredient-load-more-retry'),
          onLoadMore: _controller.loadMore,
        ),
    ];
  }
}

class _RecognitionCard extends StatelessWidget {
  const _RecognitionCard({
    required this.state,
    required this.imageBytes,
    required this.pickerError,
    required this.onPickImage,
  });

  final IngredientRecognitionState state;
  final Uint8List? imageBytes;
  final String? pickerError;
  final VoidCallback onPickImage;

  @override
  Widget build(BuildContext context) {
    final result = state.result;
    return Card.outlined(
      key: const ValueKey('ingredient-recognition-card'),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.document_scanner_outlined),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        AppStrings.ingredientRecognitionTitle,
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(AppStrings.ingredientRecognitionSubtitle),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                FilledButton.icon(
                  key: ValueKey('ingredient-recognition-pick'),
                  onPressed: state.status == IngredientRecognitionStatus.loading
                      ? null
                      : onPickImage,
                  icon: Icon(Icons.add_photo_alternate_outlined),
                  label: Text(
                    imageBytes == null
                        ? AppStrings.ingredientRecognitionPick
                        : AppStrings.ingredientRecognitionPickAnother,
                  ),
                ),
              ],
            ),
            if (pickerError != null) ...[
              const SizedBox(height: 12),
              Text(
                pickerError!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (imageBytes != null) ...[
              const SizedBox(height: 18),
              _RecognitionPreview(
                imageBytes: imageBytes!,
                result: result,
              ),
            ],
            if (state.status == IngredientRecognitionStatus.loading) ...[
              const SizedBox(height: 16),
              const LinearProgressIndicator(),
              const SizedBox(height: 8),
              const Text(AppStrings.ingredientRecognitionRunning),
            ],
            if (state.status == IngredientRecognitionStatus.error) ...[
              const SizedBox(height: 16),
              Text(
                state.errorMessage ?? AppStrings.genericError,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (result != null) ...[
              const SizedBox(height: 16),
              Text(
                '${AppStrings.ingredientRecognitionModel}: ${result.algorithmVersion}',
                style: Theme.of(context).textTheme.bodySmall,
              ),
              const SizedBox(height: 10),
              if (result.detections.isEmpty)
                const Text(AppStrings.ingredientRecognitionNoDetections)
              else
                ...result.detections.map(
                  (detection) => _DetectionRow(detection: detection),
                ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RecognitionPreview extends StatelessWidget {
  const _RecognitionPreview({
    required this.imageBytes,
    required this.result,
  });

  final Uint8List imageBytes;
  final IngredientRecognitionResult? result;

  @override
  Widget build(BuildContext context) {
    final width = result?.imageWidth ?? 16;
    final height = result?.imageHeight ?? 9;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 760),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: AspectRatio(
            aspectRatio: width / height,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.memory(
                  imageBytes,
                  fit: BoxFit.fill,
                  gaplessPlayback: true,
                ),
                if (result != null)
                  CustomPaint(
                    painter: _DetectionPainter(
                      result: result!,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _DetectionRow extends StatelessWidget {
  const _DetectionRow({required this.detection});

  final IngredientRecognitionDetection detection;

  @override
  Widget build(BuildContext context) {
    final percent = (detection.confidence * 100).toStringAsFixed(1);
    return ListTile(
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        detection.needsConfirmation
            ? Icons.help_outline
            : Icons.check_circle_outline,
      ),
      title: Text(detection.nameVi),
      subtitle: Text(
        '${detection.code} · '
        '${AppStrings.ingredientRecognitionConfidence}: $percent%',
      ),
      trailing: detection.needsConfirmation
          ? const Chip(
              label: Text(AppStrings.ingredientRecognitionNeedsConfirmation),
            )
          : const Chip(
              label: Text(AppStrings.ingredientRecognitionDetected),
            ),
    );
  }
}

class _DetectionPainter extends CustomPainter {
  const _DetectionPainter({
    required this.result,
    required this.color,
  });

  final IngredientRecognitionResult result;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scaleX = size.width / result.imageWidth;
    final scaleY = size.height / result.imageHeight;
    final boxPaint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5;
    final labelPaint = Paint()..color = color.withValues(alpha: 0.92);

    for (final detection in result.detections) {
      final rect = Rect.fromLTRB(
        detection.box.x1 * scaleX,
        detection.box.y1 * scaleY,
        detection.box.x2 * scaleX,
        detection.box.y2 * scaleY,
      );
      canvas.drawRect(rect, boxPaint);

      final label =
          '${detection.nameVi} ${(detection.confidence * 100).toStringAsFixed(0)}%';
      final painter = TextPainter(
        text: TextSpan(
          text: label,
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.w600,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: size.width);

      final labelLeft = rect.left.clamp(0.0, size.width - painter.width - 8);
      final labelTop = (rect.top - painter.height - 8).clamp(
        0.0,
        size.height - painter.height - 8,
      );
      final background = Rect.fromLTWH(
        labelLeft,
        labelTop,
        painter.width + 8,
        painter.height + 6,
      );
      canvas.drawRRect(
        RRect.fromRectAndRadius(background, const Radius.circular(4)),
        labelPaint,
      );
      painter.paint(canvas, Offset(labelLeft + 4, labelTop + 3));
    }
  }

  @override
  bool shouldRepaint(covariant _DetectionPainter oldDelegate) =>
      oldDelegate.result != result || oldDelegate.color != color;
}
