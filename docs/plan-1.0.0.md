# Plan: en16931-update 1.0.0

Status: **PLAN ONLY — nothing implemented. Q1–Q6 answered 2026-11-06 (section 4); Phase 0 may start.** · Created 2026-11-06

## 1. Goal

A Java library plus CLI that **upgrades an EN 16931:2017 invoice to EN 16931:2026 within the same
syntax** — it is neither a syntax converter nor a purifier.

| Syntax kind | Input (EN 16931:2017) | Output (EN 16931:2026) |
|-------------|-----------------------|------------------------|
| CII | D16B | D25A |
| UBL Invoice | 2.1 | 2.5 |
| UBL Credit Note | 2.1 | 2.5 |

BT-24 changes from `urn:cen.eu:en16931:2017` to `urn:cen.eu:en16931:2026`.

Same shape as its siblings: multi-module Maven build (`en16931-update` library +
`en16931-update-cli` picocli fat JAR), Java 17+, `com.helger` coordinates, Apache 2.0, Hungarian
notation per the global rules.

## 2. What the existing projects already give us

| Source | What we reuse |
|--------|---------------|
| `en16931-basics` 1.0.4 | `EEN16931Edition` (+ BT-24 detection by prefix, SAX based, never unmarshals), `EEN16931SyntaxKind`, `EEN16931DocumentType`, `CEN16931Syntax` (namespaces, root QNames), `EN16931CodeLists`, `ConversionHelper`. **No local copies** (rule in all three siblings). |
| `en16931-cii2ubl/docs/en16931-2017-syntax.md`, `…-2026-syntax.md` | The two three-way mapping tables. The 2017→2026 delta is derived from them, not from memory. |
| `en16931-cii2ubl/docs/plan-4.0.0.md` | Traps already paid for: §4.2 (no namespace detection), §4.4b (cardinality widenings), §4.4c (JAXB materialises `@listID` defaults), §4.4d (BT-218 defect in the source table), §4.4g (the "new in 2026" table is not a complete diff). **Read before Phase 2.** |
| `en16931-ubl2cii/…/UBLToCIIDispatcher` | Template for the dispatcher: detect document type + edition, route, write. |
| `en16931-purifier` | Closest architectural precedent: same-syntax in/out, one class per (edition × syntax kind), `EN16931Purifiers` lookup on top of the data-only basics enums. |
| Both siblings' test corpora | ~100 CII D16B files (EN examples, XRechnung 1.2.2 / 2.0.0 / 3.0.2, issue cases), ~17 UBL 2.1 files (Peppol + edge cases) — the input corpus, copied as test resources. |

## 3. Measured scope of the 2017→2026 delta

Computed by diffing the two mapping tables in `en16931-cii2ubl/docs/` (182 rows have a comparable
shape in both). The real work is much smaller than the 284-row size of the 2026 table suggests.

**Rewrites required (data exists in 2017, changes place or form in 2026):**

| # | Item | UBL 2.1 → 2.5 | CII D16B → D25A |
|---|------|---------------|-----------------|
| R1 | BT-24 | `cbc:CustomizationID` | `ram:GuidelineSpecifiedDocumentContextParameter/ram:ID` |
| R2 | BT-10 buyer reference (now 0..n) | `cbc:BuyerReference` → `cac:BuyerAssignedReference/cbc:BuyerReference` | `ram:BuyerReference` → `ram:BuyerReferenceID` |
| R3 | BG-1 notes, BT-21 / BT-22 | `cbc:Note` with `#code#` prefix → `cac:Annotation/{cbc:SubjectCode, cbc:AnnotationContent}` | unchanged paths (`IncludedNote/SubjectCode,Content`) |
| R4 | BT-9 on Credit Note | `cac:PaymentMeans/cbc:PaymentDueDate` → `cbc:DueDate` | — |
| R5 | BT-11 on Credit Note | `cac:AdditionalDocumentReference` → `cac:ProjectReference` | — |
| R6 | BT-32-2 national tax scheme | `cac:TaxScheme/cbc:ID` (any non-`VAT`) → fixed `LOC` | schemeID `FC` (check) |
| R7 | BT-122-1 / BT-122-1-1 supporting doc | add `cbc:DocumentTypeCode=916` + `@listID=1001` | `ram:TypeCode=916` — **absent from the 2026 "new" table**, found late in 4.0.0 (§4.4g) |
| R8 | Schema model | element widenings 1..1→0..n (`Name`, `StreetName`, `CityName`, …) — only API, not content | `Description`, `SubjectCode`, `InvoiceReferencedDocument` … — only API |

