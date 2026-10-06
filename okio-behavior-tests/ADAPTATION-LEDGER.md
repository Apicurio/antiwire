# okio behavior adaptation ledger (TASK-4)

Status: TASK-4 deliverable, 2026-09-30. One row per adapted or new case in the okio
behavior suite. The shared files under okio-behavior-tests/src/kotlin are compiled twice:
against the port's translated okio package (wire-runtime-java) and against upstream
okio-jvm 3.18.2 in the isolated oracle module okio-behavior-oracle (scripts/check-okio-
behavior.sh verifies both executions and equal shared case counts). Upstream citations
are paths in the pinned okio clone at tag parent-3.18.2, commit
9403a094ac98d5a6ba8bad4ec3301399796fad67.

Rules used in this ledger (extending the translation-conventions rulebook section 6.3
for the okio corpus):

- R5: kotlin.test assertions rewritten to JUnit 5 (assertEquals argument order preserved;
  assertFailsWith becomes assertThrows with the same exception types).
- R6: the upstream internal Segment.SIZE constant replaced by its pinned value 8192 in
  shared files; cases needing Segment/SegmentPool internals beyond that moved port-only.
- R7: the Burst ByteStringFactory/BufferFactory harness parameter dropped; every case runs
  on the plain implementations (the factory matrix covers multiplatform variants the port
  does not ship).
- R8: construction spellings rewritten to forms both implementations compile:
  decodeHex inputs go through the local hex() helper (same digits, built via of()),
  encodeUtf8 inputs through utf8() over of(...) and String.getBytes(UTF_8), size()
  becomes the size property, and the index operator resolves via get(index).
- P1: port-only because upstream marks the static construction spelling deprecated-error
  for Kotlin, so it cannot compile against the pinned artifact in the oracle.
- P2: port-only because the case observes Segment/SegmentPool internals upstream declares
  internal to the okio module.
- N9: new spike-authored case (not an upstream adaptation); recorded for completeness so
  the ledger reconciles with the executed suites.
- R10: segment-layout observations run shared through a reflection helper over the pinned
  JVM field names (Buffer.head, Segment.pos/limit/next: @JvmField upstream, plain fields
  on the port), because upstream declares them Kotlin-internal and the oracle module is
  not a friend of the upstream module. The helper mirrors the semantics of upstream's own
  commonTest/okio/OkioTesting.kt segmentSizes() helper and the asserted size lists stay
  verbatim. Added during the 2026-09-30 review-fix pass so the upstream shared-suite
  segment-layout cases could move out of the port-only internals file.
- R11: Path construction in shared files goes through a local reflective path() helper
  invoking the JVM static Path.get(String): the port declares that static for Java while
  upstream compiles the String.toPath() companion extension to the same JVM static
  signature (@JvmStatic/@JvmName("get"), confirmed on the pinned okio-jvm 3.18.2
  bytecode), which Kotlin source resolves only under its declaration name toPath. Added
  during the 2026-09-30 bug-fix pass for the shared PathTest and FileSystemTest.
- P3: port-only because upstream marks the getByte member spelling deprecated-error for
  Kotlin, so the oracle cannot compile it; the behavior it covers also runs shared
  through the index-operator case.

Review discipline: every row's inputs, expected values, and error paths were taken from
the pinned upstream source at the cited lines; no assertion was changed to make a case
pass. Three real bugs found by these cases in the port (ByteString.utf8 bypassing
internalArray for segmented snapshots, equals comparing the data field instead of size(),
and a base64Url/size spelling mismatch) were fixed in the port, never in the tests.

Corrections recorded during the 2026-09-30 review-fix pass: the pre-existing
copyIntoWithTargetOffset row had drifted from the pin (it asserted
copyInto(0, target, 4, 8) over "WwwwabcdefghZzzz" where upstream ByteStringTest.kt:537
passes targetOffset=11, byteCount=5 and expects "WwwwXxxxYyyabcde"); the row was realigned
to the pin's inputs and expectation. The decodeBase64WithWhitespace port-only row had
dropped upstream assertion 7 (whitespace-only input decodes to an empty ByteString,
ByteStringTest.kt:374); the assertion was restored.

