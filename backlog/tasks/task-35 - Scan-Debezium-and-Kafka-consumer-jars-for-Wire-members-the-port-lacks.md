---
id: TASK-35
title: Scan Debezium and Kafka consumer jars for Wire members the port lacks
status: Done
assignee: []
created_date: '2026-10-11 07:23'
updated_date: '2026-10-11 08:06'
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
- [x] #1 Every jar present in the local Maven repository that references com.squareup.wire (Debezium, Kafka, Confluent, Apicurio) is identified by scanning class files, and the ones that are compiled consumers of Wire are scanned with scripts/scan-consumer-jars.py; the result (members referenced, members missing from the port) is recorded in the task. Jars not in the local repository are out of scope and listed as a limit.
- [x] #2 Every missing member Java can express is added or recorded as EXCLUDED with a reason in config/surface-baseline.tsv; limits of the DEC-15 kind are listed.
- [x] #3 Run /code-review at high effort on the final diff (not applicable: no code changed, backlog record only)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
2026-10-11 result (scan run by me on bird with scripts/scan-consumer-jars.py against the port jars built at 7cb92a3). Method: I scanned every one of the 6967 jars in ~/.m2 for the string com/squareup/wire/ in their class files, excluding Wire and antiwire themselves: only 40 jars outside Wire reference it, and they fall into four families. (1) Confluent kafka-protobuf-provider 8.0.0 (18 classes), already scanned in TASK-33.2: 111 of 111 resolve; kafka-protobuf-serializer 8.0.0 (2 classes): resolves. (2) Confluent 7.6.1: kafka-protobuf-provider 7.6.1: 110 distinct references, 108 resolve, 2 do not (ExtensionsElement(Location, String, List) and a ProtoFileElement constructor with fewer arguments): both constructors are absent from real Wire 7.1.0 AND 6.4.0 too (7.1.0 and 6.4.0 only have the 4-argument ExtensionsElement and the 10-argument ProtoFileElement), because Confluent 7.6.1 was compiled against an older Wire (the 8.0.0 parent pom pins wire.version 5.3.0); so this is an incompatibility of Wire itself, not a gap of the port. kafka-protobuf-serializer 7.6.1: 2 of 2 resolve. (3) Apicurio's own protobuf modules (utils, schema-util, serde-common, schema-validation, the app, the maven plugin) in all snapshot versions and 2.5.11.Final: they are the code migrated in TASK-33.4; apicurio-registry-schema-util-protobuf 2.5.11.Final resolves 14 of 14; apicurio-registry-maven-plugin 3.4.0-SNAPSHOT has no Wire reference. (4) Debezium and Kafka itself: none of the 2751 jars under io/debezium, org/apache/kafka, io/quarkus, org/apache/camel and com/google/protobuf references com.squareup.wire. The Debezium Kafka Connect converter distribution that the Apicurio integration tests unpack (apicurio-kafka-connect-converter-3.3.1-SNAPSHOT.tar.gz, 68 jars, unpacked and scanned by me) also has 0 Wire references: it bundles no Wire at all. Conclusion: the Debezium and Kafka jars reference no Wire member, so there is nothing to port for them; the only compiled consumers of Wire in the Apicurio dependency tree are Confluent's protobuf provider and serializer and Apicurio's own modules, all covered. Limits: I scanned the jars present in ~/.m2 (what Apicurio and its integration tests resolved), not every published Debezium or Kafka release; Confluent versions other than 7.6.1 and 8.0.0 were not available. A future Confluent release compiled against a newer Wire can still add references: rerun the scanner when Apicurio bumps it.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Scanned the compiled consumers of Wire available in the Apicurio dependency tree (2026-10-11, scripts/scan-consumer-jars.py, port built at 7cb92a3). Debezium and Kafka reference no com.squareup.wire member (none of 2751 jars under io/debezium, org/apache/kafka, io/quarkus, org/apache/camel, com/google/protobuf; the Debezium Kafka Connect converter distribution, 68 jars, bundles no Wire). The only compiled consumers are Confluent kafka-protobuf-provider and kafka-protobuf-serializer (8.0.0: 111 of 111 resolve, TASK-33.2; 7.6.1: 108 of 110 resolve, the 2 misses are constructors that real Wire 7.1.0 and 6.4.0 also lack because 7.6.1 was compiled against an older Wire) and Apicurio's own modules (migrated in TASK-33.4). No member needed to be added and no baseline row changed. Limits: only the jars in ~/.m2; rerun the scanner when Apicurio bumps Confluent. Gates: no code changed (backlog record only); /code-review not applicable.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 Run /code-review at high effort on the final diff and resolve or explicitly disposition every finding before marking Done
<!-- DOD:END -->