**Checks required (2026 made a sub-identifier mandatory that 2017 left optional) — cardinality 0..1 → 1..1:**
BT-29-1, BT-30-1, BT-46-1, BT-47-1, BT-60-1, BT-61-1, BT-71-1, BT-125-2.
An input with an ID but no `@schemeID` **cannot be upgraded without inventing data** → ErrorList
entry, never a silent guess. **Superseded by Q8 (2026-11-06): warning, element left as it is; `--strict` = error** — see `update-rules.md` §2/§3.

**Relaxations (nothing to do):** BT-34 / BT-49 endpoint 1..1→0..1, BT-117, BT-160, BT-91
`ProprietaryID` alternative, BT-41/BT-56 `DepartmentName` alternative, BT-161 `ValueMeasure`.

**New in 2026 (63 BTs + 7 BGs, BG-33…BG-39):** all optional or derivable-from-nothing → **never
emitted by the updater**. A 2017 document has no source data for them.

**To confirm in Phase 0 (not assumed):** that no 2017 value becomes *invalid* under 2026 code lists
or rules. No 2026 Schematron was available when 4.0.0 was written; check whether
`phive-rules-en16931` now ships one (it decides how strong the validation story in §7 can be).

## 4. Answered questions (2026-11-06)

| # | Question | Answer |
|---|----------|--------|
| Q1 | Preserve or purify? | **Preserve everything** schema-valid in the input (XRechnung extras, `PaymentChannelCode`, UBL extensions, attachments). Purification stays the job of `en16931-purifier`. |
| Q2 | BT-24 suffix (82 of 103 corpus files carry `#compliant#…`) | **Replace only the `urn:cen.eu:en16931:2017` prefix**, keep the suffix, warn that the CIUS is not 2026-aware; `--customization-id` / `--profile-id` overrides. |
| Q3 | Missing mandatory scheme IDs (8 checks) and ambiguous R5 | ~~Error + no output by default; `--lenient` downgrades~~ **Superseded by Q8/Q7** (see §10): default is *warning, element untouched*, `--strict` makes it an error; R5 follows `--cn-id-only-reference` (default `keep`). |
| Q4 | Input already EN 2026? | **Skip** (warn, no output / unchanged). `--on-2026 copy\|skip\|fail` stays as the switch, default `skip`. |
| Q5 | Names | `en16931-update` / `en16931-update-cli`, package `com.helger.en16931.update`, `1.0.0-SNAPSHOT`, repo `phax/en16931-update`. |
| Q6 | Oracle dependencies | Both siblings are released, **but the updater does not depend on them** — not even in test scope. The commutativity oracle is dropped (see D6). |

## 5. Locked decisions

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | Three updaters: `CIID16BToD25A`, `UBL21InvoiceToUBL25`, `UBL21CreditNoteToUBL25` | One per `EEN16931SyntaxKind`, as in the purifier |
| D2 | **Read the old document with the *new* JAXB model** (ph-cii-d25a, ph-ubl25), transform typed, write with the new marshaller | D16B instances validate against the D25A XSD (103/103 in 4.2); namespaces are byte-identical across CII releases and across UBL 2.x. Avoids two models and an identity copy of ~2 500 lines per syntax. Typed model also keeps schema element ordering correct on write — the main risk of a DOM approach. |
| D3 | D2 is a **hypothesis to prove in Phase 0**, not an assumption. Fallback: old model in, field-by-field copy (the converter pattern). | JAXB silently drops content unknown to the model; a UBL 2.1 file read with the 2.5 model must lose nothing. |
| D4 | Edition and syntax kind detected from BT-24 / root element via basics; `--en-version` style override like the siblings | Same reasoning as 4.0.0 D4; ~2% of the corpus has no usable BT-24 (legacy ZUGFeRD ids) |
| D5 | Every rewrite R1–R8 and check is a **named rule with an ID** reported in the ErrorList/log (`info` for applied rewrites) | Users upgrading invoices need an audit trail |
| D6 | **No dependency on `en16931-cii2ubl` / `en16931-ubl2cii`**, also not in test scope. Verification instead: (a) XSD validity of every output, (b) XPath assertions per rule R1–R8 and per check, (c) a **preservation test**: every element of the input that is not touched by a rule is present unchanged in the output (this is what Q1 demands), (d) idempotence (D7), (e) the coverage guard (A10), (f) 2026 Schematron if A2 finds one | Keeps the artifact light and independent. The cost: no independent second implementation, so rule correctness rests on the mapping tables — hence (c) and the hand-written per-rule expectations in A1. |
| D7 | Idempotence: `update(update(X))` = `update(X)` | Cheap, catches double rewrites (R3 prefix handling) |
| D8 | No unmarshalling for detection (basics rule) | Correct model unknown at that point |