The 2026-09-30 bug-fix pass (six verified translation bugs in the port) added one
regression case per bug, each with its expectation verified against the pinned okio-jvm
3.18.2 artifact on this machine before landing, and two new shared files (PathTest,
FileSystemTest) which scripts/check-okio-behavior.sh now counts on both sides. Shared
totals moved from 85 to 98 cases, port-only upstream bodies from 8 to 9.

## Shared: ByteStringTest (50 cases, both implementations)

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| ByteStringTest.get | commonTest/kotlin/okio/ByteStringTest.kt:39 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:53  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.getByte | commonTest/kotlin/okio/ByteStringTest.kt:57 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:63  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.startsWithByteString | commonTest/kotlin/okio/ByteStringTest.kt:63 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:69  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.endsWithByteString | commonTest/kotlin/okio/ByteStringTest.kt:74 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:80  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.indexOfByteString | commonTest/kotlin/okio/ByteStringTest.kt:107 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:91  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.indexOfWithOffset | commonTest/kotlin/okio/ByteStringTest.kt:133 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:116  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.lastIndexOfByteString | commonTest/kotlin/okio/ByteStringTest.kt:152 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:127  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.equalsTest | commonTest/kotlin/okio/ByteStringTest.kt:189 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:156  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.equalsEmptyTest | commonTest/kotlin/okio/ByteStringTest.kt:197 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:164  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.equalsConstantTime | commonTest/kotlin/okio/ByteStringTest.kt:204 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:171  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.equalsConstantTimeEmptyTest | commonTest/kotlin/okio/ByteStringTest.kt:214 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:181  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.equalsNotConstantTime | commonTest/kotlin/okio/ByteStringTest.kt:219 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:186  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.utf8 | commonTest/kotlin/okio/ByteStringTest.kt:231 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:196  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.testHashCode | commonTest/kotlin/okio/ByteStringTest.kt:245 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:213  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.toAsciiLowerCaseNoUppercase | commonTest/kotlin/okio/ByteStringTest.kt:251 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:219  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.toAsciiAllUppercase | commonTest/kotlin/okio/ByteStringTest.kt:259 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:225  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.substring | commonTest/kotlin/okio/ByteStringTest.kt:271 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:229  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.substringWithInvalidBounds | commonTest/kotlin/okio/ByteStringTest.kt:280 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:238  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.encodeBase64 | commonTest/kotlin/okio/ByteStringTest.kt:296 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:246  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.encodeBase64WithoutPadding | commonTest/kotlin/okio/ByteStringTest.kt:307 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:257  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.encodeBase64Url | commonTest/kotlin/okio/ByteStringTest.kt:318 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:268  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.encodeBase64UrlWithoutPadding | commonTest/kotlin/okio/ByteStringTest.kt:329 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:279  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.toStringOnEmpty | commonTest/kotlin/okio/ByteStringTest.kt:377 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:290  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.toStringOnShortText | commonTest/kotlin/okio/ByteStringTest.kt:381 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:294  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.compareToSingleBytes | commonTest/kotlin/okio/ByteStringTest.kt:458 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:298  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.compareToMultipleBytes | commonTest/kotlin/okio/ByteStringTest.kt:478 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:318  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.copyInto | commonTest/kotlin/okio/ByteStringTest.kt:523 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:356  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.copyIntoFullRange | commonTest/kotlin/okio/ByteStringTest.kt:530 |  okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:363  | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringTest.copyIntoWithTargetOffset | commonTest/kotlin/okio/ByteStringTest.kt:537 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:370 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings: hex()/utf8() helpers over of(), size property, index operator); realigned to the pin during the review-fix pass, see Corrections | review-fix session, 2026-09-30 (drift correction) |
| ByteStringTest.startsWithByteArray | commonTest/kotlin/okio/ByteStringTest.kt:85 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:377 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.endsWithByteArray | commonTest/kotlin/okio/ByteStringTest.kt:96 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:388 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.indexOfByteArray | commonTest/kotlin/okio/ByteStringTest.kt:144 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:399 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.lastIndexOfByteArray | commonTest/kotlin/okio/ByteStringTest.kt:181 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:407 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.toAsciiStartsLowercaseEndsUppercase | commonTest/kotlin/okio/ByteStringTest.kt:263 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:415 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.toAsciiStartsUppercaseEndsLowercase | commonTest/kotlin/okio/ByteStringTest.kt:267 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:419 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.toStringOnTextWithNewlines | commonTest/kotlin/okio/ByteStringTest.kt:422 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:423 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.toStringOnData | commonTest/kotlin/okio/ByteStringTest.kt:430 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:431 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.toStringOnLongDataIsTruncated | commonTest/kotlin/okio/ByteStringTest.kt:444 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:445 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (hex()/utf8() construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.copyIntoWithSourceOffset | commonTest/kotlin/okio/ByteStringTest.kt:544 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:459 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings; upstream named/defaulted copyInto arguments written positionally) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.copyIntoWithAllParameters | commonTest/kotlin/okio/ByteStringTest.kt:551 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:466 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings; upstream named copyInto arguments written positionally) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.copyIntoBoundsChecks | commonTest/kotlin/okio/ByteStringTest.kt:558 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:473 | R5 (assertFailsWith becomes assertThrows, same exception types), R7 (factory parameter dropped), R8 (construction spellings; positional copyInto arguments) | review-fix session, 2026-09-30 (quality finding 1); inputs and error paths verbatim from the pin |
| ByteStringTest.copyEmptyAtBounds | commonTest/kotlin/okio/ByteStringTest.kt:581 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:496 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (construction spellings; positional copyInto arguments) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.ofCopy | commonTest/kotlin/okio/ByteStringTest.kt:593 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:507 | R5 (JUnit5 rewrite of kotlin.test), R8 (encodeToByteArray becomes toByteArray(UTF_8)) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.ofCopyRange | commonTest/kotlin/okio/ByteStringTest.kt:602 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:515 | R5 (JUnit5 rewrite of kotlin.test), R8 (ByteArray.toByteString(2, 9) extension becomes ByteString.of over copyOfRange(2, 2 + 9), same offset/byteCount values; the extension does not exist on the port) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| ByteStringTest.getByteOutOfBounds | commonTest/kotlin/okio/ByteStringTest.kt:611 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:523 | R5 (upstream try/fail/catch becomes assertThrows, same exception type), R7 (factory parameter dropped), R8 (hex() construction spelling, index operator via get) | review-fix session, 2026-09-30 (quality finding 1); inputs and error paths verbatim from the pin |
| ByteStringTest.toStringOnLongTextIsTruncated | commonTest/kotlin/okio/ByteStringTest.kt:392 | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:298 | R5 (JUnit5 rewrite of kotlin.test), R7 (factory parameter dropped), R8 (utf8() construction spelling) | TASK-4 port-fix session, 2026-09-30; moved out of Exclusions after the port gained the missing four-byte UTF-8 branch in ByteString.codePointIndexToCharIndex (see the note in Exclusions); inputs and expectations verbatim from the pin |
| ByteStringTest.toStringOnOverlongTwoByteEncodingIsHex | Utf8.kt:392 (process2Utf8Bytes rejects overlong two-byte sequences with the replacement code point) | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:337 | N9-style bug-fix regression (R5/R8 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 4) |
| ByteStringTest.toStringOnOverlongThreeByteEncodingIsHex | Utf8.kt:435 (process3Utf8Bytes rejects overlong three-byte sequences) | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:341 | N9-style bug-fix regression (R5/R8 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 4) |
| ByteStringTest.toStringOnThreeByteSurrogateEncodingIsHex | Utf8.kt:435 (process3Utf8Bytes rejects partial surrogates, 0xd800..0xdfff) | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:345 | N9-style bug-fix regression (R5/R8 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 4) |
| ByteStringTest.toStringOnMalformedByteAtCodePointCutIsText | Utf8.kt:207 (processUtf8CodePoints closure checks j++ == codePointCount before the replacement check, so a malformed byte as the 65th code point still renders text) | okio-behavior-tests/src/kotlin/okio/ByteStringTest.kt:349 | N9-style bug-fix regression (R5/R8 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 4) |

