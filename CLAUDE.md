# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Updates an EN 16931:2017 invoice to EN 16931:2026 in the **same syntax** (CII D16B -> D25A, UBL 2.1 -> 2.5,
Invoice and Credit Note). Java 17+ library plus picocli CLI. Not a converter and not a purifier: unknown and
extension content is preserved.

**Status:** plan items A0-A7 done: dispatcher, settings, reporting and all three updaters (CII, UBL Invoice, UBL Credit
Note). Open: hardening (A8), preservation test (A9), coverage guard (A10), real CLI (A11), README (A12). The authoritative documents are
`docs/plan-1.0.0.md` (decisions, action items, progress log) and `docs/update-rules.md` (the rules R1-R9,
checks C1/C2 and the classification of every ID of the 2026 mapping table). Read both before changing code.

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
