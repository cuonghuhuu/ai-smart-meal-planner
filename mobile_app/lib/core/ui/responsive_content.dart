import 'package:flutter/widgets.dart';
import 'package:smart_meal_planner/core/responsive/responsive_breakpoints.dart';

class ResponsiveContent extends StatelessWidget {
  const ResponsiveContent({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final horizontalPadding =
            constraints.maxWidth < ResponsiveBreakpoints.compact ? 16.0 : 32.0;

        return Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
              maxWidth: ResponsiveBreakpoints.maxContentWidth,
            ),
            child: Padding(
              padding: EdgeInsets.symmetric(
                horizontal: horizontalPadding,
                vertical: 24,
              ),
              child: child,
            ),
          ),
        );
      },
    );
  }
}
