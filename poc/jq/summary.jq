$cat[0] as $c
| $vio[0] as $v
| "# TestNG test catalog\n\n"
  + "Extracted with ast-grep \($c.repo.astGrepVersion); no timestamps, so the output is diffable.\n\n"
  + "## Counts\n\n"
  + "- packages: \([$c.packages[].package] | unique | length)\n"
  + "- classes: \($c.classes | length)\n"
  + "- tests: \($c.tests | length)\n"
  + "- providers: \($c.providers | length)\n"
  + "- hooks: \($c.hooks | length)\n"
  + "- violations: \($v.violations | length)\n\n"
  + "## Tests\n\n"
  + "| Class | Method | Groups | Provider | Description |\n"
  + "| --- | --- | --- | --- | --- |\n"
  + ([$c.tests[]
      | "| \(.className) | \(.method) | \(.effectiveGroups | join(",")) | \(.dataProvider // "-") | \((.description // "-") | gsub("\\|"; "\\|")) |"]
     | join("\n"))
  + "\n\n## Violations\n\n"
  + "| Rule | Severity | Location | Message |\n"
  + "| --- | --- | --- | --- |\n"
  + ([$v.violations[]
      | "| \(.ruleId) | \(.severity) | \(.file):\(.line) | \(.message | gsub("\\|"; "\\|")) |"]
     | join("\n"))
  + "\n"
