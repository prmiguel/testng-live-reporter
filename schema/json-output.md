# ast-grep JSON output

Verified on **ast-grep 0.51.3** with:

```bash
ast-grep scan -c sgconfig.yml --filter '^extract\.' --json=stream --include-metadata <paths>
```

`--json` accepts `pretty` (default), `compact` or `stream`; `stream` is one JSON object per
line (NDJSON) and is the form the jq join consumes. `--include-metadata` is required or the
rule's `metadata` block is omitted.

## Scan record (trimmed)

```json
{
  "text": "priority = 1",
  "range": {
    "byteOffset": {"start": 293, "end": 294},
    "start": {"line": 9, "column": 95},
    "end":   {"line": 9, "column": 96}
  },
  "file": "poc/fixtures/java/com/acme/tests/LoginTest.java",
  "lines": "…",
  "charCount": {"leading": 0, "trailing": 0},
  "language": "Java",
  "metaVariables": {
    "single": {
      "K": {"text": "priority", "range": {"…": "…"}},
      "V": {"text": "1", "range": {"…": "…"}}
    },
    "multi": {"secondary": []},
    "transformed": {}
  },
  "ruleId": "extract.testng.attr",
  "severity": "hint",
  "message": "attribute $K",
  "note": null,
  "metadata": {"record": "attr"}
}
```

| Field | Meaning |
| --- | --- |
| `range.start/end` | 0-based line/column, `end` exclusive; `byteOffset` for byte-precise joins |
| `metaVariables.single` | one node per `$VAR`, with its own `text` and `range` |
| `metaVariables.multi` | `$$$ARGS` as an array under `secondary` |
| `metadata` | rule metadata, only with `--include-metadata` |
| `ruleId`, `severity`, `message` | present in `scan`, absent from `run --json` |

Notes:

- Captures inside `has`/`inside` **do** appear in `metaVariables.single` (probed); the join still
  uses ranges so it does not depend on that behaviour.
- `--filter <regex>` selects rules by id, which is how the extraction and violation passes are
  separated.
- Exit code is **1** when an `error`-severity rule matches, 0 otherwise. Extraction rules use
  `hint`; violation rules use `error`, so the runner treats exit 1 as expected.
- `--slurpfile` treats NDJSON as a list of documents (the array is the file value); a file
  containing one JSON array is wrapped again (`$x[0]`). The scripts account for both.
