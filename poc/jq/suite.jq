def arr:
  if . == null then [] elif type == "array" then . else [.] end;

.suite as $s
| {
    file: $file,
    name: $s["+@name"],
    parallel: ($s["+@parallel"] // null),
    threadCount: (($s["+@thread-count"] // null) | if . == null then null else tonumber end),
    configFailurePolicy: ($s["+@configfailurepolicy"] // null),
    groupByInstances: ($s["+@group-by-instances"] // null),
    preserveOrder: ($s["+@preserve-order"] // null),
    parameters: ((($s.parameter | arr) | map({(. ["+@name"]): .["+@value"]}) | add) // {}),
    tests: ((($s.test | arr) | map({
      name: .["+@name"],
      preserveOrder: (.["+@preserve-order"] // null),
      groupByInstances: (.["+@group-by-instances"] // null),
      parameters: (((.parameter | arr) | map({(. ["+@name"]): .["+@value"]}) | add) // {}),
      groups: {
        include: ((.groups.run.include | arr) | map(.["+@name"])),
        exclude: ((.groups.run.exclude | arr) | map(.["+@name"]))
      },
      packages: ((.packages.package | arr) | map(.["+@name"])),
      methods: {
        include: ((.methods.include | arr) | map(.["+@name"])),
        exclude: ((.methods.exclude | arr) | map(.["+@name"]))
      },
      classes: ((.classes.class | arr) | map(.["+@name"]))
    }))),
    suiteFiles: ((($s["suite-files"]["suite-file"] | arr) | map(.["+@path"]))),
    listeners: ((($s.listeners.listener | arr) | map(.["+@class-name"])))
  }
