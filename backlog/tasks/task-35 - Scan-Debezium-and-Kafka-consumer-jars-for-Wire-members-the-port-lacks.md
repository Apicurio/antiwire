---
id: TASK-35
title: Scan Debezium and Kafka consumer jars for Wire members the port lacks
status: To Do
assignee: []
created_date: '2026-10-11 07:23'
labels:
  - adversarial-audit
  - compatibility
milestone: m-12
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Found lost by the 2026-10-11 adversarial audit. The TASK-33.2 brief asked to scan the jars of binary Wire consumers to drive the shim set (Confluent and Debezium converters and the Kafka jars). Only Confluent (kafka-protobuf-provider, kafka-schema-registry-client, kafka-protobuf-serializer 8.0.0: 111 of 111 resolve) and the Apicurio modules were scanned, because no Debezium or Kafka protobuf jars were in ~/.m2; the implementing agent wrote 'not scanned' in its report and nobody opened a task. The Companion failure that started TASK-33 came from exactly this class of consumer, so the scan is the only way to know whether more compiled consumers break. Fetch the jars with mvn dependency:get (versions as pinned in the Apicurio registry poms at 3620f08c), run the scanner, record the result.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Debezium converters (the protobuf-related ones Apicurio uses) and the protobuf-related Apache Kafka and Kafka Connect jars on Apicurio's classpath are resolved from Maven Central with checksums and scanned with scripts/scan-consumer-jars.py; the result (members referenced, members missing from the port) is recorded in docs/ or the task.
- [ ] #2 Every missing member Java can express is added or recorded as EXCLUDED with a reason in config/surface-baseline.tsv; DEC-15-style limits are listed.
- [ ] #3 Run /code-review at high effort on the final diff
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
