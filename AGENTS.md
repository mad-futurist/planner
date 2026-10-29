
## Project Knowledge & Documentation

This repository contains an offline-first Android AI organizer.

### Product principles

- Capture first, structure afterward.
- Zero-friction input.
- Offline-first local persistence.
- Room is the Android UI source of truth.
- Raw notes must remain independent of AI interpretations.
- AI proposes structured information; deterministic code applies actions.
- Users can correct every AI interpretation.
- Dynamic types are defined through a Type Registry.
- Calm, predictable UX without unnecessary cognitive overhead.

### Before implementing changes

1. Read README.md and relevant documentation in docs/.
2. Understand existing architecture and conventions.
3. Check existing data models, interfaces, and contracts.
4. Reuse existing components and patterns.
5. Respect product principles and established architectural decisions.
6. Avoid unnecessary dependencies and abstractions.

### Documentation rules

Documentation is a mandatory part of development.

For every feature, bug fix, or refactor:

1. Update the relevant Markdown documentation.
2. Keep architecture diagrams and data models synchronized with code.
3. Document new screens, navigation, interactions, and UI states.
4. Update API contracts when endpoints or DTOs change.
5. Record significant decisions in docs/DECISIONS.md.
6. Update feature implementation status in docs/FEATURES.md.
7. Update docs/ROADMAP.md when milestones change.

Never document planned functionality as already implemented.

### Architecture rules

- Maintain four logical layers on Android and backend: Domain, Application, Infrastructure, Presentation/Interface.
- Domain imports no Android/Room/Retrofit, FastAPI/Pydantic/SQLAlchemy/httpx, application or infrastructure code.
- Application owns workflows and ports, depends inward on domain, and does not import concrete adapters or HTTP DTOs.
- Infrastructure implements ports. Hilt AppModule and Python bootstrap/main wire concrete implementations.
- Feature ViewModels publish immutable UiState and typed commands. Keep mutable flows private/protected.
- Compose content accepts state/callbacks; no repository, DAO, DataStore, SyncEngine or arbitrary business lambdas.
- Keep Room storage entities and ORM objects inside infrastructure; use explicit domain mappings.
- Main composer and native overlay share CaptureUseCase. Keep real overlay lifecycle/permissions intact.
- Backend routers map inputs/results only. Application use cases own UoW boundaries; provider I/O stays outside DB transactions.
- Preserve worker SKIP LOCKED claims, lease fencing and owner-before-job lock order on success and failure.
- Run tools/check_architecture.py alongside behavioral tests. Add narrow compatibility exceptions to docs/DEPENDENCIES.md, never silently bypass checks.
- Maintain offline-first behavior.
- Avoid network calls on the UI thread.
- Preserve backward compatibility or provide migrations.
- Use versioned structured interpretations.
- Protect user corrections from stale AI responses.
- Keep AI provider integration replaceable.
- Do not introduce new architectural patterns without justification.

### UX rules

- Follow docs/CONCEPT.md and docs/SCREENS.md.
- Maintain a consistent design system.
- Preserve original user input.
- Provide immediate feedback for local actions.
- Handle loading, offline, empty, and error states.
- Do not add unnecessary steps to note capture.
- Keep interactions predictable and reversible.

### Completion criteria

A task is complete only when:

- The implementation is functional.
- Relevant tests pass, or unexecuted tests are explicitly reported.
- Relevant documentation is updated.
- Architectural decisions are documented.
- Feature status reflects reality.

Documentation and implementation must never silently diverge.

### Context continuity

Treat the Markdown documentation as persistent project knowledge
across development sessions.

Before starting a new task, reconstruct the relevant project context
from the repository and documentation.

After completing a task, ensure another developer or AI agent can
understand:

- What was implemented.
- Why it was implemented this way.
- Which files and components were affected.
- What remains incomplete.
- Which decisions must be preserved.

Do not silently change established product behavior or architecture.
If a significant change is necessary, explain the trade-off and
record the decision.

Code is the source of truth for implemented behavior.
CONCEPT.md is the source of truth for intended product behavior.
Explicitly document any discrepancy between them.

### Architecture and migration knowledge

- Read docs/DEPENDENCIES.md and docs/ARCHITECTURE_MIGRATION.md before structural work.
- Current Room schema is v4. Do not apply the obsolete v3 instruction from the supplied refactor brief.
- Source-compatible aliases/CLI paths are documented exceptions, not permission to reintroduce outward business-layer dependencies.
- Record performed checks and remaining gates honestly; do not infer test completion from compilation or historical screenshots.


### Android source ownership

- Use canonical app.planner.domain/application/infrastructure/presentation packages matching directories; no flat features/core/data/sync roots.
- Application files belong in port, usecase or model. Do not create pass-through use cases just to mirror a screen or a base kind.
- Presentation files belong to their feature, common, design, navigation or shell. Feature Routes own Hilt ViewModel collection; the graph registers Routes.
- Split real screen sections into feature components; shared tokens and UI primitives belong in presentation/design.
- Preserve stored worker class-name compatibility when moving workers. Changing a Room class package must preserve schema identities and move historical exports intact.
- Read docs/ANDROID_PACKAGES.md for the full tree and scoped platform boundary.
