# What is really left

State as of 2026-11-09: plan items A0–A11 are done (see `plan-1.0.0.md` §10). All three updaters, the dispatcher and the CLI exist; only the README (A12) and the open questions below are left.

## 1. Work items (in order)

| Item | What | Depends on |
|------|------|------------|
| **A12** | README (usage, rules table, limits), News and noteworthy, CLAUDE.md status, `@since 1.0.0` | all |

## 2. Prerequisites not done yet

- ~~Test resources are not copied.~~ Done in A4 (121 files).
- **Hand-written inputs are missing** for: UBL header `cbc:Note` with `#code#` prefix (R3), non-`VAT`
  `TaxScheme/ID` (R6), `cbc:UBLVersionID` (R9), Credit Note with ID-only `AdditionalDocumentReference` (R5/Q7),
  `ext:UBLExtensions`, embedded attachments. The corpus has none of these.
- `phive-rules-en16931` 4.6.4 is a test dependency now. `ph-cii-d16b` / `ph-ubl21` were **not** needed: the
  preservation test compares XML, not models.

## 3. Open questions

| # | Question | Effect while open |
|---|----------|-------------------|
| Q9a | Exact spelling/casing of the literal schemes `Buyer assigned` / `Seller assigned` | Insertion stays off by default |
| Q9b | CII: is the target of an unscheme'd `ram:ID` really `ram:GlobalID` + `@schemeID`? | Same |
| Q9c | Which scheme for BT-30/47/60/61/71 (no obvious assigner)? | Those only warn (Q8 default) |
| — | R6 (BT-32-2 → `LOC`) is lossy when the old code carried information. Rewrite and log the old value (current plan), or leave non-`VAT` codes alone? | Current plan: rewrite + info log |
| — | R7 `@listID='1001'`: optional in 2026; emitted to match the 2026 converters. Confirm. | Current plan: emit |

## 4. Known verification limits (must go into the README)

- No 2026 Schematron or other 2026 validation artefact exists; conformance of the output is checked only by
  XSD (on write), per-rule tests, preservation, idempotence and the coverage guard.
- Reading is strict: an input invalid against the 2026 XSD is rejected, not repaired.
- The UBL corpus is small (18 files, 3 Credit Notes); the CII corpus is large but mostly XRechnung.
- The mapping tables had a source defect before (BT-218); they are an input to check, not an oracle.

## 5. Housekeeping / decisions for you

- Keep or delete `.github/workflows/release-to-slack.yml` (copied from the siblings).
- `en16931-basics`: once Q9 is answered, the two scheme literals belong there, not here.
- GitHub repo `phax/en16931-update` and Maven Central coordinates are assumed in the poms; nothing was created.
- Commit when you want it; nothing from this session is committed.
