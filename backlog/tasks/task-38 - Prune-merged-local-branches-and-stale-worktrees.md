---
id: TASK-38
title: Prune merged local branches and stale worktrees
status: To Do
assignee: []
created_date: '2026-10-11 07:23'
labels:
  - adversarial-audit
  - hygiene
milestone: m-12
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Housekeeping found by the 2026-10-11 adversarial audit: 11 local branches are already merged into main, 2 are not (m6-footprint, which backs a prunable worktree at /tmp/aw-wt-foot, and wip/okio-3.18.2-translation-2026-09-30), two worktrees are prunable, and a detached worktree /tmp/aw-98 plus its Maven repository /tmp/m2-98 remain from verification runs. Deleting branches is irreversible, so the maintainer confirms the list first. Check the two unmerged branches for content that exists nowhere else before touching them.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Local branches already merged into main (audit-m6-followups, m6-docs, m6-gen, m6-license, m6-prototarget-wt, m6-scripts, task-33-2-companion-and-members, task-33-3-unchecked, task-34-surface-check, upstream-getter-names) are deleted after the maintainer confirms.
- [ ] #2 The two branches that are not merged (m6-footprint, wip/okio-3.18.2-translation-2026-09-30) are each either merged, pushed as an archive tag, or deleted, with the decision recorded here and the content verified first (git log main..branch).
- [ ] #3 The prunable worktrees /tmp/aw-wt-foot and /tmp/aw-wt-lic and the detached /tmp/aw-98 and /tmp/m2-98 are pruned or removed.
- [ ] #4 git worktree list and git branch show only what is intended.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
