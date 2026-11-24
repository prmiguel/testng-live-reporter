# Java node kinds used by the rules

Pinned against **ast-grep 0.51.3** (tree-sitter-java). Verify with:

```bash
ast-grep run -l java --kind annotation --json=compact poc/fixtures/java | jq length
```

## Kinds observed in the fixtures

| Construct | Kind | Useful fields / captures |
| --- | --- | --- |
| Package | `package_declaration` | whole text (`package …;`) |
| Class | `class_declaration` | `name`, `superclass`, `modifiers`, `body` |
| Method | `method_declaration` | `modifiers`, `name`, `parameters`, `body` |
| Formal parameter | `formal_parameter` | `type`, `name` |
| Annotation with arguments | `annotation` | `name`; `arguments` → `annotation_argument_list` |
| Annotation without arguments | `marker_annotation` | `name` (e.g. bare `@Test`) |
| Named attribute | `element_value_pair` | `key`, `value` |
| Array value | `element_value_array_initializer` | `string_literal` children |
| String literal | `string_literal` | whole text, including quotes |
| Call | `method_invocation` | `object`, `name`, `arguments` |

Counts on the fixture set (7 files): `annotation` 20, `marker_annotation` 2,
`element_value_pair` 26, `element_value_array_initializer` 9, `method_declaration` 19,
`class_declaration` 6, `package_declaration` 7, `formal_parameter` 6,
`method_invocation` 16, `string_literal` 47.

## Traps

- **`@Test` vs `@Test(...)` are different kinds.** A bare `@Test` is `marker_annotation`;
  `@Test(enabled = false)` is `annotation`. Match both with `any`.
- Annotations attach under the method's or class's `modifiers` node, so a containment rule
  needs `stopBy: end`; the default is `stopBy: neighbor` and will miss them.
- A `method_declaration`'s range **includes its annotations**. A method's line therefore starts
  at its first annotation; the catalog uses the `name` node's line instead.
- `class_declaration` ranges also include class-level annotations.
- `@Parameters({"env", "browser"})` has no `element_value_pair`: the strings sit directly in
  the annotation. `@TmsId("TMS-101")` is the same shape (one `element_value`).
- Java `@interface` declarations are `annotation_type_declaration`, not `class_declaration`;
  they are not test classes.
- Lines and columns in ast-grep JSON are **0-based**; `end` is exclusive.
