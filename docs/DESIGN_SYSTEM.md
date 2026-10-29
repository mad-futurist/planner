# Design system

Status: **Implemented and reviewed with scoped emulator/golden checks**, 2026-10-21. The supplied [screen references](SCREENS.md#reference-gallery) now own visual direction, superseding ADR-011's simplified treatment. Runtime evidence belongs in [Visual audit](VISUAL_AUDIT.md).

Use native Compose/Material 3, shared tokens, accessible controls and Canvas paths for original landscape/stair/plant accents. Evaluate fonts against the references and bundle redistribution licenses. Do not embed screenshot backgrounds or seed production data.

Implemented source: [tokens/theme](../android/app/src/main/java/app/planner/presentation/design/theme/Theme.kt), [typography](../android/app/src/main/java/app/planner/presentation/design/theme/Typography.kt), [shared components](../android/app/src/main/java/app/planner/presentation/design/components/PlannerCard.kt), [original icons](../android/app/src/main/java/app/planner/presentation/design/components/PlannerIcons.kt) and [vector accents](../android/app/src/main/java/app/planner/presentation/design/illustrations/Illustrations.kt). Serif display is Playfair Display (Latin/Cyrillic); body is DM Sans with platform fallback for missing glyphs. Both are bundled offline with SIL OFL licenses in source and APK assets. Font sources: [Playfair Display](https://github.com/google/fonts/tree/main/ofl/playfairdisplay), [DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans). No new runtime dependency was added.

Geometry: 20 dp page inset, 18 dp cards, 28 dp composer, 24 dp navigation, 48 dp action targets, 36–38 dp decorative icon circles. Fonts scale with system settings; long task text wraps, lists scroll and keyboard hides the navigation row while capture remains available. System/light/dark selection persists. Material colors provide semantic dark surfaces; daytime landscape accents are decorative only. Rendered reference comparisons, dark mode and responsive compromises are recorded in Visual audit.

Research: [Compose graphics](https://developer.android.com/develop/ui/compose/graphics/draw/overview) supports vector drawing and cached paths. [Kizitonwose Calendar](https://github.com/kizitonwose/Calendar) is customizable but excessive for seven date cells; retain native date pickers and build a small strip. [Roborazzi](https://github.com/takahirom/roborazzi) is a maintained screenshot option; assess compatibility before adding a Gradle plugin. Emulator captures and deterministic instrumentation fixtures are the initial comparison path. Final choices and executed results are recorded below and in Testing.

## Component assessment and final choices

Checked primary repositories on 2026-10-21:

| Candidate | Evidence / suitability | Decision |
| --- | --- | --- |
| Compose/Material 3 already pinned in the app | Native semantics, scalable layout, Canvas/Brush/Path and rounded Surface/Card primitives; no additional runtime | Use for cards, navigation, timeline, checkboxes, gradients, expansion and illustrations |
| Kizitonwose Calendar 2.10.1 | MIT; last repository push 2026-08-13; customizable date/week/month support, more state/dependency surface than this fixed seven-cell agenda needs | Keep a small native strip and native date picker; no library added |
| Roborazzi 1.76.0 | Apache-2.0; release 2026-09-29, repository active 2026-10-21; good JVM screenshot tooling, but this project's AGP 9.4.1 pair was not separately validated | Use existing instrumentation renderer for two fixed component goldens and full emulator captures; do not imply Roborazzi is incompatible |
| Playfair Display + DM Sans | Unmodified Google Fonts variable TTFs, SIL OFL; Playfair has Latin/Cyrillic cmap coverage verified from the downloaded asset; DM Sans has Latin, with platform Cyrillic fallback | Playfair's high-contrast serif is closer to the references than rounded display alternatives. Bundle both with explicit weight axes and licenses |

Font source hashes/licenses: [font sources](../android/licenses/FONT_SOURCES.md). Native overlay reuses vector glyph geometry and body font, with system/light/dark panel colors. All production illustration drawing is vector based; no screenshot bitmaps or image loading library are used.
