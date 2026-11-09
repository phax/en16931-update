# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Updates an EN 16931:2017 invoice to EN 16931:2026 in the **same syntax** (CII D16B -> D25A, UBL 2.1 -> 2.5,
Invoice and Credit Note). Java 17+ library plus picocli CLI. Not a converter and not a purifier: unknown and
extension content is preserved.

**Status:** plan items A0-A12 are done: dispatcher, settings, reporting, the three updaters, the CLI, the
preservation test, the coverage guard and the README. What is left is in `docs/todo.md` (open questions about the
2026 "Buyer assigned" / "Seller assigned" schemes, R6 and R7 confirmations, housekeeping).

## Build & Test Commands

```bash
mvn clean verify                 # Build and test (use verify, not install, to keep ~/.m2 clean)
mvn test -pl en16931-update -Dtest=UpdateVersionTest
mvn clean package -pl en16931-update-cli
java -jar en16931-update-cli/target/en16931-update-cli-full.jar --help
```

## Modules

- **en16931-update/** - core library, package `com.helger.en16931.update`
- **en16931-update-cli/** - picocli wrapper, shaded fat JAR `en16931-update-cli-full.jar`

## Key design decisions (details in the plan)

- The 2017 document is **read with the 2026 JAXB model** (ph-cii-d25a, ph-ubl25) and transformed typed; plan
  item A0 showed this loses nothing on 121 corpus files (only `xsi:schemaLocation` is dropped).
- Reading is schema-validating and strict: an input that is invalid against the 2026 XSD is rejected.
- Edition and syntax kind come from `en16931-basics` (BT-24 prefix, root element). Do not copy that code.
- No dependency on en16931-cii2ubl / en16931-ubl2cii, not even in test scope.
- **No 2026 Schematron exists yet.** Verification = XSD + XPath per rule + preservation test + idempotence +
  coverage guard. Never claim 2026 conformance beyond that.
- Do not invent code values the standard tables do not give (see Q9 in `docs/update-rules.md`).
