# en16931-update

Library and command line tool that updates EN 16931:2017 invoices and credit notes to EN 16931:2026
**within the same syntax**:

| Syntax | Input (EN 16931:2017) | Output (EN 16931:2026) |
|--------|-----------------------|------------------------|
| CII | D16B | D25A |
| UBL Invoice | 2.1 | 2.5 |
| UBL Credit Note | 2.1 | 2.5 |

It is neither a syntax converter (see https://github.com/phax/en16931-cii2ubl and
https://github.com/phax/en16931-ubl2cii) nor a purifier (see https://github.com/phax/en16931-purifier):
everything the input contains is preserved, only what the new edition moves or requires is rewritten.
The facts about the standard live in https://github.com/phax/en16931-basics.

**Status: work in progress - the update logic is not implemented yet.**
See [docs/plan-1.0.0.md](docs/plan-1.0.0.md) and [docs/update-rules.md](docs/update-rules.md).

This library is licensed under the Apache License Version 2.0.

# News and noteworthy

v1.0.0 - work in progress
* Initial project structure.