## 6. Target structure

```
en16931-update/
  pom.xml                          en16931-update-parent-pom (parent-pom 3.1.0, same BOM imports as cii2ubl)
  en16931-update/                  library
  en16931-update-cli/              picocli, shade -> en16931-update-cli-full.jar
  docs/plan-1.0.0.md               this file
  docs/update-rules.md             R1…Rn / checks, derived from the two mapping docs (Phase 1)
  CLAUDE.md  README.md             README carries "News and noteworthy" (no wiki checkout exists)

com.helger.en16931.update
  AbstractEN16931Updater<IMPLTYPE>   settings (fluent), ErrorList helpers, rule reporting
  EN16931UpdateDispatcher            detect edition + syntax kind -> route -> write (cf. UBLToCIIDispatcher)
  EN16931Updaters                    lookup syntax kind -> updater (cf. EN16931Purifiers)
  UpdateVersion                      version constants from properties
com.helger.en16931.update.cii        CIID16BToD25AUpdater
com.helger.en16931.update.ubl        UBL21InvoiceToUBL25Updater, UBL21CreditNoteToUBL25Updater,
                                     shared helpers for R1/R2/R3/R6/R7 where the UBL types allow it
```

Dependencies: `en16931-basics`, `ph-commons`/`ph-xml`/`ph-jaxb`, `ph-cii-d25a`, `ph-ubl25`;
D3 fallback additionally `ph-cii-d16b`, `ph-ubl21`. Test scope: `phive-rules-en16931` (validate
the 2017 **inputs** and, if available, the 2026 outputs), `ph-cii-d16b`, `ph-ubl21` (to read inputs
with the old model for the preservation test). No converter dependencies (D6).

Naming rule from `behaviour.md`: public API change ⇒ News entry in `README.md` for the current
`-SNAPSHOT` ("v1.0.0 - work in progress", one sentence per line).

## 7. Action items

Each fits one working session; `mvn clean test` green before an item counts as done; one commit per
item, **only when asked** (global rule), prefixed `[1.0.0 A<n>]`.

### Phase 0 — Prove or kill the premises (no production code)

- [x] **A0 — Round-trip premise test** · scratch test only · **done 2026-11-06, D2 stands — see §10**
  - Read all ~100 CII D16B files with the D25A marshaller and write them back; XML-diff against
    the original ignoring whitespace/ordering of namespace declarations. Same for the UBL 2.1 files
    with the UBL 2.5 marshaller.
  - **Done when:** either zero information loss (→ D2 stands) or a list of exactly what is lost
    (→ D3 fallback, plan effort for Phase 2 grows by ~2 sessions).
- [x] **A1 — Re-derive the delta** · `docs/update-rules.md` · **done 2026-11-06 — see §10; Q7–Q9 open**
  - Redo the §3 diff for **UBL Credit Note** and the CII column separately, and for every
    sub-identifier row; resolve each `(same)` and each notation-only difference (52 of 58 were
    notation in 4.0.0). Record each rule with BT, old path, new path, example.
  - Cross-check against 4.0.0 §4.4g (rows missing from the "new" table).
  - **Done when:** the list of rules is closed and every row of the 2026 table is classified:
    carried / rewritten / checked / new-not-emitted. A `MappingCoverageTest` analogue enforces it.
- [x] **A2 — Test inventory & 2026 validation story** · **done 2026-11-06 — see §10** — find out whether 2026 Schematron exists in
  `phive-rules-en16931`; count usable input files; decide whether D6 needs file-by-file expected
  fixtures.

### Phase 1 — Skeleton

- [x] **A3 — Project scaffolding** · **done 2026-11-06**: parent pom, two modules, license header plugin, `.gitignore`,
  CI workflow copied from siblings, `UpdateVersion`, CLAUDE.md. Compiles, empty tests green.
- [x] **A4 — Core API** · **done 2026-11-09**: `AbstractEN16931Updater`, rule-report type, `EN16931UpdateDispatcher`
  detection + routing (edition, syntax kind, already-2026 handling Q4), error cases (BT-24 missing,
  unknown edition, not-well-formed, unsupported root) — unit tested without any rewrite logic.

### Phase 2 — Updaters (one syntax each, smallest first)

- [x] **A5 — CII** · **done 2026-11-09**: R1, R2, R3-check, R6, R7 + the scheme-id checks; read D16B via D2, write D25A.
  Run over the whole CII corpus; XSD valid output; per-rule XPath assertions.
- [x] **A6 — UBL Invoice** · **done 2026-11-09**: R1, R2, R3 (the `#code#` prefix parser — **careful**: only a leading
  `#…#` token; BT-127 line notes must not move), R6, R7 + checks.
