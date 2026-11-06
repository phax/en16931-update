# EN 16931 2017 → 2026 update rules

Derived 2026-11-06 (plan item A1) from `en16931-cii2ubl/docs/en16931-2017-syntax.md` and
`…-2026-syntax.md` plus the corpora of `en16931-cii2ubl` (103 CII D16B files) and
`en16931-ubl2cii` (18 UBL 2.1 files, 3 of them Credit Notes). Paths follow the mapping documents:
UBL relative to `/Invoice` resp. `/CreditNote`, CII relative to `/rsm:CrossIndustryInvoice`.

**Method.** All rows with an ID present in both documents were compared column by column; textual
differences were then triaged by hand into *notation only* (e.g. the 2026 table prints `cac:Party/…`
and `[@format='102']` explicitly) and *real*. Rows only in 2026 were triaged into *implicit* (the
element or discriminator already had to exist in a conformant 2017 document) and *new*. Counts below
are from the classification table at the end — 299 distinct IDs, 12 of which are discriminator notes.

## 1. Rewrites

| Rule | Applies to | 2017 (input) | 2026 (output) | Notes |
|------|-----------|--------------|---------------|-------|
| **R1** BT-24 | UBL, CII | `cbc:CustomizationID` / `ram:GuidelineSpecifiedDocumentContextParameter/ram:ID` starting with `urn:cen.eu:en16931:2017` | same element, prefix replaced by `urn:cen.eu:en16931:2026`, **suffix kept** (Q2) | Warn if a suffix (`#compliant#…`) exists: the CIUS is not 2026-aware. Overridable with `--customization-id`. |
| **R2** BT-10 | UBL, CII | `cbc:BuyerReference` / `ram:BuyerReference` | `cac:BuyerAssignedReference/cbc:BuyerReference` / `ram:BuyerReferenceID` | New optional BT-10-1 is **not** produced. UBL: also check that the legacy element is removed, not duplicated. |
| **R3** BG-1 | UBL only | header `cbc:Note`, optionally prefixed `#code#` (BT-21) | `cac:Annotation` with `cbc:SubjectCode` (BT-21) and `cbc:AnnotationContent` (BT-22) | CII paths are unchanged (`ram:IncludedNote/ram:SubjectCode`, `ram:Content`). Only a **leading** `#…#` token is a code. **Not** BT-20 (`cac:PaymentTerms/cbc:Note`) and **not** BT-127 (line `cbc:Note`). |
| **R4** BT-9 | UBL Credit Note | `cac:PaymentMeans/cbc:PaymentDueDate` | `/CreditNote/cbc:DueDate` | Invoice unchanged. |
| **R5** BT-11 | UBL Credit Note | `cac:AdditionalDocumentReference/cbc:ID`, no discriminator | `/CreditNote/cac:ProjectReference/cbc:ID` | **Ambiguous in general** — see Q7. |
| **R6** BT-32-2 | UBL | `cac:Party/cac:PartyTaxScheme/cac:TaxScheme/cbc:ID` ≠ `VAT` | fixed `LOC` | Lossy if the old code carried information (e.g. `GST`); log the old value at info level. CII `@schemeID='FC'` is unchanged in both editions. |
| **R7** BG-24 | UBL only | `cac:AdditionalDocumentReference` without `cbc:DocumentTypeCode` | add `cbc:DocumentTypeCode` = `916` with `@listID='1001'` (BT-122-1, BT-122-1-1) | `listID` is optional in 2026; emitted to match the 2026 converters. References that already have a code (`130`, anything else) are left alone. **CII needs nothing**: `ram:TypeCode` `916` is already required in D16B (corpus: 53 × `916`). Same Credit Note ambiguity as R5 for ID-only references. |
| **R9** UBL version | UBL | `cbc:UBLVersionID` = `2.1` (if present) | `2.5` | Found by reading both converters (they set it); no 2026 table row. None of the 18 corpus files carries it. |
| R8 model only | — | 1..1 → 0..n widenings (`Name`, `StreetName`, `Description`, `SubjectCode`, `InvoiceReferencedDocument`, …) | — | No content change; relevant only to the typed code. Closed list: 4.0.0 plan §4.4b. |

## 2. Checks (2026 made an identifier scheme mandatory)

| Check | BTs | UBL 2.1 input | CII D16B input |
|-------|-----|---------------|----------------|
| **C1** party identifier schemes (0..1 → 1..1) | BT-29-1, 30-1, 46-1, 47-1, 60-1, 61-1, 71-1 | `cbc:ID` / `cbc:CompanyID` without `@schemeID` | `ram:ID` directly under `Seller/Buyer/SellerTaxRepresentative/Payee/ShipTo TradeParty` — **2026 maps these BTs to `ram:GlobalID` only** — and `ram:SpecifiedLegalOrganization/ram:ID` without `@schemeID` |
| **C2** BT-125-2 attachment filename (0..1 → 1..1) | BT-125-2 | `cbc:EmbeddedDocumentBinaryObject` without `@filename` | `ram:AttachmentBinaryObject` without `@filename` |

