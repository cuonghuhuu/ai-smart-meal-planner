import 'package:flutter/material.dart';
import 'package:smart_meal_planner/app/theme/app_theme.dart';

class PageIntro extends StatelessWidget {
  const PageIntro({
    super.key,
    required this.eyebrow,
    required this.title,
    required this.subtitle,
    this.icon,
  });

  final String eyebrow;
  final String title;
  final String subtitle;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 28),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (icon != null) ...[
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: AppTheme.mint,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Icon(icon, color: AppTheme.forest),
          ),
          const SizedBox(width: 16),
        ],
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                eyebrow.toUpperCase(),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: AppTheme.forest,
                  letterSpacing: 1.8,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              Text(title, style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 8),
              Text(
                subtitle,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                  height: 1.4,
                ),
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class SectionSurface extends StatelessWidget {
  const SectionSurface({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.icon,
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final IconData? icon;
  final Widget? trailing;
  final Widget child;

  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surface,
    elevation: 2,
    shadowColor: AppTheme.forest.withValues(alpha: .12),
    borderRadius: BorderRadius.circular(24),
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              if (icon != null) ...[
                Icon(icon, color: AppTheme.forest, size: 22),
                const SizedBox(width: 10),
              ],
              Expanded(
                child: Text(
                  title,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              ?trailing,
            ],
          ),
          if (subtitle != null) ...[
            const SizedBox(height: 7),
            Text(
              subtitle!,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
          const SizedBox(height: 20),
          child,
        ],
      ),
    ),
  );
}

class WellnessEmptyState extends StatelessWidget {
  const WellnessEmptyState({
    super.key,
    required this.icon,
    required this.message,
    this.action,
  });

  final IconData icon;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
    decoration: BoxDecoration(
      color: AppTheme.mint.withValues(alpha: .5),
      borderRadius: BorderRadius.circular(20),
    ),
    child: Column(
      children: [
        Icon(icon, size: 35, color: AppTheme.forest),
        const SizedBox(height: 12),
        Text(message, textAlign: TextAlign.center),
        if (action != null) ...[const SizedBox(height: 16), action!],
      ],
    ),
  );
}

class WellnessNotice extends StatelessWidget {
  const WellnessNotice({
    super.key,
    required this.message,
    this.error = false,
    this.action,
  });

  final String message;
  final bool error;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: error ? scheme.errorContainer : AppTheme.mint,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(
            error ? Icons.error_outline : Icons.check_circle_outline,
            color: error ? scheme.onErrorContainer : AppTheme.forest,
          ),
          const SizedBox(width: 12),
          Expanded(child: Text(message)),
          ?action,
        ],
      ),
    );
  }
}
