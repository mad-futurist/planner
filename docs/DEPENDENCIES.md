# Dependency rules

The migration target has four logical layers in each existing monolith. Keeping one Gradle app and one Python service avoids new deployment/build modules.

```mermaid
flowchart BT
  Presentation[Compose / Android platform] --> Application
  HTTP[FastAPI interface] --> Application
  Infrastructure[Room / HTTP / PostgreSQL / provider adapters] --> Application
  Application --> Domain
  Composition[Hilt / Python composition root] --> Infrastructure
  Composition --> Presentation
  Composition --> HTTP
```

Arrows represent source imports, not runtime call order. Domain must contain only business models and policies. Application owns workflows and ports. Infrastructure implements persistence, provider and platform ports. Presentation owns formatting, native pickers and navigation, and invokes application commands through feature ViewModels. The migration record lists remaining exceptions until verification is complete.


## Enforced package mapping and exceptions

Run `uv run --project backend --frozen python tools/check_architecture.py`. CI and a backend test execute the same source checks. Android domain/application must not import framework/Room/HTTP/JSON adapters. Feature UI/ViewModels cannot import concrete repositories/DAO/preferences/sync or expose mutable flows. Python domain/application cannot import FastAPI/Pydantic/SQLAlchemy/httpx or concrete outer packages. API resource modules cannot query persistence or construct providers/security clients.

Android uses canonical `domain`, `application`, `infrastructure`, `presentation` packages. There is no features/core/data/sync/navigation root. Package declarations must match source directories. Application code belongs to port/usecase/model; presentation belongs to a named feature or common/design/navigation/shell owner. Feature Route files create/collect ViewModels; the navigation graph only composes Routes.

The only presentation-to-infrastructure import exception is settings/platform/OverlaySettingsRoute.kt importing infrastructure.overlay for native permission/service wiring. SettingsScreen receives a composable slot and does not import that adapter. Native overlay code can reuse shared design tokens; it does not depend on screen ViewModels. Root MainActivity/PlannerApp are Android composition entry points.

Backend retains provider/security/operator modules at historical paths, wired through bootstrap. `app/services.py` re-exports infrastructure compatibility helpers; `app/ai/context.py` adapts historical DB callers to application context assembly. New application services import neither. These paths are compatibility surfaces, not additional business layers.

`androidx.hilt:hilt-lifecycle-viewmodel-compose:1.4.0` supplies the DI-aware destination factory. Plain Navigation Compose `viewModel()` did not provide Hilt dependencies in the first runtime attempt. The package/API is documented in [AndroidX Hilt releases](https://developer.android.com/jetpack/androidx/releases/hilt). Existing build/framework pins remain otherwise unchanged.


WorkManager uses `infrastructure/sync/PlannerWorkerFactory.kt`, injected into `PlannerApp` (Configuration.Provider). SyncWorker receives SyncEngine in its constructor. Only the WorkManager default startup metadata is removed; other AndroidX startup initializers remain enabled. `SyncDependencies` is retained as an instrumentation/diagnostic compatibility entry point; production code does not call EntryPointAccessors. See [official custom WorkManager configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration).

`presentation/navigation/PlannerRoutes.kt` owns route constants; `ScreenNavigation` exposes presentation callbacks. NavController is restricted to graph/route bindings. Record/interpretation/development modal sessions reject old close signals and publish inline failures. These are presentation lifecycle mechanics; version/CAS and approval rules remain in application/storage policies.


The factory accepts the previously persisted app.planner.sync.SyncWorker name as well as the new canonical name. This is a runtime migration rule, not a legacy source package. Room exported-schema directories follow the new database class name; SQL identity hashes/versions stay fixed. [Full package tree](ANDROID_PACKAGES.md). Negative architecture tests reject flat packages, outward imports and ViewModel creation inside the graph.
