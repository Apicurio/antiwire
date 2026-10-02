---
id: TASK-23
title: Demonstrate upstream synchronization and security response before release
status: Done
assignee: []
created_date: '2026-09-29 09:23'
updated_date: '2026-10-02 12:40'
labels: []
milestone: m-11
dependencies:
  - TASK-17
documentation:
  - docs/decisions.md
  - docs/upstream-sync.md
priority: medium
ordinal: 20500
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Define and rehearse ongoing maintenance before publishing 0.1.0. Track the pinned Wire tag/commit, changelog and security advisories together, including relevant non-advisory fixes. Identify maintainers, triage ownership, response/escalation expectations and the supported upstream range. Rehearse an already-published security-relevant change using the actual port verification tools: detection, applicability triage, owner routing and simulated escalation, source-diff extraction, implementation/test adaptation and full parity validation. Record the exercise locally without sending real incident notifications. Do not make acceptance contingent on the first future post-7.1.0 release. Future releases use the same procedure when available.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 docs/upstream-sync.md records pinning, supported upstream versions, changelog/advisory monitoring, applicability triage, maintenance owners, update-lag and security-response targets, and escalation when owners are unavailable.
- [x] #2 A worked example replays an already-published applicable change that includes at least one security fix; it records detection, applicability triage, owner routing and simulated escalation, source diff, implementation/test adaptation, regression detection and successful full parity rerun on isolated classpaths.
- [x] #3 An automated watch or an owned manual checklist covers upstream tags and non-advisory security changes without assuming an advisory count is exhaustive.
- [x] #4 The synchronization and security-response procedure is demonstrated and reviewed before TASK-21 using local exercise records, without real incident notifications; the next actual upstream release is future maintenance work, not a prerequisite for completing this task.
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->

#2 - 2026-10-02 08:40 (UTC)
TASK-17 hand-off: the security regression inventory is docs/security-regression-inventory.md (10 in-scope items with source-linked evidence and named regression cases running as the security-corpus suite; 12 exclusion rows with reasons). Changelog and advisory monitoring should treat it as the authoritative list - it deliberately includes fixes no advisory covers (the pre-window recursion limit) and records two attribution conflicts (tag vs changelog).

#3 - 2026-10-02 12:40 (UTC)
Done. docs/upstream-sync.md is the procedure (pinning discipline, supported range, four
monitoring channels, triage table with DEC-6 exclusion rows and the twin check, owners and
escalation, update-lag targets marked PROPOSAL pending maintainer ratification, bump
mechanics with the case-map/ledger and security-corpus duties). The watch is
scripts/check-upstream.sh: git ls-remote tag comparison plus pin-integrity check, strict
and --security modes exiting nonzero when behind or when the snapshot needs review, wired
into verify.sh as the informational upstream-watch suite (PASS = the watch ran and
reported; state in the note). Drilled failure modes are recorded in the doc's section 8
(behind, pre-release ordering, moved/absent pin, malformed pins, network failure, empty
snapshot, unparsed tag names); today's real output is state=CURRENT with pin 7.1.0 the
newest upstream tag. The rehearsal (doc section 9) replays GHSA-7xpr-hc2w-34m9 end to end
against the real clone objects: detection greps, triage (port has both guards:
ProtoReader.java:285, ByteArrayProtoReader32.java:269), routing record (local only, no
notifications), upstream diff quoted, adaptation state and corpus assertions cited, full
parity rerun on isolated classpaths: VERDICT: all 12 ACTIVE suites passed.

#4 - 2026-10-02 12:40 (UTC)
DoD #1 review record: /code-review at high effort ran on the working diff (9 findings).
Fixed in the tree before committing:
- git ls-remote had no stall bound: now runs with http.lowSpeedLimit/http.lowSpeedTime so
  a blackholed connection fails in bounded time instead of hanging verify.sh.
- strict modes treated an unparsed tag name as clean CURRENT (hardening was --security
  only): both strict modes now exit 1; drilled with a synthetic remote carrying v7.2.0.
- the defensive unknown-state branch printed PASS in informational mode: now NOT_RUN, 3.
- an empty-but-successful ls-remote snapshot reported PIN_MOVED (absent): now
  could-not-run, drilled against a bare local remote.
- ls-remote lines that are not <hash> <ref> pairs were silently skipped: now counted and
  reported.
- verify.sh leaked WATCH_LOG on interrupt: the EXIT trap removes it; the watch block now
  documents why it cannot reuse run_suite ($LOG still holds the mvn log at that point).
Dispositioned without code change, recorded in docs/upstream-sync.md:
- "BEHIND has no mechanical consumer": the weekly owned checklist now includes the strict
  --security run (section 3); a scheduled CI job is recorded as a maintainer decision
  (it mails every watcher), not installed unilaterally.
- "network reachability now gates a green verify": intended per the task design (PASS
  means the watch ran and reported); the tradeoff and the offline behavior are stated
  explicitly in section 8.
Task-file finding (done-claim preceded the review record) resolved by this entry.
Final ./scripts/verify.sh after the fixes: VERDICT: all 12 ACTIVE suites passed.
/simplify: not applicable, documentation plus one reviewed shell script; no production
code touched by this task.

## Final Summary

Commit 599a9e6: docs/upstream-sync.md (10 sections: pinning discipline with worktree-at-pin enforcement, supported range exactly 7.1.0 forward, four monitoring channels with advisory counts explicitly never the source of truth, triage table incl. DEC-6 rows and the Swift-twin check, owners/escalation via the issue tracker with a security label, update-lag targets marked PROPOSAL pending maintainer ratification, bump mechanics with case-map/ledger/security-corpus duties) plus the GHSA-7xpr-hc2w-34m9 rehearsal (detection with verbatim git output, triage citing the port's fix sites and corpus cases, local-only routing/escalation record, upstream diff quoted, adaptation record, full parity rerun on isolated classpaths) and scripts/check-upstream.sh wired as the upstream-watch suite (PASS = ran and reported; TODAY real output: state=CURRENT, 203 tags parsed, pin integrity ok; nine failure modes drilled incl. odd-sort pre-releases). Review gate: high-effort code review, 9 findings fixed in-tree (ls-remote stall bound, strict-mode exits, unknown-state, empty-snapshot, skipped-line accounting, EXIT trap) or dispositioned in the doc (scheduled strict consumer left as maintainer decision; network gating intended). All 12 ACTIVE suites pass.
