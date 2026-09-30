---
id: m-10
title: "M4 Apicurio migration, delta-aware parity and footprint"
---

## Description

Integrate the port on a recorded Apicurio branch with limited documented caller and dependency changes. Capture upstream 6.4.0 versus 7.1.0 differences, then require antiwire to match upstream 7.1.0. Gate: applicable protobuf and serde integration tests pass, upgrade differences receive explicit acceptance, production protobuf classpaths contain no Kotlin, and measured clean-consumer/marginal footprint is accepted. TASK-18 and TASK-19.