- [x] **A7 — UBL Credit Note** · **done 2026-11-09**: everything from A6 plus R4, R5.
- [x] **A8 — Hardening** · **done 2026-11-09**: XXE/DTD protection test (ubl2cii has `XXEProtectionTest` to copy),
  idempotence (D7), `@listID` default trap (4.4c) on every `qdt:` code copied, `@format`
  preservation, extension content (Q1), huge-file smoke.

### Phase 3 — Verification

- [x] **A9 — Preservation test (D6c)** for CII and both UBL kinds: diff input vs output over the
  whole corpus; the only differences allowed are those named by a rule R1–R8. Unexpected loss or
  change fails the test. Also run `phive-rules-en16931` on the 2017 inputs to confirm the corpus is
  itself conformant.
- [x] **A10 — Coverage guard**: every row of `en16931-2026-syntax.md` is classified in
  `update-rules.md`; test fails if a row is unclassified. Prevents the BT-122-1 miss.

### Phase 4 — CLI and docs

- [x] **A11 — CLI**: `-t/--target`, `--output-suffix` (default `-2026`), `--en-version`-style
  override, `--on-2026`, `--strict`, `--cn-id-only-reference`, `--customization-id`, `--profile-id`, `--verbose`,
  `--disable-wildcard-expansion`, `source files 1..*` — mirroring the sibling options exactly.
  **Don't** repeat the sibling NPE with unset optional values (4.4f). `_test.sh` / `_test.cmd`.
- [ ] **A12 — Docs**: README (usage, CLI, rules table, limits), News and noteworthy
  `v1.0.0 - work in progress`, CLAUDE.md, javadoc `@since 1.0.0`.

## 8. Estimate

| Phase | Sessions |
|-------|----------|
| 0 Premises | 2 |
| 1 Skeleton | 1–2 |
| 2 Updaters | 3–4 |
| 3 Verification | 1–2 |
| 4 CLI, docs | 1–2 |
| **Total** | **~8–12 sessions** — about half of 4.0.0, because there is no 214-row port |

If A0 fails (D3 fallback), add ~2 sessions.

## 9. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| 2017 CN `AdditionalDocumentReference` cannot reliably be told from a project reference (R5) | Wrong move | Only move when unambiguous, warn otherwise (Q3) |
| Preserve-everything (Q1) means unknown/extension content may collide with new 2026 elements | Invalid or misleading output | XSD validation of every output; A8 extension tests |
| `#code#` note prefix (R3) misparsed | Text corruption | Strict leading-token regex, round-trip test, idempotence |
| JAXB silently drops unknown content (D2) | Data loss | A0 diff test; validate on read |
| No 2026 Schematron, no second implementation | Subtle wrong output | D6: XPath per rule, preservation test, idempotence, coverage guard; hand-derived expectations in A1 |
| 2026 rules tighten something not visible in the mapping tables (code lists, BR rules) | Output "valid" but non-compliant | A2 research; document as known limitation in README |
| Mapping tables contain source defects (BT-218 precedent) | Bug copied from the standard | Treat the tables as input to check, not oracle; the converters' known corrections apply |

## 10. Progress log and findings

### A0 — round-trip premise (2026-11-06) — D2 confirmed

Scratch harness (not in the repo): read with the **target** model (`CIID25ACrossIndustryInvoiceTypeMarshaller`,
`UBL25Marshaller.invoice()/creditNote()`, schema validation on, collecting handler), write back, parse both
sides with namespace aware DOM and compare element by element (QName sequence, attributes, trimmed text).

| Corpus | Files | Read errors | Write failures | Identical | Differing |
|--------|-------|-------------|----------------|-----------|-----------|
| CII D16B read as D25A (EN examples, XRechnung 1.2.2 / 2.0.0 / 3.0.2, issue + technical cases) | 103 | 0 | 0 | 73 | 30 — **all** only `xsi:schemaLocation` on the root |
| UBL 2.1 read as UBL 2.5 (Peppol Invoice + Credit Note, coverage and edge cases; 3 Credit Notes) | 18 | 0 | 0 | 18 | 0 |

Consequences:

- **No element, attribute value or text is lost or changed**, no element is reordered, and no JAXB schema
  default (the `@listID` trap of 4.4c) is *written* — it only shows up through getters. The D3 fallback
  (old model in, field-by-field copy) is **not needed**.
- `xsi:schemaLocation` is dropped by JAXB. Harmless (it was a relative path to the D16B XSD and would be
  wrong for 2026 anyway) — record it as an intentional, documented difference in the preservation test (A9).
