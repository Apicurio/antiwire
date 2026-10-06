---
id: TASK-29
title: Ignore Python bytecode caches in scripts
status: To Do
assignee: []
created_date: '2026-10-06 15:33'
labels:
  - adversarial-audit
  - hygiene
milestone: m-12
dependencies: []
references:
  - scripts/__pycache__/
  - .gitignore
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Running the repository's own scripts creates scripts/__pycache__/*.pyc files. The repository has no ignore rule for them, one cache file (cpython-311) is already tracked, and every machine with another Python version shows a new untracked file. Both audit reviews and the worker reports flagged this. Add the ignore rule and drop the tracked file from the index.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Python bytecode caches under scripts/ are ignored by git and the stale tracked cpython-311 cache file is removed from the index.
- [ ] #2 git status in a fresh clone after running the repository's scripts shows no untracked cache files.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
