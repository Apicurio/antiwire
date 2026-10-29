---
id: TASK-38
title: Prune merged local branches and stale worktrees
status: Done
assignee: []
created_date: '2026-10-11 07:23'
updated_date: '2026-10-29 14:36'
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
- [x] #1 Local branches already merged into main (audit-m6-followups, m6-docs, m6-gen, m6-license, m6-prototarget-wt, m6-scripts, task-33-2-companion-and-members, task-33-3-unchecked, task-34-surface-check, upstream-getter-names) are deleted after the maintainer confirms.
- [x] #2 The two branches that are not merged (m6-footprint, wip/okio-3.18.2-translation-2026-09-30) are each either merged, pushed as an archive tag, or deleted, with the decision recorded here and the content verified first (git log main..branch).
- [x] #3 The prunable worktrees /tmp/aw-wt-foot and /tmp/aw-wt-lic and the detached /tmp/aw-98 and /tmp/m2-98 are pruned or removed.
- [x] #4 git worktree list and git branch show only what is intended.
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-29, maintainer confirmed the proposal ("38 okay"). Deleted with git branch -d (merged into main): the 10 listed in AC#1 plus fix-element-tostring and footprint-s11, and today's merged task-33-5-inmemory, task-36-tmp-artifacts and snapshot-publishing (their worktrees wt-antiwire-snap and wt-antiwire-t36 were clean and removed). Unmerged branches: m6-footprint's only commit 05db830 has the same patch-id (acac4666caca) as 549aa50 on main, so its content is on main and the branch was deleted. wip/okio-3.18.2-translation-2026-09-30 (981fa46, 54 files, about 10.9k lines, unique) was archived as annotated tag archive/wip-okio-3.18.2-translation-2026-09-30 on origin (remote tag peels to 981fa46) and then deleted. The /tmp paths of AC#3 (/tmp/aw-wt-foot, /tmp/aw-wt-lic, /tmp/aw-98, /tmp/m2-98) no longer existed (host reboot) and git worktree prune left no entries. Final state: git branch shows only main; git worktree list shows only the primary tree. DoD /code-review: not applicable, no code or file diff (git housekeeping only). Not touched, outside this task's local scope: origin still has branches audit-m6-followups, fix/protoadapter-short-constructors and m0-spikes.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Local repository pruned to main only: 17 branches deleted. 15 merged ones went with git branch -d. m6-footprint went with -D after confirming by patch-id that its commit is on main. The unique okio 3.18.2 spike was archived as tag archive/wip-okio-3.18.2-translation-2026-09-30 on origin, then its branch deleted with -D. Stale /tmp worktrees were already gone. Remote branches were left alone.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
