---
name: mealplanner-figma-to-flutter
description: Convert an approved Figma screen into production-ready Flutter UI for AI Smart Meal Planner. Use when implementing or refining Android/Web screens from a Figma node.
---
# Meal Planner Figma to Flutter

## Preconditions

- Require a Figma design URL with a concrete node id when implementing an exact screen.
- Inspect the existing Flutter theme, shared widgets, routing, localization, repositories, and feature structure before editing.
- Preserve the current stack: Flutter for Android and Web. Do not introduce React or another UI framework.

## Workflow

1. Read the target Figma node using design-to-code context and a screenshot.
2. Identify reusable design tokens: spacing, radius, typography, colors, elevation, icons, and component variants.
3. Search the repository for an existing equivalent widget before creating a new one.
4. Implement the screen in the existing feature architecture; UI must not call the database or Python service directly.
5. Keep all user-facing copy in Vietnamese through the project's localization mechanism.
6. Make the layout responsive using available width, not device-name checks.
7. Wire loading, empty, error, and success states.
8. Run:
   - dart format
   - flutter analyze
   - focused widget/unit tests
9. Compare the running screen against the Figma screenshot and fix visible spacing/overflow differences.

## Project rules

- Prefer existing theme/components over one-off styling.
- Do not rewrite working business logic just to match a visual.
- Do not hard-code secrets, API tokens, backend URLs, or credentials.
- Android and Web are both required targets.
- For food-recognition results, display Vietnamese names and confidence clearly; bounding boxes belong to the recognition presentation layer.
