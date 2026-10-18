---
name: task-accelerator
description: >-
  Enforces strict scoping, isolated verification, and execution guards to complete tasks
  in 2-5 minutes instead of 15-20. Use when planning, implementing, modifying code, or testing
  features in tusur-schedule.
---

# Task Accelerator for Antigravity (tusur-schedule)

This skill optimizes agent workflow loops, prevents context-window bloat, avoids slow end-to-end network requests, and ensures fast, deterministic commits.

## Core Directives

### 1. Scope & File Pinning
- **Strict target list**: Always identify 1–2 target files to edit before touching code.
- **Never explore broadly**: Do NOT run repository-wide text searches (`grep`, `rg`, or `manage_task`) or scan unrelated modules (e.g. do not read `bot.py` or `index.html` when modifying `parser.py` or `gcal_sync.py`).
- Read only the specific line intervals needed using precise view tools.

### 2. Dependency Guards
- All base requirements are already installed in `.venv`.
- **FORBIDDEN**: Do NOT run `pip install` or `pip list` unless the user explicitly requested adding a new library to `requirements.txt`.

### 3. Verification & Test Constraints
- **FORBIDDEN**: Never run full network crawls or semester-wide syncs (e.g., NEVER run `python parser.py --all-weeks`).
- **Minimal test execution**:
  - Run the parser strictly on a single week or a mocked HTML response: `python parser.py` (no `--all-weeks`).
  - Verify changes via targeted inline one-liners:
    ```bash
    .venv/Scripts/python -c "import <module>; print('OK')"
    ```
  - For data validation, verify against a small mock slice (e.g. reading 1 entry from `schedule.json` or `overrides.json`).

### 4. Diff-First Modifications
- Do not rewrite entire files. Apply unified diffs or minimal block updates.
- If a data schema or contract is provided in the prompt, adhere strictly to its fields without inventing additional parameters.

### 5. Execution Workflow
1. **Analyze**: Check inputs, locate target functions, verify no unrelated files are touched.
2. **Apply**: Edit code in a single targeted tool call.
3. **Verify**: Run one minimal verification command (runtime import or single-unit test).
4. **Finalize**: Stage only the modified target files and commit directly to the working branch.
