---
id: m-9
title: "M3 External oracle, Java code generation and security closure"
---

## Description

Run all applicable protoc 4.36.1 interoperability cases and Java-target compiler/profile cases. Compare Java goldens according to the reviewed namespace contract and compile/run generated output. Gate: protoc, compiler/profile, golden and applicable security regressions run as blocking CI checks, with no required case pending. Exclusions cover only declared non-ported functionality. TASK-15 through TASK-17.
