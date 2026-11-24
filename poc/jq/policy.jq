# Project-policy checks that need configured values (ast-grep YAML cannot parameterise them).
# Input: catalog.json. Requires --rawfile vocab, --rawfile tmsPat, --slurpfile tmsAnns.

def trim: gsub("^\\s+|\\s+$"; "");

def rec($id; $sev; $msg; $t): {
  ruleId: $id,
  severity: $sev,
  message: $msg,
  file: $t.file,
  line: $t.line,
  catalogRule: null
};

def tmsEntry($t):
  (($t.customAnnotations // {}) | to_entries
   | map(select(.key as $k | ($tmsAnns[0] | index($k)) != null))
   | .[0]);

def tmsValue($t): (tmsEntry($t) | .value // null);

($vocab | split("\n") | map(trim) | map(select(length > 0))) as $groups
| ($tmsPat | trim) as $pat
| . as $catalog
| [
    (.tests[] as $t
     | (($t.effectiveGroups // []) - $groups)[]
     | rec("policy.testng.unknown-group"; "warning";
           "Unknown group '\(.)' on \($t.className).\($t.method)"; $t)),

    (.tests[] as $t
     | select(tmsEntry($t) == null)
     | rec("policy.testng.missing-tms-id"; "warning";
           "No TMS id on \($t.className).\($t.method)"; $t)),

    (.tests[] as $t
     | (tmsValue($t)) as $v
     | select($v != null and (($v | test($pat)) | not))
     | rec("policy.testng.invalid-tms-id"; "error";
           "TMS id '\($v)' does not match \($pat)"; $t)),

    (.tests[] as $t
     | select($t.description != null and (($t.description | length) > 60))
     | rec("policy.testng.description-too-long"; "warning";
           "Description is \($t.description | length) characters (max 60): \($t.className).\($t.method)"; $t)),

    (.tests | group_by([.file, .className, .method])
     | map(select(length > 1))[]
     | .[0]
     | rec("policy.testng.duplicate-method"; "warning";
           "Duplicate test method \(.className).\(.method)"; .))
  ]
| . + [
    ($catalog.tests | map({t: ., v: tmsValue(.)}) | map(select(.v != null))
     | group_by(.v) | map(select(length > 1))[]
     | .[0].v as $v
     | .[] | rec("policy.testng.duplicate-tms-id"; "error";
                  "TMS id \($v) is used by more than one test"; .t))
  ]
| . + [
    ($catalog.providers) as $ps
    | $catalog.tests[] as $t
    | select($t.dataProvider != null)
    | select(($ps | map(select(.name == $t.dataProvider
          and (.className == ($t.dataProviderClass // $t.className)))) | length) == 0)
    | rec("policy.testng.unresolved-provider"; "error";
          "dataProvider '\($t.dataProvider)' not found for \($t.className).\($t.method)"; $t)
  ]
| sort_by(.file, .line, .ruleId)
