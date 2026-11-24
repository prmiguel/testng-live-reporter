# rules

ast-grep YAML rules. Two families, separated by ID so `--filter` can run them independently.

## Conventions

- **Extraction:** `extract.testng.<record>`, severity `hint`, never fails the scan.
- **Violation:** `violate.testng.<name>`, severity `error`, exit code 1 when it matches.
- **Policy:** not YAML — `poc/jq/policy.jq`, because the values (group vocabulary, TMS regex)
  are project configuration and YAML has no substitution.
- Every extraction rule tags its match with `metadata.record`; jq switches on that, never on
  the rule id, so adding a rule needs no jq change for an existing record type.

## Extraction rules

| Rule | Matches | Record | Emits |
| --- | --- | --- | --- |
| `extract.testng.package` | `package_declaration` | `package` | file, text |
| `extract.testng.class` | `class_declaration` + name | `class` | name, range |
| `extract.testng.class-super` | class with `superclass` | `class-super` | name, superclass |
| `extract.testng.method` | `method_declaration` + name | `method` | name, range, text |
| `extract.testng.param` | `formal_parameter` | `param` | type, name |
| `extract.testng.annotation` | `annotation` / `marker_annotation` | `annotation` | name, range |
| `extract.testng.attr` | `element_value_pair` | `attr` | key, value |
| `extract.testng.annotation-string` | string inside an annotation | `annotation-string` | text, owning annotation |
| `extract.testng.call` | `method_invocation` | `call` | callee name |

## Violation rules

| Rule | Check | Catalog rule |
| --- | --- | --- |
| `violate.testng.class-suffix` | test class does not end in `Test` | TN-NAME-001 |
| `violate.testng.missing-description` | `@Test` method has no `description` | TN-META-001 |
| `violate.testng.disabled-test` | `enabled = false` | TN-META-009 |
| `violate.testng.priority-ordering` | any `priority` | TN-META-007 |
| `violate.testng.broad-expected-exceptions` | `expectedExceptions` without a message regex | TN-META-010 |
| `violate.testng.always-run-on-setup` | `alwaysRun` on `@Before*` | TN-AP-008 |
| `violate.testng.missing-assertion` | no `assert*`/`fail`/`verify` call | TN-ASRT-001 |
| `violate.testng.system-out` | `System.out` | TN-LOG-002 |
| `violate.testng.thread-sleep` | `Thread.sleep` | TN-AP-003 |

The `catalogRule` metadata points at the base TestNG rule catalog (`TN-*`) this tool was designed against.

## Shared utility

`rules/utils/test-method.yml` defines the global util `testMethod` (a `method_declaration` with
an `annotation` or `marker_annotation` named `Test`, `stopBy: end`). Global util files must look
like a rule (`id`, `language`, `rule`); a bare `utils:` map is rejected.

## Add something

| Need | Change |
| --- | --- |
| New structural record | add a rule under `rules/extract/`, pick a new `metadata.record`, extend jq |
| New violation that is purely syntactic | add a rule under `rules/violations/` |
| New project value (group, TMS format) | edit `poc/config/*`, not YAML |
| Run only extraction | `--filter '^extract\.'` |
| Run only violations | `--filter '^violate\.'` |

## Verify

```bash
ast-grep scan -c sgconfig.yml --filter '^extract\.' --json=stream --include-metadata \
  poc/fixtures/java | jq -r '.metadata.record' | sort | uniq -c
ast-grep scan -c sgconfig.yml --filter '^violate\.' poc/fixtures/java
```

The second command exits 1 because the fixture intentionally contains violations.