## Shared: BufferTest (41 cases, both implementations)

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| BufferTest.readAndWriteUtf8 | CommonBufferTest.kt:31 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:51  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.bufferToString | CommonBufferTest.kt:47 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:65  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.multipleSegmentBuffers | CommonBufferTest.kt:70 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:86  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.writeAllMultipleSegments | CommonBufferTest.kt:351 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:104  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.moveAllRequestedBytesWithRead | CommonBufferTest.kt:216 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:113  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.moveFewerThanRequestedBytesWithRead | CommonBufferTest.kt:229 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:126  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.indexOfWithOffset | CommonBufferTest.kt:242 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:139  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.byteAt | CommonBufferTest.kt:259 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:156  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.getByteOfEmptyBuffer | CommonBufferTest.kt:271 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:168  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyTo | CommonBufferTest.kt:360 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:173  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyToOnSegmentBoundary | CommonBufferTest.kt:371 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:184  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyToOffSegmentBoundary | CommonBufferTest.kt:389 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:202  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyToSourceAndTargetCanBeTheSame | CommonBufferTest.kt:407 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:220  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyToEmptySource | CommonBufferTest.kt:419 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:232  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.copyToEmptyTarget | CommonBufferTest.kt:427 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:240  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.snapshotReportsAccurateSize | CommonBufferTest.kt:435 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:248  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.snapshotSpansMultipleSegments | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:253  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.equalsSpanningSegments | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:266  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.cloneIsIndependent | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:275  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.readShortLe | BufferedSourceTest.kt:185 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:282  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.readLongLe | BufferedSourceTest.kt:289 |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:292  | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192 or internals cases moved port-only), R8 (construction spellings) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.readIntSplitAcrossMultipleSegments | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:309  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.writeAndReadVarintLikeDecimalRoundTrip | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:319  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.writeAndReadHexadecimalUnsignedLongRoundTrip | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:332  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.select | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:345  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.requireAndSkip | (none: spike-authored new case) |  okio-behavior-tests/src/kotlin/okio/BufferTest.kt:366  | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.clearIsEmpty | (none: spike-authored new case) | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:374 | N9 (new spike case: not an upstream adaptation; inputs and expectations derived from the pinned subset semantics) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferTest.equalsAndHashCodeEmpty | commonTest/kotlin/okio/CommonBufferTest.kt:289 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:382 | R5 (JUnit5 rewrite), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.equalsAndHashCode | commonTest/kotlin/okio/CommonBufferTest.kt:298 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:390 | R5 (JUnit5 rewrite), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.equalsAndHashCodeSpanningSegments | commonTest/kotlin/okio/CommonBufferTest.kt:311 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:402 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R8 (construction spellings); uses the bufferWithRandomSegmentLayout test support helper re-homed from commonTest/okio/OkioTesting.kt:37 | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.writePrefixToEmptyBuffer | commonTest/kotlin/okio/CommonBufferTest.kt:279 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:418 | R5 (JUnit5 rewrite), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.writePrefixDoesntSplit | commonTest/kotlin/okio/CommonBufferTest.kt:187 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:426 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.writePrefixDoesntSplitButRequiresCompact | commonTest/kotlin/okio/CommonBufferTest.kt:201 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:440 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.writeSplitSourceBufferLeft | commonTest/kotlin/okio/CommonBufferTest.kt:158 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:456 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.writeSplitSourceBufferRight | commonTest/kotlin/okio/CommonBufferTest.kt:173 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:471 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.moveBytesBetweenBuffersShareSegment | commonTest/kotlin/okio/CommonBufferTest.kt:113 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:485 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings); re-homed from the port-only BufferInternalsTest, see the port-only section note | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.moveBytesBetweenBuffersReassignSegment | commonTest/kotlin/okio/CommonBufferTest.kt:119 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:491 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings); re-homed from the port-only BufferInternalsTest | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.moveBytesBetweenBuffersMultipleSegments | commonTest/kotlin/okio/CommonBufferTest.kt:125 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:497 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R10 (segmentSizes read through the reflection helper), R8 (construction spellings); re-homed from the port-only BufferInternalsTest | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.readAllWritesAllSegmentsAtOnce | commonTest/kotlin/okio/CommonBufferTest.kt:331 | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:519 | R5 (JUnit5 rewrite), R6 (Segment.SIZE replaced by 8192), R8 (construction spellings); the MockSink test support (commonTest/okio/MockSink.kt) is carried trimmed to the recording surface this case uses (log and assertLog; no scheduleThrow scripting) | review-fix session, 2026-09-30 (quality finding 1); inputs and expectations verbatim from the pin |
| BufferTest.snapshotGetOnSegmentedSnapshot | jvmMain/kotlin/okio/SegmentedByteString.kt:80 with internal/SegmentedByteString.kt:132 (commonInternalGet backs both get and getByte on snapshots) | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:293 | N9-style bug-fix regression (R5/R8 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 1) |
| BufferTest.sourceIndexOfWithInvalidRangeThrows | internal/RealBufferedSource.kt:319 (require(fromIndex in 0L..toIndex) with message "fromIndex=$fromIndex toIndex=$toIndex") | okio-behavior-tests/src/kotlin/okio/BufferTest.kt:165 | N9-style bug-fix regression (R5/R8 spellings, Buffer wrapped in a real buffered source via Okio.buffer; the double-named Okio.buffer overload takes an explicit Source cast); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 6) |

