# en16931-update

Library and command line tool that updates EN 16931:2017 invoices and credit notes to EN 16931:2026 **within the same syntax**:

| Syntax | Input (EN 16931:2017) | Output (EN 16931:2026) |
|--------|-----------------------|------------------------|
| CII | D16B | D25A |
| UBL Invoice | 2.1 | 2.5 |
| UBL Credit Note | 2.1 | 2.5 |

It is neither a syntax converter (see https://github.com/phax/en16931-cii2ubl and https://github.com/phax/en16931-ubl2cii) nor a purifier (see https://github.com/phax/en16931-purifier):
**everything the input contains is preserved** - extensions, attachments, unknown but schema valid content - and only what the new edition moves or requires is rewritten.
The facts about the standard (editions, BT-24 detection, namespaces) come from https://github.com/phax/en16931-basics.

This is a Java 17+ library (`en16931-update`) plus a command line client (`en16931-update-cli`).
The project is not released yet.

## What it does and does not do

The 2017 document is read with the **2026** XML model - the namespaces are identical and a 2017 document is valid against the 2026 schema - rewritten in place and written again.
Reading validates against the 2026 schema, so an input that is not valid there is **rejected**, not repaired.
The rules are listed below; every applied rewrite is reported with its rule ID, so the change is auditable.

| Rule | Syntax | What changes |
|------|--------|--------------|
| R1 | all | BT-24: the prefix `urn:cen.eu:en16931:2017` becomes `urn:cen.eu:en16931:2026`, a CIUS suffix like `#compliant#...` is kept (with a warning). Can be overridden. |
| R2 | all | BT-10 buyer reference: UBL `cbc:BuyerReference` becomes `cac:BuyerAssignedReference/cbc:BuyerReference`, CII `ram:BuyerReference` becomes `ram:BuyerReferenceID`. |
| R3 | UBL | BG-1 notes: `cbc:Note` with an optional `#code#` prefix becomes `cac:Annotation` with `cbc:SubjectCode` (BT-21) and `cbc:AnnotationContent` (BT-22). |
| R4 | UBL Credit Note | BT-9: `cac:PaymentMeans/cbc:PaymentDueDate` becomes `cbc:DueDate`. |
| R5 | UBL Credit Note | BT-11: an additional document reference becomes `cac:ProjectReference` - but only if it can be told from a supporting document, see below. |
| R6 | UBL | BT-32-2: a tax scheme of the Seller other than `VAT` becomes `LOC`. |
| R7 | UBL | BG-24 supporting documents get `cbc:DocumentTypeCode` 916 with `@listID` 1001 (BT-122-1). CII already has it. |
| R9 | UBL | `cbc:UBLVersionID` 2.1 becomes 2.5. |
| C1 | all | Check: the scheme identifier of a party identifier is mandatory in 2026 (BT-29-1, 30-1, 46-1, 47-1, 60-1, 61-1, 71-1). |
| C2 | all | Check: the filename of an attached document is mandatory in 2026 (BT-125-2). |

Everything new in 2026 (BG-33 to BG-39 and BT-166 to BT-220) is optional and has no source in a 2017 document, so it is never written.
The complete rule derivation, including the classification of **every** row of the 2026 mapping table, is in [docs/update-rules.md](docs/update-rules.md).

### Things you need to know

* **Identifiers without a scheme identifier (C1) are the rule, not the exception.** 94 of the 103 CII and all 18 UBL test files of this project have at least one. In CII, 2026 maps these business terms to `ram:GlobalID` only, so a 2017 `ram:ID` is no longer one. The updater does **not** invent a scheme: by default it reports a warning and leaves the element as it is. With `--strict` (`setStrict (true)`) this is an error and nothing is written. Inserting the 2026 "Buyer assigned" / "Seller assigned" schemes is not implemented, because their exact values are not confirmed yet (see [docs/todo.md](docs/todo.md)).
* **ID-only references of a Credit Note** (R5): UBL 2.1 Credit Notes have no project reference, so a reference without type, description and attachment is either BT-11 or a supporting document, and the document does not tell. The default is to leave it alone and warn. `--cn-id-only-reference project` moves it if it is the only one, `document` makes it a supporting document.
* **No 2026 validation artefact exists yet.** The output is checked against the 2026 XML schema when written, and the tests of this project check every rule; but there is no Schematron to confirm that an updated document is EN 16931:2026 conformant. The 2017 Schematron is not usable on the result.
* The input is not repaired: of the 121 test inputs, 88 are valid against the 2017 Schematron (all UBL, 70 of 103 CII); the others stay as invalid as they were.
* A document with no usable BT-24 (like legacy ZUGFeRD ones) needs the source edition to be set explicitly.
* The XML parser rejects any DOCTYPE, so external entities and entity expansion attacks are not possible.

## Command line

```
java -jar en16931-update-cli-full.jar -t out invoice.xml credit-note.xml "*.xml" some-directory
```

| Option | Default | Meaning |
|--------|---------|---------|
| `-t`, `--target` | `.` | Target directory |
| `--output-suffix` | `-2026` | Suffix added to the file name |
| `--source-edition` | detected from BT-24 | `2017` or `2026`: treat all sources as this edition |
| `--on-2026` | `skip` | A file that already is EN 16931:2026: `skip` (warn, write nothing), `copy` (write unchanged), `fail` |
| `--strict` | off | A failed check (C1, C2) is an error and nothing is written |
| `--cn-id-only-reference` | `keep` | `keep`, `project` or `document` (R5) |
| `--customization-id` | | Write this BT-24 instead of rewriting the one of the source |
| `--profile-id` | | Write this BT-23 instead of keeping the one of the source |
| `--verbose` | off | Debug logging |
| `--disable-wildcard-expansion` | off | Do not expand `*`, `?` and `[...]` (relative to the working directory) |

The exit code is 0 if no file failed and 1 if at least one did; a skipped file is not a failure.
A file is only written if it was updated without errors and the result is valid against the 2026 schema, and a source file is never overwritten.

## Library

```java
final ErrorList aErrorList = new ErrorList ();
final UpdateResult aResult = new EN16931UpdateDispatcher ().setStrict (false)
                                                           .setCreditNoteIDOnlyReferenceHandling (ECreditNoteIDOnlyReferenceHandling.KEEP)
                                                           .update (new File ("invoice.xml"), aErrorList);
if (aResult.hasDocument () && aErrorList.containsNoError ())
  try (final OutputStream aOS = Files.newOutputStream (new File ("invoice-2026.xml").toPath ()))
  {
    EN16931UpdateDispatcher.writeDocument (aResult.getDocument (), aOS, aErrorList);
  }
```

`EN16931UpdateDispatcher` detects the syntax kind from the root element and the source edition from BT-24 (it cannot be taken from the namespaces, which are identical in all CII releases and all UBL 2.x versions), and returns an `UpdateResult`: `UPDATED`, `COPIED`, `SKIPPED` or `FAILED`.
The document is a CII D25A `CrossIndustryInvoiceType`, a UBL 2.5 `InvoiceType` or a UBL 2.5 `CreditNoteType`.
An update is successful only if there is a document **and** the error list contains no error - warnings and infos (every applied rule is an info) do not count.
The updaters themselves (`CIID16BToCIID25AUpdater`, `UBL21InvoiceToUBL25InvoiceUpdater`, `UBL21CreditNoteToUBL25CreditNoteUpdater`) can be used directly.

## Build

```
mvn clean verify
java -jar en16931-update-cli/target/en16931-update-cli-full.jar --help
```

See [docs/plan-1.0.0.md](docs/plan-1.0.0.md) for the design decisions and the verification, and [docs/todo.md](docs/todo.md) for what is still open.

This library is licensed under the Apache License Version 2.0.

# News and noteworthy

v1.0.0 - work in progress
* Initial release.
* Added the updater from EN 16931:2017 to EN 16931:2026 for CII D16B to D25A, UBL 2.1 Invoice to UBL 2.5 Invoice and UBL 2.1 Credit Note to UBL 2.5 Credit Note.
* Added the command line client `en16931-update-cli`.
  The exit code is 1 if a file failed.
  A file is only written if the result is valid against the 2026 schema.
