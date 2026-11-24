# Build a TestNG test catalog from ast-grep NDJSON.
# Input: slurped extraction records. Requires --slurpfile suite <normalised suite json>.
# Extraction rules are generic; classification (Test/DataProvider/Before*/After*) happens here.

def contains_inner($outer; $inner):
  ($outer.file == $inner.file)
  and (
    ($outer.range.start.line < $inner.range.start.line)
    or ($outer.range.start.line == $inner.range.start.line
        and $outer.range.start.column <= $inner.range.start.column)
  )
  and (
    ($outer.range.end.line > $inner.range.end.line)
    or ($outer.range.end.line == $inner.range.end.line
        and $outer.range.end.column >= $inner.range.end.column)
  );

def inside($outer): contains_inner($outer; .);
def inside_any($nodes): . as $n | $nodes | any(contains_inner(.; $n));

def unq:
  if type == "string"
  then (gsub("^\"|\"$"; "") | gsub("\\\\\""; "\""))
  else . end;

def num:
  if type == "string" then (tonumber? // null) else . end;

def anns_in($anns; $node): $anns | map(select(inside($node)));
def attrs_in($attrs; $node): $attrs | map(select(inside($node)));
def strings_in($strings; $node): $strings | map(select(inside($node)));
def calls_in($calls; $node): $calls | map(select(inside($node)));
def params_in($params; $node): $params | map(select(inside($node)));

def attr($attrs; $key): ($attrs | map(select(.metaVariables.single.K.text == $key)) | .[0]);
def attr_value($attrs; $key): (attr($attrs; $key) | if . == null then null else .metaVariables.single.V.text | unq end);
def attr_strings($attrs; $strings; $key):
  (attr($attrs; $key)) as $a
  | if $a == null then [] else ($strings | map(select(inside($a))) | map(.text | unq)) end;
def attr_present($attrs; $key): (attr($attrs; $key) != null);

def ann_names($anns): $anns | map(.metaVariables.single.ANN.text);

def stack_scope:
  if test("^BeforeSuite$|^AfterSuite$") then "suite"
  elif test("^BeforeTest$|^AfterTest$") then "test"
  elif test("^BeforeGroups$|^AfterGroups$") then "groups"
  elif test("^BeforeClass$|^AfterClass$") then "class"
  else "method" end;

. as $all
| ($all | map(select(.metadata.record == "package"))) as $packages
| ($all | map(select(.metadata.record == "class"))) as $classes
| ($all | map(select(.metadata.record == "class-super"))) as $supers
| ($all | map(select(.metadata.record == "method"))) as $methods
| ($all | map(select(.metadata.record == "param"))) as $params
| ($all | map(select(.metadata.record == "annotation"))) as $anns
| ($all | map(select(.metadata.record == "attr"))) as $attrs
| ($all | map(select(.metadata.record == "annotation-string"))) as $strings
| ($all | map(select(.metadata.record == "call"))) as $calls

| def package_of($file): ($packages | map(select(.file == $file)) | .[0].text // null
    | if . == null then null else (sub("^package[ \t]+"; "") | sub(";[ \t]*$"; "")) end);
  def class_of($m): ($classes | map(select(contains_inner(.; $m))) | sort_by(.range.start.line) | last);
  def class_anns($cl): anns_in($anns; $cl) | map(select(inside_any($methods) | not));
  def class_groups($cl):
    (class_anns($cl) | map(select(.metaVariables.single.ANN.text == "Test")) | .[0]) as $ta
    | if $ta == null then [] else attr_strings(attrs_in($attrs; $ta); $strings; "groups") end;
  def method_anns($m): anns_in($anns; $m);

  {
    schemaVersion: 1,
    repo: {root: ".", astGrepVersion: $version},

    packages: ($packages | map({file, package: (.text | sub("^package[ \t]+"; "") | sub(";[ \t]*$"; ""))})
               | sort_by(.file)),

    classes: ($classes
      | map(. as $cl
        | ($supers | map(select(.file == $cl.file
              and .metaVariables.single.NAME.text == $cl.metaVariables.single.NAME.text)) | .[0]) as $sup
        | {
            file: $cl.file,
            line: $cl.metaVariables.single.NAME.range.start.line,
            package: package_of($cl.file),
            name: $cl.metaVariables.single.NAME.text,
            extends: (if $sup == null then null
                      else ($sup.metaVariables.single.SUPER.text | sub("^extends[ \t]+"; "")) end),
            groups: class_groups($cl),
            annotations: (class_anns($cl) | ann_names(.) | unique)
          })
      | sort_by(.file, .line)),

    tests: ($methods
      | map(. as $m
        | (class_of($m)) as $cl
        | (method_anns($m)) as $ma
        | ($ma | map(select(.metaVariables.single.ANN.text == "Test")) | .[0]) as $ta
        | select($ta != null and $cl != null)
        | (attrs_in($attrs; $ta)) as $a
        | (strings_in($strings; $ta) | map(select(inside_any($a) | not)) | map(.text | unq)) as $positional
        | ($ma | map(select(.metaVariables.single.ANN.text == "Parameters")) | .[0]) as $pa
        | (if $pa == null then []
           else (strings_in($strings; $pa)
                 | map(select(inside_any(attrs_in($attrs; $pa)) | not))
                 | map(.text | unq))
           end) as $suiteParams
        | {
            file: $m.file,
            line: $m.metaVariables.single.M.range.start.line,
            className: $cl.metaVariables.single.NAME.text,
            method: $m.metaVariables.single.M.text,
            description: (attr_value($a; "description")),
            enabled: ((attr_value($a; "enabled") // "true") != "false"),
            priority: ((attr_value($a; "priority")) | num),
            groups: (attr_strings($a; $strings; "groups")),
            classGroups: class_groups($cl),
            effectiveGroups: ((class_groups($cl) + attr_strings($a; $strings; "groups")) | unique),
            dataProvider: (attr_value($a; "dataProvider")),
            dataProviderClass: (attr_value($a; "dataProviderClass")
              | if . == null then null else sub("\\.class$"; "") end),
            parameters: ($suiteParams),
            expectedExceptions: ((attr_value($a; "expectedExceptions") // "")
              | gsub("[{}]"; "") | split(",")
              | map(gsub("^\\s+|\\s+$"; "") | sub("\\.class$"; ""))
              | map(select(length > 0))),
            timeOut: ((attr_value($a; "timeOut")) | num),
            invocationTimeOut: ((attr_value($a; "invocationTimeOut")) | num),
            invocationCount: ((attr_value($a; "invocationCount")) | num),
            threadPoolSize: ((attr_value($a; "threadPoolSize")) | num),
            successPercentage: ((attr_value($a; "successPercentage")) | num),
            singleThreaded: ((attr_value($a; "singleThreaded") // "false") == "true"),
            alwaysRun: ((attr_value($a; "alwaysRun") // "false") == "true"),
            retryAnalyzer: (attr_value($a; "retryAnalyzer")
              | if . == null then null else sub("\\.class$"; "") end),
            expectedExceptionsMessageRegExp: (attr_value($a; "expectedExceptionsMessageRegExp")),
            dependsOn: ((attr_strings($a; $strings; "dependsOnMethods"))
                        + (attr_strings($a; $strings; "dependsOnGroups"))),
            customAnnotations: (($ma
              | map(select(.metaVariables.single.ANN.text as $n
                     | ($known[0] | index($n)) == null))
              | map(. as $ann
                | {(.metaVariables.single.ANN.text):
                   ((strings_in($strings; $ann) | map(select(inside_any(attrs_in($attrs; $ann)) | not))
                     | .[0].text // null) | if . == null then null else unq end)})
              | add) // {}),
            assertionCalls: (calls_in($calls; $m)
              | map(select(.metaVariables.single.CALL.text | test("^(assert|fail|verify)")))
              | map(.metaVariables.single.CALL.text) | unique),
            parametersFromMethod: (params_in($params; $m) | map(.metaVariables.single.PNAME.text))
          })
      | sort_by(.file, .line, .method)),

    providers: ($methods
      | map(. as $m
        | (method_anns($m) | map(select(.metaVariables.single.ANN.text == "DataProvider")) | .[0]) as $da
        | select($da != null)
        | (attrs_in($attrs; $da)) as $a
        | {
            file: $m.file,
            line: $m.metaVariables.single.M.range.start.line,
            className: (class_of($m).metaVariables.single.NAME.text),
            method: $m.metaVariables.single.M.text,
            name: (attr_value($a; "name") // $m.metaVariables.single.M.text),
            static: ($m.text | test("\\bstatic\\b")),
            parallel: ((attr_value($a; "parallel") // "false") == "true"),
            parameters: (params_in($params; $m) | map(.metaVariables.single.PNAME.text))
          })
      | sort_by(.file, .line)),

    hooks: ($methods
      | map(. as $m
        | (method_anns($m) | map(select(.metaVariables.single.ANN.text | test("^(Before|After)"))) | .[0]) as $ha
        | select($ha != null)
        | (attrs_in($attrs; $ha)) as $a
        | {
            file: $m.file,
            line: $m.metaVariables.single.M.range.start.line,
            className: (class_of($m).metaVariables.single.NAME.text),
            method: $m.metaVariables.single.M.text,
            annotation: $ha.metaVariables.single.ANN.text,
            scope: ($ha.metaVariables.single.ANN.text | stack_scope),
            groups: (strings_in($strings; $ha) | map(select(inside_any($a) | not)) | map(.text | unq)),
            alwaysRun: (attr_value($a; "alwaysRun") == "true")
          })
      | sort_by(.file, .line)),

    suite: ($suite[0] // null)
  }