Measured on the corpora (files with at least one hit):

| | C1 | C2 |
|---|----|----|
| CII D16B, 103 files | **94** of 103 (e.g. `BuyerTradeParty/ram:ID` unscheme'd: 55 occurrences; legal organisation `ID` unscheme'd: 80) | 0 of 45 attachments lack a filename |
| UBL 2.1, 18 files | **18** of 18 | 0 of 1 |

**Consequence:** "error and write nothing" (agreed as the Q3 default) would reject virtually every
real-world 2017 invoice for C1, and — for CII — there is no 2017→2026 *path* for an unscheme'd
`ram:ID`: it stays XSD-valid, but in the 2026 semantic model it is no longer BT-29/46/60/71. The 2026
CII/UBL tables say that the scheme "must be derived from ISO/IEC 6523, complemented with entries for
*Buyer assigned* and *Seller assigned*" (BT-71, BT-186 notes) — the codes of those two entries are not
in `en16931-basics` (`EEN16931ICDCode` has none) and are not in any document I have. **I will not
invent them.** See Q8/Q9.

## 3. Decisions on the questions raised by A1

| # | Question | Decision (2026-11-06) |
|---|----------|-----------------------|
| Q7 | R5/R7 on Credit Notes: ID-only `AdditionalDocumentReference` | **Agreed:** option `--cn-id-only-reference project\|document\|keep`, default **`keep`** (left untouched, warning). References with `Attachment` or `DocumentDescription` are BG-24 (R7 applies); `DocumentType='ATS'` is BT-18 (untouched). |
| Q8 | C1 default | **Agreed:** warning, element left as it is, summary count; `--strict` turns C1 into an error. Inserting a scheme (below) is the 1.0.0 goal, switchable. |
| Q9 | Scheme codes for "Buyer assigned" / "Seller assigned" | **Working assumption, NOT verified:** the values are meant **literally**, i.e. the scheme identifier is the text `Buyer assigned` resp. `Seller assigned`. They live in one place (a constant pair in `en16931-basics` once confirmed; a private constant here until then) and are **off by default** until confirmed. |

Still open under Q9, because the tables do not say: (1) exact spelling and casing of the two literals;
(2) for CII, whether the target of an unscheme'd `ram:ID` really is `ram:GlobalID` carrying the literal as
`@schemeID` (the 2026 table maps BT-29/46/60/71 to `GlobalID` only); (3) **which of the two applies to
which BT**. Only BT-29 (seller) and BT-46 (buyer) are self-evident; BT-60 (payee), BT-71 (deliver-to
location) and the legal-registration IDs BT-30/47/61 have no obvious "assigner" and must not be guessed.
Until (1)–(3) are answered, insertion is implemented for BT-29/BT-46 only, behind an option, and everything
else follows the Q8 default (warn, leave).

## 4. Not rewritten, by design

- **Everything new in 2026** (71 IDs: BG-33…BG-39, BT-166…BT-220 and BT-10-1) — no source data in a 2017 document.
- **Relaxations** BT-34/BT-49 endpoint (1..1 → 0..1), BT-117, BT-160; **alternatives** BT-41/BT-56 `DepartmentName`, BT-84/BT-91 `ProprietaryID`, BT-161 `ValueMeasure`.
- **Implicit rows** (30): sub-identifiers that a conformant 2017 document already carried — `@format='102'` (BT-2-1, 7-1, 9-1, 26-1, 72-1, 73-1, 74-1, 134-1, 135-1), `TypeCode` 50/130/VAT (BT-17-1, 18-2, 95-1, 102-1, 118-1, 128-2, 151-1), `TaxScheme/ID='VAT'` and `@schemeID='VA'` (BT-31-1/-2, 48-1/-2, 63-1/-2), `@currencyID` (BT-110-1, 111-1, 116-1), `ChargeIndicator=false` (BT-147-1, 148-1), BT-11-1 project name, BT-32-1, BT-90-1 `SEPA`. If one is missing in an input, the input was not a conformant 2017 invoice; A5–A7 validate with the 2017 Schematron (A9) rather than repair.
- **Unknown or extension content** — preserved (Q1).
- **`xsi:schemaLocation`** on the root — dropped by JAXB (A0).

## 5. Classification of every ID of the 2026 table

