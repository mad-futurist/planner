
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

- Preserve separation between presentation, domain, and data layers.
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
