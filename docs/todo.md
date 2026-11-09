# What is really left

State as of 2026-11-09: **all planned items A0–A12 are done** (see `plan-1.0.0.md` §10 for what was built and found).
The updaters for CII, UBL Invoice and UBL Credit Note, the dispatcher, the CLI, the tests (79) and the README exist.
What remains is not coding work but decisions, confirmations and release housekeeping.

## 1. Open questions (they change behaviour, not structure)

| # | Question | Effect while open |
|---|----------|-------------------|
| Q9a | Exact spelling/casing of the 2026 party identifier schemes `Buyer assigned` / `Seller assigned` (taken as literal text so far, **unverified**) | Insertion of a scheme is **not implemented**; C1 only warns |
| Q9b | CII: is the target of an unscheme'd `ram:ID` really `ram:GlobalID` + `@schemeID`? | Same |
| Q9c | Which scheme for BT-30/47/60/61/71, which have no obvious "assigner"? | Same |
| R6 | BT-32-2 → `LOC` is lossy when the old tax scheme code carried information (e.g. `GST`). Rewrite and log the old value (current), or leave non-`VAT` codes alone? | Current: rewrite + info log |
| R7 | `@listID='1001'` on the new `916` document type code is optional in 2026; it is emitted to match the 2026 converters. Confirm. | Current: emit |

Q9 is the one that matters: 94 of 103 CII and 18 of 18 UBL test files have identifiers without a scheme, so users will see
many C1 warnings until the schemes can be inserted. If you give me the exact values and the CII target element, I would add them to
`en16931-basics` (where facts about the standard belong) and implement the insertion for BT-29 and BT-46 first.

## 2. Known verification limits (all stated in the README)

- No 2026 Schematron or other 2026 validation artefact exists. The output is checked by the 2026 XSD (on write), the per-rule
  tests, the preservation test, idempotence and the coverage guard — not by a conformance rule set.
- Reading is strict: an input that is invalid against the 2026 XSD is rejected, not repaired.
- The UBL corpus is small (18 files, 3 Credit Notes); the rules R3, R4, R5, R6, R7 and R9 are mostly exercised by hand-built inputs.
- The mapping tables had a source defect before (BT-218 in the cii2ubl work); they are inputs to check, not an oracle.
- The preservation test does not compare the content of *added* subtrees (moved elements); the per-rule XPath tests do.
- `XMLDiffer` pairs same-named siblings by position; exact for all current rules, not for an arbitrary removal in the middle.

## 3. Housekeeping / decisions for you

- Keep or delete `.github/workflows/release-to-slack.yml` (copied from the siblings, needs the `SLACK_WEBHOOK_URL` secret).
- `README.md` has no badge block yet (the sibling projects have the Sonatype/javadoc badges) — add it when the first release exists.
- GitHub repo `phax/en16931-update` and the Maven Central coordinates `com.helger:en16931-update` are assumed in the poms;
  nothing was created or released.
- `en16931-basics`: once Q9 is answered, the two scheme literals belong there, not here.
- `docs/en16931-2017-syntax.md` and `docs/en16931-2026-syntax.md` are copies from `en16931-cii2ubl` (commit 9960b78); the
  `MappingCoverageTest` depends on them. The `docs/00readme.txt` of cii2ubl still has the wrong "Draft versions" citation of the
  2026 sources (see its plan A17) — it was not copied here.
- That `cbc:DueDate` of a UBL 2.5 Credit Note sits between `IssueTime` and `TaxPointDate` was read from the UBL 2.5 XSD,
  not from the standard.