Classes: CARRIED (same in both editions, notation differences ignored) · REWRITE Rn · CHECK Cn ·
IMPLICIT · NEW (not emitted) · NOTE (a discriminator note row, not an ID). `A5`–`A7` must have a test for
every REWRITE and CHECK row; A10 turns this table into a coverage guard.

| ID | Name | Card. 2026 | Class |
|----|------|-----------|-------|
| BT-1 | Invoice number | 1..1 | CARRIED |
| BT-2 | Invoice issue date | 1..1 | CARRIED |
| BT-2-1 | Invoice issue date code | 1..1 | IMPLICIT |
| BT-166 | Invoice issue time | 0..1 | NEW |
| BT-166-1 | Invoice issue time code | 1..1 | NEW |
| BT-3 | Invoice type code | 1..1 | CARRIED |
| BT-5 | Invoice currency code | 1..1 | CARRIED |
| BT-6 | VAT accounting currency code | 0..1 | CARRIED |
| BT-167 | VAT accounting currency exchange rate | 0..1 | NEW |
| BT-167-1 | Target currency code | 1..1 | NEW |
| BT-167-2 | Source currency code | 1..1 | NEW |
| BT-7 | Value added tax point date | 0..1 | CARRIED |
| BT-7-1 | Value added tax point date code | 1..1 | IMPLICIT |
| BT-8 | Value added tax point date code | 0..1 | CARRIED |
| BT-9 | Credit note requires UBL 2.2 — /CreditNote/cbc:DueDate did not exist in UBL 2.1 (2017: cac:PaymentMeans/cbc:PaymentDueDate) |  | REWRITE R4 |
| BT-9-1 | Payment due date code | 1..1 | IMPLICIT |
| BT-10 | Buyer reference | 0..n | REWRITE R2 |
| BT-10-1 | Buyer reference Scheme identifier | 0..1 | NEW |
| BT-11 | Credit note requires UBL 2.2 — /CreditNote/cac:ProjectReference did not exist in UBL 2.1 (2017: cac:AdditionalDocumentReference/cbc:ID) |  | REWRITE R5 |
| BT-11-1 | Project name | 1..1 | IMPLICIT |
| BT-12 | Contract reference | 0..1 | CARRIED |
| BT-13 | Purchase order reference | 0..1 | CARRIED |
| BT-14 | Sales order reference | 0..1 | CARRIED |
| BT-15 | Receiving advice reference | 0..1 | CARRIED |
| BT-16 | Despatch advice reference | 0..1 | CARRIED |
| BT-197 | Delivery note reference | 0..1 | NEW |
| BT-17 | Tender or lot reference | 0..1 | CARRIED |
| BT-17-1 | Tender or lot reference type code | 1..1 | IMPLICIT |
| BT-18 | Invoiced object identifier | 0..1 | CARRIED |
| BT-18-1 | Invoiced object Scheme identifier | 0..1 | CARRIED |
| BT-18-2 | Invoiced object identifier type code | 1..1 | IMPLICIT |
| BT-19 | Buyer accounting reference | 0..1 | CARRIED |
| BT-20 | Payment term text | 0..1 | CARRIED |
| BT-170 | Discount end date | 1..1 | NEW |
| BT-170-1 | Format identifier | 1..1 | NEW |
| BT-171 | Discount percentage | 0..1 | NEW |
| BT-172 | Discount amount | 0..1 | NEW |
| BT-181 | Penalty start date | 1..1 | NEW |
| BT-181-1 | *(unnamed in source)* | 1..1 | NEW |
| BT-182 | Penalty yearly interest percentage | 0..1 | NEW |
| BT-183 | Penalty amount | 0..1 | NEW |
| BT-21 | Invoice note subject code | 0..1 | REWRITE R3 |
| BT-22 | Invoice note | 1..1 | REWRITE R3 |
| BT-23 | Business process type | 0..1 | CARRIED |
| BT-24 | Specification identifier | 1..1 | REWRITE R1 |
| BT-25 | Preceding invoice reference | 1..1 | CARRIED |
| BT-26 | Preceding invoice issue date | 0..1 | CARRIED |
| BT-26-1 | Preceding invoice issue date code | 1..1 | IMPLICIT |
| BT-202 | Preceding invoice type code | 0..1 | NEW |
| BT-27 | Seller name | 1..1 | CARRIED |
| BT-28 | Seller trading name | 0..1 | CARRIED |
| BT-29 | Seller identifier | 0..n | CARRIED + CHECK C1 |
| BT-29-1 | Seller Scheme identifier | 1..1 | CHECK C1 |
| BT-30 | Seller legal registration identifier | 0..1 | CARRIED + CHECK C1 |
| BT-30-1 | Seller legal registration Scheme identifier | 1..1 | CHECK C1 |
| BT-31 | Seller VAT identifier | 0..1 | CARRIED |
| BT-31-1 | Seller VAT registration Scheme identifier | 0..1 | IMPLICIT |
| BT-31-2 | Seller VAT tax code | 1..1 | IMPLICIT |
| BT-32 | Seller tax registration identifier | 0..1 | CARRIED |
| BT-32-1 | National tax registration Scheme identifier | 0..1 | IMPLICIT |
| BT-32-2 | National tax code | 1..1 | REWRITE R6 |
| BT-33 | Seller additional legal information | 0..1 | CARRIED |
| BT-34 | Seller electronic address | 0..1 | CARRIED |
| BT-34-1 | Seller electronic address Scheme identifier | 1..1 | CARRIED |
| BT-35 | Seller address line 1 | 0..1 | CARRIED |
| BT-36 | Seller address line 2 | 0..1 | CARRIED |
| BT-162 | Seller address line 3 | 0..1 | CARRIED |
| BT-37 | Seller city | 0..1 | CARRIED |
| BT-38 | Seller post code | 0..1 | CARRIED |
| BT-39 | Seller country subdivision | 0..1 | CARRIED |
| BT-40 | Seller country code | 1..1 | CARRIED |
| BT-41 | Seller contact point | 0..1 | CARRIED |
| BT-42 | Seller contact telephone number | 0..1 | CARRIED |
| BT-43 | Seller contact email address | 0..1 | CARRIED |
| BT-44 | Buyer name | 1..1 | CARRIED |
| BT-45 | Buyer trading name | 0..1 | CARRIED |
| BT-46 | Buyer identifier | 0..n | CARRIED + CHECK C1 |
| BT-46-1 | Buyer Scheme identifier | 1..1 | CHECK C1 |
| BT-47 | Buyer legal registration identifier | 0..1 | CARRIED + CHECK C1 |
| BT-47-1 | Buyer legal registration Scheme identifier | 1..1 | CHECK C1 |
| BT-48 | Buyer VAT identifier | 0..1 | CARRIED |
| BT-48-1 | Buyer VAT registration Scheme identifier | 0..1 | IMPLICIT |
| BT-48-2 | Buyer VAT tax code | 1..1 | IMPLICIT |
| BT-49 | Buyer electronic address | 0..1 | CARRIED |
| BT-49-1 | Buyer electronic address Scheme identifier | 1..1 | CARRIED |
| BT-50 | Buyer address line 1 | 0..1 | CARRIED |
| BT-51 | Buyer address line 2 | 0..1 | CARRIED |
| BT-163 | Buyer address line 3 | 0..1 | CARRIED |
| BT-52 | Buyer city | 0..1 | CARRIED |
| BT-53 | Buyer post code | 0..1 | CARRIED |
| BT-54 | Buyer country subdivision | 0..1 | CARRIED |
| BT-55 | Buyer country code | 1..1 | CARRIED |
| BT-56 | Buyer contact point | 0..1 | CARRIED |
| BT-57 | Buyer contact telephone number | 0..1 | CARRIED |
| BT-58 | Buyer contact email address | 0..1 | CARRIED |
| BT-59 | Payee name | 1..1 | CARRIED |
| BT-60 | Payee identifier | 0..1 | CARRIED + CHECK C1 |
| BT-60-1 | Payee Scheme identifier | 1..1 | CHECK C1 |
| BT-61 | Payee legal registration identifier | 0..1 | CARRIED + CHECK C1 |
| BT-61-1 | Payee legal registration Scheme identifier | 1..1 | CHECK C1 |
| BT-62 | Seller tax representative name | 1..1 | CARRIED |
| BT-63 | Seller tax representative VAT identifier | 1..1 | CARRIED |
| BT-63-1 | Seller tax representative VAT registration Scheme identifier | 0..1 | IMPLICIT |
| BT-63-2 | Seller tax representative VAT tax code | 1..1 | IMPLICIT |
| BT-64 | Tax representative address line 1 | 0..1 | CARRIED |
| BT-65 | Tax representative address line 2 | 0..1 | CARRIED |
| BT-164 | Tax representative address line 3 | 0..1 | CARRIED |
| BT-66 | Tax representative city | 0..1 | CARRIED |
| BT-67 | Tax representative post code | 0..1 | CARRIED |
| BT-68 | Tax representative country subdivision | 0..1 | CARRIED |
| BT-69 | Tax representative country code | 1..1 | CARRIED |
| BT-70 | Deliver to party name | 0..1 | CARRIED |
| BT-71 | Deliver to location identifier | 0..1 | CARRIED + CHECK C1 |
| BT-71-1 | Deliver to location Scheme identifier | 1..1 | CHECK C1 |
| BT-72 | Actual delivery date | 0..1 | CARRIED |
| BT-72-1 | Actual delivery date code | 1..1 | IMPLICIT |
| BT-73 | Invoicing period start date | 0..1 | CARRIED |
| BT-73-1 | Invoicing period start date code | 1..1 | IMPLICIT |
| BT-74 | Invoicing period end date | 0..1 | CARRIED |
| BT-74-1 | Invoicing period end date code | 1..1 | IMPLICIT |
| BT-75 | Deliver to address line 1 | 0..1 | CARRIED |
| BT-76 | Deliver to address line 2 | 0..1 | CARRIED |
| BT-165 | Deliver to address line 3 | 0..1 | CARRIED |
| BT-77 | Deliver to city | 0..1 | CARRIED |
| BT-78 | Deliver to post code | 0..1 | CARRIED |
| BT-79 | Deliver to country subdivision | 0..1 | CARRIED |
| BT-80 | Deliver to country code | 1..1 | CARRIED |
| BT-81 | Payment means type code | 1..1 | CARRIED |
| BT-82 | Payment means text | 0..1 | CARRIED |
| BT-83 | Remittance information | 0..1 | CARRIED |
| BT-84 | Payment account identifier | 1..1 | CARRIED |
| BT-85 | Payment account name | 0..1 | CARRIED |
| BT-86 | Payment service provider identifier | 0..1 | CARRIED |
| BT-87 | Payment card primary account number | 1..1 | CARRIED |
| BT-88 | Payment card holder name | 0..1 | CARRIED |
| BT-89 | Mandate reference identifier | 0..1 | CARRIED |
| BT-90 | Bank assigned creditor identifier | 0..1 | CARRIED |
| BT-90-1 | Bank assigned creditor identifier Scheme Identifier | 1..1 | IMPLICIT |
| BT-91 | Debited account identifier | 0..1 | CARRIED |
| BT-216 | Debited account name | 0..1 | NEW |
| BT-215 | Debited account payment service provider identifier | 0..1 | NEW |
| BT-92 | Document level allowance amount | 1..1 | CARRIED |
| BT-93 | Document level allowance base amount | 0..1 | CARRIED |
| BT-94 | Document level allowance percentage | 0..1 | CARRIED |
| BT-95 | Document level allowance VAT category code | 1..1 | CARRIED |
| BT-95-1 | VAT tax code | 1..1 | IMPLICIT |
| BT-96 | Document level allowance VAT rate | 0..1 | CARRIED |
| BT-173 | Document level allowance exemption reason text | 0..1 | NEW |
| BT-174 | Document level allowance VAT exemption reason and specification code | 0..1 | NEW |
| BT-97 | Document level allowance reason | 0..1 | CARRIED |
| BT-98 | Document level allowance reason code | 0..1 | CARRIED |
| BT-213 | Document level allowance goods/services code | 0..1 | NEW |
| BT-99 | Document level charge or tax amount | 1..1 | CARRIED |
| BT-100 | Document level charge or tax base amount | 0..1 | CARRIED |
| BT-101 | Document level charge or tax percentage | 0..1 | CARRIED |
| BT-102 | Document level charge or tax VAT category code | 1..1 | CARRIED |
| BT-102-1 | VAT tax code | 1..1 | IMPLICIT |
| BT-103 | Document level charge or tax VAT rate | 0..1 | CARRIED |
| BT-175 | Document level charge or tax exemption reason text | 0..1 | NEW |
| BT-176 | VAT exemption reason and specification code of the document level charge or tax | 0..1 | NEW |
| BT-104 | Document level charge or tax reason | 0..1 | CARRIED |
| BT-105 | Document level charge reason code | 0..1 | CARRIED |
| BT-177 | Document level non-VAT tax code | 0..1 | NEW |
| BT-177-1 | Document level non-VAT tax code list identifier | 1..1 | NEW |
| BT-214 | Document level charge goods/services code | 0..1 | NEW |
| BT-106 | Sum of invoice line net amount | 1..1 | CARRIED |
| BT-107 | Sum of allowances on document level | 0..1 | CARRIED |
| BT-108 | Sum of charges and taxes on document level | 0..1 | CARRIED |
| BT-109 | Invoice total amount without VAT | 1..1 | CARRIED |
| BT-110 | Invoice total VAT amount | 0..1 | CARRIED |
| BT-110-1 | Invoice total VAT amount currency code | 1..1 | IMPLICIT |
| BT-111 | Invoice total VAT amount in VAT accounting currency | 0..1 | CARRIED |
| BT-111-1 | Invoice total VAT amount currency code | 1..1 | IMPLICIT |
| BT-112 | Invoice total amount with VAT | 1..1 | CARRIED |
| BT-113 | Paid amount | 0..1 | CARRIED |
| BT-114 | Rounding amount | 0..1 | CARRIED |
| BT-115 | Amount due for payment | 1..1 | CARRIED |
| BT-179 | Charge amount collected on behalf of a third party | 1..1 | NEW |
| BT-179-1 | Line identifier | 1..1 | NEW |
| BT-180 | Charges specification | 1..1 | NEW |
| BT-184 | VAT breakdown currency | 0..1 | NEW |
| BT-116 | VAT category taxable amount | 1..1 | CARRIED |
| BT-116-1 | VAT category taxable amount currency | 1..1 | IMPLICIT |
| BT-117 | VAT category tax amount | 0..1 | CARRIED |
| BT-118 | VAT category code | 1..1 | CARRIED |
| BT-118-1 | VAT tax type code | 1..1 | IMPLICIT |
| BT-119 | VAT category rate | 0..1 | CARRIED |
| BT-120 | VAT exemption reason text | 0..1 | CARRIED |
| BT-121 | VAT exemption reason and specification code | 0..1 | CARRIED |
| BT-210 | VAT breakdown goods/services code | 0..1 | NEW |
| BT-122 | Supporting document reference | 1..1 | CARRIED |
| BT-122-1 | Supporting document reference code | 1..1 | REWRITE R7 |
| BT-122-1-1 | Supporting document reference document type list | 0..1 | REWRITE R7 |
| BT-123 | Supporting document description | 0..1 | CARRIED |
| BT-124 | External document location | 0..1 | CARRIED |
| BT-125 | Attached document | 0..1 | CARRIED |
| BT-125-1 | Attached document MIME code | 1..1 | CARRIED |
| BT-125-2 | Attached document filename | 1..1 | CHECK C2 |
| BT-126 | Invoice line identifier | 1..1 | CARRIED |
| BT-127 | Invoice line note | 0..1 | CARRIED |
| BT-128 | Invoice line object identifier | 0..1 | CARRIED |
| BT-128-1 | Invoice line object Scheme identifier | 0..1 | CARRIED |
| BT-128-2 | Invoice line object identifier type code | 1..1 | IMPLICIT |
| BT-129 | Invoiced quantity | 1..1 | CARRIED |
| BT-130 | Invoiced quantity unit of measure code | 1..1 | CARRIED |
| BT-131 | Invoice line net amount | 1..1 | CARRIED |
| BT-188 | Invoice line purchase order reference | 0..1 | NEW |
| BT-132 | Invoice line purchase order line reference | 0..1 | CARRIED |
| BT-200 | Invoice line sales order reference | 0..1 | NEW |
| BT-201 | Invoice line sales order line reference | 0..1 | NEW |
| BT-189 | Invoice line despatch advice reference | 0..1 | NEW |
| BT-190 | Invoice line despatch advice line reference | 0..1 | NEW |
| BT-191 | Invoice line receiving advice reference | 0..1 | NEW |
| BT-192 | Invoice line receiving advice line reference | 0..1 | NEW |
| BT-198 | Invoice line delivery note reference | 0..1 | NEW |
| BT-199 | Invoice line delivery note line reference | 0..1 | NEW |
| BT-133 | Invoice line Buyer accounting reference | 0..1 | CARRIED |
| BT-217 | Line-level preceding invoice reference | 1..1 | NEW |
| BT-218 | Line-level preceding invoice issue date | 0..1 | NEW |
| BT-218-1 | Line-level preceding invoice issue date code | 1..1 | NEW |
| BT-219 | Line-level preceding invoice type code | 0..1 | NEW |
| BT-220 | Line-level preceding invoice line reference | 0..1 | NEW |
| BT-185 | Invoice line deliver to party name | 0..1 | NEW |
| BT-186 | Invoice line deliver to location identifier | 0..1 | NEW |
| BT-186-1 | Invoice line deliver to location Scheme identifier | 1..1 | NEW |
| BT-187 | Invoice line actual delivery date | 0..1 | NEW |
| BT-187-1 | Invoice line actual delivery date code | 1..1 | NEW |
| BT-203 | Invoice line deliver to address line 1 | 0..1 | NEW |
| BT-204 | Invoice line deliver to address line 2 | 0..1 | NEW |
| BT-205 | Invoice line deliver to address line 3 | 0..1 | NEW |
| BT-206 | Invoice line deliver to city | 0..1 | NEW |
| BT-207 | Invoice line deliver to post code | 0..1 | NEW |
| BT-208 | Invoice line deliver to country subdivision | 0..1 | NEW |
| BT-209 | Invoice line deliver to country code | 1..1 | NEW |
| BT-134 | Invoice line period start date | 0..1 | CARRIED |
| BT-134-1 | Invoice line period start date code | 1..1 | IMPLICIT |
| BT-135 | Invoice line period end date | 0..1 | CARRIED |
| BT-135-1 | Invoice line period end date code | 1..1 | IMPLICIT |
| BT-136 | Invoice line allowance amount | 1..1 | CARRIED |
| BT-137 | Invoice line allowance base amount | 0..1 | CARRIED |
| BT-138 | Invoice line allowance percentage | 0..1 | CARRIED |
| BT-139 | Invoice line allowance reason | 0..1 | CARRIED |
| BT-140 | Invoice line allowance reason code | 0..1 | CARRIED |
| BT-141 | Invoice line charge or tax amount | 1..1 | CARRIED |
| BT-142 | Invoice line charge or tax base amount | 0..1 | CARRIED |
| BT-143 | Invoice line charge or tax percentage | 0..1 | CARRIED |
| BT-144 | Invoice line charge or tax reason | 0..1 | CARRIED |
| BT-145 | Invoice line charge reason code | 0..1 | CARRIED |
| BT-193 | Invoice line-level non-VAT tax type code | 0..1 | NEW |
| BT-193-1 | Invoice line tax type code list identifier | 1..1 | NEW |
| BT-146 | Item net price | 1..1 | CARRIED |
| BT-147 | Item price discount | 0..1 | CARRIED |
| BT-147-1 | Item price discount indicator | 1..1 | IMPLICIT |
| BT-148 | Item gross price | 0..1 | CARRIED |
| BT-148-1 | Item gross price discount indicator | 1..1 | IMPLICIT |
| BT-149 | Item price base quantity | 0..1 | CARRIED |
| BT-150 | Item price base quantity unit of measure code | 0..1 | CARRIED |
| BT-151 | Invoiced item VAT category code | 1..1 | CARRIED |
| BT-151-1 | VAT tax code | 1..1 | IMPLICIT |
| BT-152 | Invoiced item VAT rate | 0..1 | CARRIED |
| BT-194 | Invoiced item exemption reason text | 0..1 | NEW |
| BT-195 | Invoiced item VAT exemption reason and specification code | 0..1 | NEW |
| BT-153 | Item name | 1..1 | CARRIED |
| BT-154 | Item description | 0..1 | CARRIED |
| BT-155 | Item Seller's identifier | 0..1 | CARRIED |
| BT-156 | Item Buyer's identifier | 0..1 | CARRIED |
| BT-157 | Item standard identifier | 0..1 | CARRIED |
| BT-157-1 | Item standard Scheme identifier | 1..1 | CARRIED |
| BT-196 | Goods/services code | 0..1 | NEW |
| BT-158 | Item classification identifier | 0..n | CARRIED |
| BT-158-1 | Item classification Scheme identifier | 1..1 | CARRIED |
| BT-158-2 | Scheme version identifier | 0..1 | CARRIED |
| BT-159 | Item country of origin | 0..1 | CARRIED |
| BT-211 | Item attribute code | 0..1 | NEW |
| BT-160 | Item attribute name | 0..1 | CARRIED |
| BT-161 | Item attribute value | 1..1 | CARRIED |
| BT-212 | Item attribute value unit of measure code | 0..1 | NEW |
| BG-31 | Effective cardinality 1..1 (invoice) vs 0..1 (credit note) |  | CARRIED |
| BG-20 allowance vs BG-21 charge (document level) | cac:AllowanceCharge/cbc:ChargeIndicator: false (BG-20-1) vs true (BG-21-1) | ram:SpecifiedTradeAllowanceCharge/ram:ChargeIndicator/udt:Indicator: false vs true | NOTE |
| BG-27 allowance vs BG-28 charge (line level) | same indicator on cac:InvoiceLine/cac:AllowanceCharge (BG-27-1 / BG-28-1) | same indicator on ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeAllowanceCharge | NOTE |
| BT-147 price discount vs BT-148 gross price | cac:Price/cac:AllowanceCharge with cbc:ChargeIndicator = false (BT-147-1, BT-148-1); BT-147 = cbc:Amount, BT-148 = cbc:BaseAmount | separate elements: BT-147 = ram:GrossPriceProductTradePrice/ram:AppliedTradeAllowanceCharge/ram:ActualAmount (indicator false, BT-147-1), BT-148 = ram:GrossPriceProductTradePrice/ram:ChargeAmount | NOTE |
| BT-17 vs BT-18 vs BG-24 | three separate containers: cac:OriginatorDocumentReference (BT-17); cac:AdditionalDocumentReference with cbc:DocumentTypeCode = 130 (BT-18, via BT-18-2); cac:AdditionalDocumentReference with cbc:DocumentTypeCode = 916 (BG-24, via BT-122-1) | one container ram:AdditionalReferencedDocument, ram:TypeCode: 50 (BT-17, via BT-17-1), 130 (BT-18), 916 (BG-24) | CARRIED |
| BT-31 Seller VAT ID vs BT-32 Seller tax registration ID | cac:PartyTaxScheme/cac:TaxScheme/cbc:ID: VAT (BT-31-2) vs LOC (BT-32-2) — **changed in 2026**, 2017 used != 'VAT' for BT-32 | ram:SpecifiedTaxRegistration/ram:ID/@schemeID: VA (BT-31-1) vs FC (BT-32-1) | NOTE |
| BT-48 / BT-63 VAT identifiers | cac:PartyTaxScheme/cac:TaxScheme/cbc:ID = VAT (BT-48-2 / BT-63-2) | @schemeID = VA (BT-48-1 / BT-63-1) | NOTE |
| BT-110 vs BT-111 (VAT amounts) | two cac:TaxTotal/cbc:TaxAmount elements, distinguished by @currencyID: BT-5 (BT-110-1) vs BT-6 (BT-111-1) | two ram:TaxTotalAmount elements, same @currencyID distinction | CARRIED |
| BT-105 charge reason code vs BT-177 non-VAT tax code | both cac:AllowanceCharge/cbc:AllowanceChargeReasonCode; BT-177 carries @listID = 5153 (BT-177-1, UNTDID 5153), BT-105 uses UNTDID 7161 | both ram:ReasonCode; BT-177 written as ram:ReasonCode[.!='VAT']; rules CII-DT-100/101 require @listID and @listAgencyID on BT-177 | NOTE |
| BT-145 line charge reason code vs BT-193 line non-VAT tax code | both cac:InvoiceLine/cac:AllowanceCharge/cbc:AllowanceChargeReasonCode; BT-193 via @listID (BT-193-1) | both ram:ReasonCode; rules CII-DT-102/103 require @listID and @listAgencyID on BT-193 | NOTE |
| BT-2 issue date vs BT-166 issue time | separate cbc:IssueDate and cbc:IssueTime | **one** ram:IssueDateTime/udt:DateTimeString; @format = 102 (date only, BT-2-1) or 208 (date and time, BT-166-1) | NOTE |
| BT-161 item attribute value | text in cbc:Value (BT-161a) or numeric in cbc:ValueQuantity with @unitCode = BT-212 (BT-161b) | text in ram:Value (BT-161a) or numeric in ram:ValueMeasure (BT-161b); rule CII-SR-504: exactly one of the two | NOTE |
| BT-84 / BT-91 account identifier | single cbc:ID | ram:IBANID if applicable, otherwise ram:ProprietaryID | NOTE |
| BT-90 bank assigned creditor identifier | cac:AccountingSupplierParty/…/cac:PartyIdentification/cbc:ID (BT-90a, Seller) or cac:PayeeParty/cac:PartyIdentification/cbc:ID (BT-90b, Payee), @schemeID = SEPA (BT-90-1) | one dedicated element ram:ApplicableHeaderTradeSettlement/ram:CreditorReferenceID, no alternative and no scheme identifier | NOTE |
| BG-33 / BG-35 / BG-36 payment terms | all three share /Invoice/cac:PaymentTerms; the source gives no explicit discriminator — in practice BG-35 is identified by cac:SettlementPeriod / cbc:SettlementDiscount*, BG-36 by cac:PenaltyPeriod / cbc:PenaltyAmount / cac:PenaltyInterestRate | unambiguous: BG-35 has its own container ram:ApplicableTradePaymentDiscountTerms, BG-36 uses ram:ApplicableTradePaymentPenaltyTerms | NOTE |
| BG-33 | PAYMENT TERMS | 0..n | NEW |
| BG-35 | EARLY PAYMENT DISCOUNT | 0..n | NEW |
| BG-36 | LATE PAYMENT PENALTY | 0..n | NEW |
| BG-34 | CHARGES ON BEHALF OF A THIRD PARTY | 0..n | NEW |
| BG-39 | LINE-LEVEL PRECEDING INVOICE REFERENCE | 0..n | NEW |
| BG-37 | INVOICE LINE DELIVERY INFORMATION | 0..1 | NEW |
| BG-38 | INVOICE LINE DELIVER TO ADDRESS | 0..1 | NEW |