- Negative probe: an unknown element and an unknown attribute in a UBL file make `read` return `null`
  (cvc errors). So **reading is strict**: a 2017 input that is not valid against the 2026 XSD is rejected
  with an ErrorList entry rather than silently repaired. This is the desired behaviour for Q1/Q3 but must be
  documented in the README.
- Limits of the evidence: only 3 Credit Notes and no UBL file with `ext:UBLExtensions` or embedded
  attachments in the UBL corpus. A8 must add hand-written probes for these (Q1 "preserve everything").
- Not yet checked: that the real XSDs behind `ph-ubl21` and `ph-ubl25` accept the same inputs for
  *invalid-in-2.5-but-valid-in-2.1* corner cases — none found in the corpus, none expected (UBL 2.x minors are
  backward compatible), but A1 should grep the 2.5 release notes for removed/restricted elements.

### A1 — rule list closed (2026-11-06) — three corrections to §3 and one new decision need

Full rule list, evidence and the classification of all 299 IDs of the 2026 table: [update-rules.md](update-rules.md).

| Correction to §3 | Detail |
|------------------|--------|
| R7 is **UBL only** | CII D16B already requires `ram:TypeCode` `916` for BG-24 (corpus: 53 × `916`). |
| New rule **R9** | `cbc:UBLVersionID` 2.1 → 2.5 (found by reading the converters, not the tables). |
| R5 and R7 share an ambiguity | A 2017 Credit Note reference without any type discriminator is BT-11 *or* an ID-only BG-24 → Q7. |
| The 8 "checks" are **not edge cases** | Measured: 94 of 103 CII and 18 of 18 UBL files have at least one unscheme'd party identifier; "error, write nothing" (Q3) would reject virtually all real input. For CII the unscheme'd `ram:ID` is not a BT in 2026 at all. → Q8/Q9. |

Open, blocking A5–A7 (not A2–A4): **Q7** Credit Note ID-only references, **Q8** default behaviour for C1,
**Q9** the 2026 codes for the "Buyer assigned" / "Seller assigned" party identifier schemes. Details and
options in `update-rules.md` §3. Q3 as answered stays valid for C2 (attachment filename: no corpus hit).

### A2 — inventory and validation story (2026-11-06)

- **No 2026 Schematron exists** in the newest release `phive-rules-en16931` 4.6.4 (checked: the jar's
  `external/schematron/` stops at EN 16931 validation artefacts 1.3.17; the sources register no 2026 VESID).
  The 4.6.5-SNAPSHOT is not available locally (only `.lastUpdated` markers), so I could not check it.
  → The verification set of D6 stands as written; item (f) is "not available", and the README must say
  that 2026 conformance of the output is **not** machine-verified beyond XSD and the per-rule tests.
- **2017 Schematron is available** (1.3.x up to 1.3.17) → A9 can validate the *inputs* before the update.
- **Input corpus** (copied as test resources, nothing downloaded): 103 CII D16B + 18 UBL 2.1 (13 Invoice,
  3 Credit Note, plus 2 Invoice edge cases) = 121 files; 2 legacy ZUGFeRD files have no usable BT-24.
  Gaps to fill by hand in A5–A8: UBL Credit Note with `ProjectReference`-candidate references (R5/Q7),
  UBL header `cbc:Note` with `#code#` prefix (R3), `cbc:UBLVersionID` (R9), non-`VAT` `TaxScheme/ID` (R6),
  UBL without `schemeID` variants (all corpus files have C1 hits already), `ext:UBLExtensions`.

### Decisions Q7–Q9 (2026-11-06, after A1)

- **Q7** agreed: `--cn-id-only-reference project|document|keep`, default `keep`.
- **Q8** agreed: warn and leave by default, `--strict` = error.
- **Q9** user: "meant literally" — the schemes "Buyer assigned" / "Seller assigned" are taken as the literal
  scheme identifier text. Recorded as an **unverified working assumption**, off by default; the open
  sub-questions (spelling, CII target, which BT gets which) are in `update-rules.md` §3. Insertion is limited
  to BT-29 (Seller assigned) and BT-46 (Buyer assigned); everything else warns.
- A2 note: no 2026 validation artefacts exist yet (confirmed by the user); the plan never relied on them.
  A9 keeps using the 2017 Schematron on the inputs only.

### A3 — scaffolding (2026-11-06)

Parent pom `1.0.0-SNAPSHOT` (same BOM versions as the siblings: ph-commons 12.5.3, ph-ubl 10.2.1,
ph-cii 4.1.3, en16931-basics 1.0.4), modules `en16931-update` and `en16931-update-cli`, `UpdateVersion` +
test, picocli stub `EN16931UpdateCLI` (`--help`/`--version` only), license templates, maven workflow
(copied from the siblings; the `release-to-slack` workflow was copied too - drop it if not wanted), README
with News and noteworthy, CLAUDE.md. `mvn clean install` is green (1 test); the shaded jar runs.
The existing `.gitignore` was replaced by the siblings' (superset of the old content).

