# Frontend i18n bundle baseline

Initial Paraglide baseline captured on 2026-07-15 with the sole production locale `en-US`.

| Metric | Baseline |
| --- | ---: |
| Largest JavaScript chunk | 64.21 KiB |
| Total JavaScript chunks | 171.45 KiB |
| Client chunk limit | 500 KiB |

The largest ten chunks are emitted by `pnpm build`. When adding a production locale, compare the
main entry, largest chunk, and total JavaScript size against this baseline before introducing locale
or namespace splitting.
