import 'package:flutter/material.dart';
import 'package:smart_meal_planner/core/ui/responsive_content.dart';
import 'package:smart_meal_planner/core/ui/wellness_components.dart';
import 'package:smart_meal_planner/features/auth/application/session_controller.dart';
import 'package:smart_meal_planner/features/auth/presentation/authenticated_shell.dart';
import 'package:smart_meal_planner/features/catalog/application/food_catalog_controller.dart';
import 'package:smart_meal_planner/features/catalog/data/catalog_models.dart';
import 'package:smart_meal_planner/features/catalog/presentation/catalog_widgets.dart';
import 'package:smart_meal_planner/l10n/app_strings.dart';

class FoodCatalogPage extends StatefulWidget {
  const FoodCatalogPage({
    super.key,
    required this.sessionController,
    required this.controller,
  });

  final SessionController sessionController;
  final FoodCatalogController controller;

  @override
  State<FoodCatalogPage> createState() => _FoodCatalogPageState();
}

class _FoodCatalogPageState extends State<FoodCatalogPage> {
  late final TextEditingController _searchController;

  FoodCatalogController get _controller => widget.controller;

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

  @override
  Widget build(BuildContext context) => AuthenticatedShell(
    sessionController: widget.sessionController,
    selectedIndex: 0,
    content: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final state = _controller.state;
    return SafeArea(
      child: ResponsiveContent(
        child: ListView(
          key: const ValueKey('food-list'),
          children: [
            const PageIntro(
              key: ValueKey('food-catalog-title'),
              eyebrow: AppStrings.wellnessEyebrow,
              title: AppStrings.foods,
              subtitle: AppStrings.catalogFoodsSubtitle,
              icon: Icons.restaurant_menu_rounded,
            ),
            SectionSurface(
              title: AppStrings.catalogExploreTitle,
              icon: Icons.search_rounded,
              child: Column(
                children: [
                  CatalogSearchBar(
                    controller: _searchController,
                    fieldKey: const ValueKey('food-search-field'),
                    submitKey: const ValueKey('food-search-submit'),
                    onSubmitted: _controller.search,
                  ),
                  const SizedBox(height: 12),
                  CatalogCategoryFilter(
                    categories: state.categories,
                    selectedCategoryCode: state.selectedCategoryCode,
                    filterKey: const ValueKey('food-category-filter'),
                    onChanged: _controller.setCategory,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 20),
            ..._bodyChildren(state),
          ],
        ),
      ),
    );
  }

  List<Widget> _bodyChildren(CatalogListState<FoodCatalogItem> state) {
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
          retryKey: const ValueKey('food-retry'),
          onRetry: _controller.reload,
        ),
      ];
    }
    if (state.items.isEmpty) {
      return [
        const CatalogEmptyPanel(message: AppStrings.catalogNoFoods),
        if (state.searchQuery.isEmpty && state.selectedCategoryCode == null)
          const Text('Danh mục thực phẩm đang trống. Hãy chạy import demo.'),
      ];
    }

    return [
      LayoutBuilder(
        builder: (context, constraints) {
          final columns = constraints.maxWidth >= 760 ? 2 : 1;
          final width = (constraints.maxWidth - (columns - 1) * 14) / columns;
          return Wrap(
            spacing: 14,
            runSpacing: 14,
            children: [
              for (final item in state.items)
                SizedBox(
                  width: width,
                  child: FoodCatalogListItem(
                    key: ValueKey('food-item-${item.publicId}'),
                    item: item,
                  ),
                ),
            ],
          );
        },
      ),
      if (state.hasMore)
        CatalogLoadMoreFooter(
          loading: state.isLoadingMore,
          errorMessage: state.loadMoreErrorMessage,
          loadMoreKey: const ValueKey('food-load-more'),
          retryKey: const ValueKey('food-load-more-retry'),
          onLoadMore: _controller.loadMore,
        ),
    ];
  }
}
