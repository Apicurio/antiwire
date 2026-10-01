---
id: m-7
title: "M1 Runtime implementation and complete applicable parity"
---

## Description

Port the runtime on a compiling Java foundation with explicit ownership of encoding, required well-known types and helpers, then complete reflection and remaining behavior. Gate: all applicable pinned upstream runtime cases pass with reviewed mechanical adaptations, preserved scenarios and isolated implementation classpaths. Production artifacts have no Kotlin dependencies. TASK-6 through TASK-9.
