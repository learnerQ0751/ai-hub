<!-- AI-WORKFLOW -->
# Project AI Workflow

Before coding, read and follow:

- `ai-workflow/README.md`
- `ai-workflow/docs/STANDARDS.md`
- `ai-workflow/docs/PROJECT.md`
- `ai-workflow/docs/WORKFLOW.md`

## Requirement input

When the user asks to implement `REQ-x.y`:

1. If `ai-workflow/requirements/INDEX.md` exists, read it and follow the entry to the external requirement card.
2. Otherwise (or if the user describes a requirement directly), draft the requirement and acceptance criteria from `ai-workflow/templates/REQ.md` and get user confirmation.

Both paths converge to `ai-workflow/records/REQ-<id>.md`; the rest of the workflow is identical. A missing INDEX must not block the workflow.

## Framework choices

Framework and middleware choices come from `ai-workflow/docs/PROJECT.md`. If a required choice is missing or unclear, stop and ask before implementing. Never substitute or downgrade a framework (e.g. JdbcClient instead of MyBatis) without explicit approval.

Record per-requirement decisions, acceptance, and retrospective in `ai-workflow/records/REQ-*.md`.

If a tool is unavailable (spec-kit / OpenSpec / git-ai), follow the manual fallback in `WORKFLOW.md`; do not block.