### A4 — core API (2026-11-09)

Added to `com.helger.en16931.update`: `EUpdateRule` (R1–R7, R9, C1, C2), `EAlready2026Handling`
(`skip` default / `copy` / `fail`), `EUpdateStatus`, `UpdateResult`, `AbstractEN16931UpdaterBase`
(settings `strict`, `customizationID`, `profileID`; `reportApplied` = info with the rule ID as error ID,
`reportWarning`, `reportCheckFailure` = error if strict else warning), `AbstractEN16931Updater`,
`EN16931UpdateDispatcher` (syntax kind from root element, source edition from BT-24 or forced, routing,
`writeDocument`), and the three updater **skeletons** (`cii.CIID16BToCIID25AUpdater`,
`ubl.UBL21InvoiceToUBL25InvoiceUpdater`, `ubl.UBL21CreditNoteToUBL25CreditNoteUpdater`) that only read with
the 2026 model — **no rule is applied yet, so BT-24 of the output is still the 2017 value** until A5–A7.
15 tests green (routing of all three kinds + schema-validated write, skip/copy/fail, missing BT-24,
forced edition on a legacy ZUGFeRD file, unsupported root, missing file, schema-invalid input rejected,
settings handed over).
The full corpus (103 CII + 18 UBL) was copied to `en16931-update/src/test/resources` (7.7 MB).
A test caught one defect on the way: the skip warning passed a `null` rule (NPE).

### A5 — CII updater (2026-11-09)

Implemented in `cii.CIID16BToCIID25AUpdater`: **R1** (prefix `urn:cen.eu:en16931:2017` replaced, suffix kept
with a warning; `--customization-id` / `--profile-id` overrides; a non-2017 BT-24 — only possible with a
forced source edition, e.g. legacy ZUGFeRD — is replaced by the 2026 id with a warning naming the old value),
**R2** (`ram:BuyerReference` → `ram:BuyerReferenceID`, no duplicate if the value is already there),
**C1** (reported per occurrence: `ram:ID` under Seller/Buyer/Payee/ShipTo trade party, `ram:GlobalID` and
`SpecifiedLegalOrganization/ram:ID` without scheme; element untouched; error if `strict`), **C2** (header
attachment without `@filename`).

- D25A has **both** `ram:BuyerReference` and `ram:BuyerReferenceID` — which is why a D16B document reads
  into the D25A model at all.
- Verification (D6): the whole 103-file corpus is updated and written (XSD-validated on write); BT-24, R2 and
  the C1/C2 counts are checked against **independent XPath counts on the input document**, not against the
  updater's own code. 7 tests in `CIID16BToCIID25AUpdaterTest`, 22 in total.
- **Mutation probes** were run to prove the tests can fail: skipping R2, dropping the BT-24 suffix and
  disabling the `GlobalID` check each make a test fail. The third one initially did *not* — no corpus file has
  a `GlobalID` without scheme or an attachment without filename — so those two cases are covered by
  hand-modified copies of `CII_example2.xml` (`testGlobalIDWithoutScheme`, `testC2MissingFilename`).
- Not done on purpose: inserting the Q9 "assigned" schemes (still unverified, off by default). It is not in A5.

### A6 — UBL Invoice updater (2026-11-09)

`ubl.AbstractUBL21ToUBL25Updater` holds the rules that Invoice and Credit Note share (they work on the common
`cac`/`cbc` parts, since the two document classes are unrelated), `UBL21InvoiceToUBL25InvoiceUpdater` applies
**R1, R2, R3, R6, R7, R9, C1, C2**. The BT-24 logic moved into `AbstractEN16931UpdaterBase`
(`getUpdatedSpecificationIdentifier`) and is shared with the CII updater, whose 7 tests stayed green.

Decisions made while implementing (not in the earlier plan text):
- **R3**: a note is `#code#text` only if the code is non-empty and contains no `#` or line break; `##text`,
  `#text` without closing `#` and plain text keep the whole value as `AnnotationContent` and get no
  `SubjectCode`. All header `cbc:Note` become annotations; payment-term and line notes are not touched.
- **R6** applies to the **Seller** only (BT-32); other `PartyTaxScheme` entries are not touched. The old
  value goes into the info message.
- **R7** applies to a reference with neither `cbc:DocumentTypeCode` nor `cbc:DocumentType`; any existing
  code (130 or other) is left alone. `listID="1001"` is emitted (open point 5 in `todo.md` — still to confirm).
