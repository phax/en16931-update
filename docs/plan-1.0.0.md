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
entry (see Q3), never a silent guess.

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
| Q3 | Missing mandatory scheme IDs (8 checks) and ambiguous R5 | **Error + no output** by default; `--lenient` downgrades to a warning and leaves the element untouched. R5: move only when unambiguous, otherwise warn. |
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

- [ ] **A3 — Project scaffolding**: parent pom, two modules, license header plugin, `.gitignore`,
  CI workflow copied from siblings, `UpdateVersion`, CLAUDE.md. Compiles, empty tests green.
- [ ] **A4 — Core API**: `AbstractEN16931Updater`, rule-report type, `EN16931UpdateDispatcher`
  detection + routing (edition, syntax kind, already-2026 handling Q4), error cases (BT-24 missing,
  unknown edition, not-well-formed, unsupported root) — unit tested without any rewrite logic.

### Phase 2 — Updaters (one syntax each, smallest first)

- [ ] **A5 — CII**: R1, R2, R3-check, R6, R7 + the scheme-id checks; read D16B via D2, write D25A.
  Run over the whole CII corpus; XSD valid output; per-rule XPath assertions.
- [ ] **A6 — UBL Invoice**: R1, R2, R3 (the `#code#` prefix parser — **careful**: only a leading
  `#…#` token; BT-127 line notes must not move), R6, R7 + checks.
- [ ] **A7 — UBL Credit Note**: everything from A6 plus R4, R5.
- [ ] **A8 — Hardening**: XXE/DTD protection test (ubl2cii has `XXEProtectionTest` to copy),
  idempotence (D7), `@listID` default trap (4.4c) on every `qdt:` code copied, `@format`
  preservation, extension content (Q1), huge-file smoke.

### Phase 3 — Verification

- [ ] **A9 — Preservation test (D6c)** for CII and both UBL kinds: diff input vs output over the
  whole corpus; the only differences allowed are those named by a rule R1–R8. Unexpected loss or
  change fails the test. Also run `phive-rules-en16931` on the 2017 inputs to confirm the corpus is
  itself conformant.
- [ ] **A10 — Coverage guard**: every row of `en16931-2026-syntax.md` is classified in
  `update-rules.md`; test fails if a row is unclassified. Prevents the BT-122-1 miss.

### Phase 4 — CLI and docs

- [ ] **A11 — CLI**: `-t/--target`, `--output-suffix` (default `-2026`), `--en-version`-style
  override, `--on-2026`, `--lenient`, `--customization-id`, `--profile-id`, `--verbose`,
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