## Shared: PathTest (6 cases, both implementations)

New shared file added in the 2026-09-30 bug-fix pass covering the Windows-volume parsing
and relativeTo bugs (port bugs 2 and 3). Adapted rows carry their upstream test inputs and
expectations verbatim; regression rows cite the implementation lines the port drifted
from. Construction goes through the R11 path() helper.

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| PathTest.windowsVolumeLetter | commonTest/kotlin/okio/PathTest.kt:154 | okio-behavior-tests/src/kotlin/okio/PathTest.kt:38 | R5 (JUnit5 rewrite of kotlin.test), R11 (reflective path() construction), property spellings (root/parent/volumeLetter/name/segments/isAbsolute/isRoot) | bug-fix session, 2026-09-30 (bug 2) |
| PathTest.windowsAbsolutePathWithVolumeLetter | commonTest/kotlin/okio/PathTest.kt:168 | okio-behavior-tests/src/kotlin/okio/PathTest.kt:51 | R5 (JUnit5 rewrite), R11 (reflective path() construction), property spellings | bug-fix session, 2026-09-30 (bug 2) |
| PathTest.windowsRelativePathWithVolumeLetter | commonTest/kotlin/okio/PathTest.kt:210 | okio-behavior-tests/src/kotlin/okio/PathTest.kt:64 | R5 (JUnit5 rewrite), R11 (reflective path() construction), property spellings | bug-fix session, 2026-09-30 (bug 2) |
| PathTest.windowsVolumeRootParsing | internal/Path.kt:97 (rootLength's volume-letter check `c !in 'a'..'z' && c !in 'A'..'Z'`) | okio-behavior-tests/src/kotlin/okio/PathTest.kt:83 | N9-style bug-fix regression (R5/R11 spellings); includes the non-letter negative "1:\x"; expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 2) |
| PathTest.resolveOnWindowsVolumeRoot | internal/Path.kt:206 (commonResolve returns an absolute or volume-lettered child unchanged) | okio-behavior-tests/src/kotlin/okio/PathTest.kt:102 | N9-style bug-fix regression (R5/R11 spellings); expectation verified against the pinned artifact | bug-fix session, 2026-09-30 (bug 2) |
| PathTest.relativeToRelative | commonTest/kotlin/okio/PathTest.kt:646 with internal/Path.kt:221-222 (require(root == other.root) is null-safe structural equality) | okio-behavior-tests/src/kotlin/okio/PathTest.kt:114 | R5 (JUnit5 rewrite), R11 (reflective path() construction); the helper computes a.relativeTo(b) (upstream's assertRelativeTo computes b.relativeTo(a), JvmTesting.kt:30), so the c/d pair's expected values sit on the opposite side of upstream's; the null-root vector "a/b".relativeTo("a") == "b" is the regression line | bug-fix session, 2026-09-30 (bug 3) |

## Shared: FileSystemTest (1 case, both implementations)

New shared file added in the 2026-09-30 bug-fix pass (port bug 5). The case needs a real
filesystem with symlink support, so it goes through FileSystem.SYSTEM over a JUnit
temporary directory rather than an in-memory fake (the M0 subset ships no shared fake);
both implementations expose the same FileSystem.SYSTEM spelling. Because the port returns
an eager Iterable where upstream returns a lazy Sequence, the case drains the result
inside the assertion block so both throw in the same place; the followSymlinks=false
control pins the non-cyclic listing.

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| FileSystemTest.listRecursivelyDetectsSymlinkCycleOnFirstVisit | internal/FileSystem.kt:91-99 (commonListRecursively seeds the stack with the starting directory at line 94, so the cycle is reported at the symlink on first visit) | okio-behavior-tests/src/kotlin/okio/FileSystemTest.kt:37 | N9-style bug-fix regression (R5 assertions, R11 path() construction); expectation verified against the pinned artifact on this machine | bug-fix session, 2026-09-30 (bug 5) |

## Port-only: ByteStringConstructionTest (6 cases)

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| ByteStringConstructionTest.encodeUtf8 | commonTest/kotlin/okio/ByteStringTest.kt:None | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:25 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringConstructionTest.encodeUtf8CachesTheString | commonTest/kotlin/okio/ByteStringTest.kt:None | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:42 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringConstructionTest.decodeHex | commonTest/kotlin/okio/ByteStringTest.kt:None | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:47 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringConstructionTest.ignoreUnnecessaryPadding | commonTest/kotlin/okio/ByteStringTest.kt:340 | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:55 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringConstructionTest.decodeBase64 | commonTest/kotlin/okio/ByteStringTest.kt:345 | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:60 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| ByteStringConstructionTest.decodeBase64WithWhitespace | commonTest/kotlin/okio/ByteStringTest.kt:367 | wire-runtime-java/src/test/kotlin/okio/ByteStringConstructionTest.kt:82 | R5 (JUnit5 rewrite), R7 (factory dropped), P1 (port-only: upstream marks the static spelling deprecated-error for Kotlin); the dropped upstream assertion 7 (whitespace-only "    " decodes to the empty ByteString, upstream line 374) was restored during the review-fix pass, see Corrections | review-fix session, 2026-09-30 (never-change finding restoration) |

## Port-only: BufferInternalsTest (3 cases)

The three moveBytesBetweenBuffers segment-layout cases left this section for the shared
BufferTest during the 2026-09-30 review-fix pass, when the R10 reflection helper made
them runnable against upstream okio-jvm in the oracle; their rows now live in the shared
section with the same upstream citations.

| Case | Original | Adapted | Rules applied | Reviewer |
|---|---|---|---|---|

| BufferInternalsTest.fillAndDrainPool | commonTest/kotlin/okio/CommonBufferTest.kt:88 |  wire-runtime-java/src/test/kotlin/okio/BufferInternalsTest.kt:24  | R5 (JUnit5 rewrite), P2 (port-only: observes SegmentPool.byteCount/MAX_SIZE, internal upstream, and the port-only file reads them directly; no uniform observation point exists for the oracle) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferInternalsTest.segmentLayoutsAfterSnapshot | (none: spike-authored new case) |  wire-runtime-java/src/test/kotlin/okio/BufferInternalsTest.kt:49  | N9 (new spike case over Segment/SegmentPool internals) | TASK-4 spike session, 2026-09-30; review by the session code-review gate pending at handback |
| BufferInternalsTest.snapshotGetByteOnSegmentedSnapshot | jvmMain/kotlin/okio/SegmentedByteString.kt:80 (internalGet backs both spellings; upstream jvmMain ByteString.kt:218-224 marks the getByte spelling deprecated-error for Kotlin) | wire-runtime-java/src/test/kotlin/okio/BufferInternalsTest.kt:65 | R5 (JUnit5 rewrite), P3 (port-only: the getByte spelling cannot compile against the pinned artifact; the behavior also runs shared through BufferTest.snapshotGetOnSegmentedSnapshot) | bug-fix session, 2026-09-30 (bug 1) |

## Port-only: SchemaSourceLoadingTest (6 cases, all spike-authored)

New spike code (not upstream adaptations) demonstrating the four schema-source origins
through the selected route; see the file header for the mirrored upstream loader call
shapes. Cases: inMemoryFileSystemLoadsSchemaSource, filesystemLoadsSchemaSource,
filesystemReaderHonorsBom, classpathResourcesLoadSchemaSource, zipLoadsSchemaSource,
pathOperationsMirrorLoaderUsage. Location:
wire-runtime-java/src/test/kotlin/okio/SchemaSourceLoadingTest.kt.

## Exclusions (translation-conventions 6.5 discipline)

- ByteStringTest.testHash (upstream ByteStringTest.kt:516): the M0 subset excludes the
  digest surface (md5/sha1/sha256/sha512/hmac*); no wire main or wire test use at wire
  tag 7.1.0. Not a DEC-6 exclusion; a subset-scope exclusion recorded here and in
  docs/decisions.md OPEN-1.
- ByteStringTest.toStringOnLongTextIsTruncated (upstream ByteStringTest.kt:392): no
  longer excluded. The review-fix pass of 2026-09-30 excluded it as a PARITY FINDING:
  green on upstream okio-jvm 3.18.2, red on the port, whose
  ByteString.codePointIndexToCharIndex
  (wire-runtime-java/src/main/java/okio/ByteString.java) decoded lead bytes 0xc0-0xef
  only and printed hex where upstream prints text for any ByteString whose first 64
  code points contain a four-byte UTF-8 sequence (upstream Utf8.kt process4Utf8Bytes).
  The port was fixed in TASK-4 scope on 2026-09-30: the four-byte branch now decodes
  0xf0-0xf4 leads and rejects overlong forms, partial surrogates, and code points above
  the Unicode maximum, mirroring process4Utf8Bytes. Proof re-run after the fix: the case
  is green on both the port and the oracle, and the shared case counts rose from 45 to
  46 on both sides. It now has a row in the shared ByteStringTest table. The 2026-09-30
  bug-fix pass then replaced the port's whole inline scan with the process2/3/4Utf8Bytes
  translation (four further verdict classes had diverged: overlong two- and three-byte
  encodings, three-byte surrogates, and a malformed byte at the 64-code-point cut; see
  the four toString rows above), so the scan's verdicts now match the pinned artifact
  for every input class probed.
- CommonBufferTest.fillAndDrainPool runs port-only (P2): SegmentPool.byteCount/MAX_SIZE
  are internal upstream and no uniform observation point exists for the oracle; the
  port-only copy keeps the upstream body. The moveBytesBetweenBuffers suite no longer
  runs here: it moved to the shared BufferTest via R10 (see the port-only section note).

