# Merge ast-grep violation matches with policy violations into one deterministic document.

def norm_ast: {
  ruleId,
  severity,
  message,
  file,
  line: .range.start.line,
  column: .range.start.column,
  catalogRule: (.metadata.catalogRule // null),
  source: "ast-grep"
};

def norm_pol: . + {source: "policy"};

{
  schemaVersion: 1,
  violations: (
    ((($ast // []) | map(norm_ast))
     + (($policy[0] // []) | map(norm_pol)))
    | sort_by(.file, .line, .ruleId)
  )
}