- **R9**: `2.1` → `2.5`; any other value is left alone with a warning; an absent element stays absent.
- **R2** adds no duplicate if the value is already in a `cac:BuyerAssignedReference`.

Verification: all 15 corpus invoices (13 Peppol/coverage + edge) are updated and written with XSD validation,
every rule's effect is compared with **independent XPath counts on the input**. The corpus has no `#code#`
note, no non-VAT Seller scheme, no `UBLVersionID`, no unscheme'd Payee / delivery location, so these are
covered by hand-modified copies of `base-example.xml` (`testHandBuiltCases`, `testPayeeAndDeliveryIdentifiers`).
**Nine mutation probes** (R2, R3 ×2, R6, R7 ×2, R9, C1 payee/legal entity/delivery) were run; one initially
survived (Payee, no corpus data) and led to the extra test — all are caught now. 29 tests in total.

### A7 — UBL Credit Note updater (2026-11-09)

`UBL21CreditNoteToUBL25CreditNoteUpdater` applies R1, R2, R3, R6, R9, C1, C2 through the shared base class and
adds **R4**, **R5** and the R7 classification. New setting `ECreditNoteIDOnlyReferenceHandling`
(`keep` default / `project` / `document`) in `AbstractEN16931UpdaterBase`, handed over by the dispatcher.
Exact semantics are in `update-rules.md` (R4, R5 rows). In `project` mode a reference is moved only if it is the
single ambiguous one, carries nothing but its ID and no `cac:ProjectReference` exists — otherwise it warns.

Verification: the 3 corpus Credit Notes (R1–R3, R6, C1/C2 against independent XPath counts) and 11 hand-built cases
(R4 single/differing/existing `cbc:DueDate`; R5/R7 in all three modes, several ambiguous references, an existing
project reference, BT-18 `ATS`, typed and described references, a reference with more than an ID). **Ten mutation
probes** (R4 ×3, R5 ×5, R7 described-reference detection, KEEP warning) are all caught. 41 tests in total.
Still unverified assumption: UBL 2.5 `cbc:DueDate` sits between `IssueTime` and `TaxPointDate` (read from the XSD).

### A8 — hardening (2026-11-09)

`HardeningTest` (10 tests):
- **XML attacks**: external entity (file) for CII, UBL Invoice and UBL Credit Note, external parameter entity,
  billion laughs (30 s timeout) — all rejected, nothing leaks. A **control** with a harmless *defined* internal
  entity is rejected too, which proves it is the DOCTYPE that is refused (`DOMReader`'s default) and not merely
  an undefined entity. The rejection message is only "Failed to read ... as an XML document": the reason is not
  surfaced by `DOMReader`. A caller that hands over its own `Node` is responsible for its own parser settings.
- **Idempotence** over all 121 corpus files: running the updater on its own written output yields byte-identical
  XML; the dispatcher skips the result.
- **Defect found by this test:** the second pass replaced `urn:cen.eu:en16931:2026#compliant#...` by the bare
  2026 identifier, i.e. **lost the CIUS suffix**. `getUpdatedSpecificationIdentifier` now returns an already-2026
  value unchanged.
- **`ext:UBLExtensions`** with foreign content (namespaces, attributes, repeated children, escaped text) is
  preserved on Invoice and Credit Note.
- **Attachments** are byte-identical (decoded) — 45 CII attachments of the corpus and the UBL coverage invoice.
- **Attribute trap**: for every CII file the number of `@listID`, `@listAgencyID`, `@listVersionID`, `@schemeID`,
  `@schemeAgencyID`, `@unitCode`, `@currencyID` and `@format` attributes is the same before and after, so no JAXB
  schema default is ever written.
- **Large document**: 4000 lines / 5.3 MB read, updated, written and validated in about 0.4 s.
Two test inputs of mine were wrong at first (`ext:ID` must be `cbc:ID`; the base invoice has several lines) — the
code was not at fault there. 51 tests in total.

### A9 — preservation test and input conformance (2026-11-09)

**Preservation** (`PreservationTest`, `XMLDiffer`): each of the 121 corpus inputs plus one hand-built Invoice (every
Invoice rule) and one hand-built Credit Note (R4) is updated and compared with the output element by element.
Every difference must match an entry of a **rule-tagged allow list** (path + kind), and per rule the number of
differences must equal the number of rewrites the updater **reported** (R1: exactly one BT-24 text change; R6, R9:
text changes; R7: added elements; R2, R3, R4: one element lost and one added per rewrite; C1, C2, R5: must change
nothing). So nothing is lost silently and nothing is reported without a change. The one non-rule difference is the
dropped `xsi:schemaLocation` (A0). A damage test proves the check can fail (changed text, changed text, lost
element, added attribute); two injected bugs (R2 also dropping the buyer party, an extra `setIssueDate(null)`) are caught.
Limit: **added subtrees are not compared** by this test (only that they exist) — the content of moved elements
(R2, R3, R4, R7) is covered by the per-rule XPath tests of A5–A7. The differ pairs same-named siblings by position,
which is exact because every rule removes all or the last of a kind; it had a flaw of its own (a lost duplicate
reported a bogus order change) that its self-test found.

**2017 Schematron on the inputs** (`CorpusConformanceTest`, phive-rules-en16931 4.6.4, artefacts 1.3.17, test
scope): **88 of 121 inputs are valid — all 18 UBL, but only 70 of the 103 CII files.** The 33 invalid CII files are old
test inputs of the sibling projects (BR-53 in 20 XRechnung 1.2.2 files, CII-SR-461 "only one TaxPointDate" in 6, BR-CL-06
in 5, BR-27, CII-DT-097 and the 21 errors of `CII_example1a`). The updater preserves them as they are. The numbers
are pinned; if they change, the Schematron version changed. The Schematron is deliberately **not** run on the
outputs: its first step is the 2017 XSD, which an updated document fails by design (checked: 79 of the 88 outputs are
rejected by it, all because of 2026-only elements like `ram:BuyerReferenceID` or `cac:Annotation`). 62 tests in total.

### A10 — coverage guard (2026-11-09)

`MappingCoverageTest` reads the two mapping tables (copied into `docs/`, provenance in `update-rules.md`) and the
classification of `update-rules.md` §5, and fails unless: every ID of the 2026 table is classified and nothing else
is; every class is valid and names an existing `EUpdateRule`; an ID of the 2017 table is never NEW/IMPLICIT and one that
is not in it is never CARRIED (BG-1..BG-32 and the notes excepted); every REWRITE/CHECK ID is **named in the code** of the
updaters (exact match — BT-9 is not satisfied by BT-91 or BT-9-1) and no NEW ID is; every `EUpdateRule` (R9 excepted, it
has no row) is used in the table and described in the rule document. The checking function is separate and has five
self-tests with deliberately broken inputs; probes against the real files (row removed, NEW turned REWRITE without code)
fail as intended.

**Found immediately:** two discriminator rows that exist in both tables (`BT-17 vs BT-18 vs BG-24`,
`BT-110 vs BT-111`) were wrongly labelled CARRIED instead of NOTE — fixed (CARRIED 160, NOTE 14).
Limits: "named in the code" is satisfied by a javadoc mention, so the guard proves the ID was *considered*, not that a test
covers it — the per-rule tests of A5–A7 and the preservation test do that. 68 tests in total.

### A11 — CLI (2026-11-09)

`EN16931UpdateCLI` (picocli, shaded `en16931-update-cli-full.jar`): `-t/--target` (default `.`), `--output-suffix` (default
`-2026`), `--source-edition 2017|2026` (forces the source edition; for documents without a usable BT-24), `--on-2026
skip|copy|fail` (default `skip`), `--strict`, `--cn-id-only-reference keep|project|document` (default `keep`),
`--customization-id`, `--profile-id`, `--verbose`, `--disable-wildcard-expansion`, source files/directories `1..*`
(wildcards relative to the working directory, as in the sibling tools). `_test.sh` / `_test.cmd` added.

Differences from the sibling CLIs, on purpose:
- **Exit code**: 1 if at least one file failed, 0 otherwise (the siblings always return 0). A skipped already-2026 file and
  an ignored non-existent file are not failures. A wrong enum value is picocli's exit code 2.
- **Nothing half written**: the document is serialised and schema-validated in memory first; the file is only written if
  that succeeded and the error list has no error. `--strict` with a failed check therefore writes nothing.
- **The source is never overwritten** (e.g. same directory and an empty suffix).
- Applied rewrites are logged as INFO (the audit trail), warnings as WARN, errors as ERROR. The siblings' mapping shows
  infos as WARN because `IError.isFailure()` is true for them; this one uses the error level.
- Optional values are only set when non-empty (the siblings' NPE of 4.4f).

11 CLI tests (all three kinds, wildcard + suffix, directory input with one failing file, source-edition override, skip/copy/fail,
strict, overrides, the credit note option incl. a wrong value, never overwriting the source, non-XML and missing files, help/
version/missing arguments). **A test found a defect on the way:** the overwrite protection kept the source safe but did not count
as a failure, so the exit code was 0 — fixed. 79 tests in total (68 library + 11 CLI).
