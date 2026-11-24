# Suite XML

```xml
<suite name="acme" parallel="methods" thread-count="4" configfailurepolicy="skip"
       group-by-instances="false" preserve-order="true">
    <parameter name="env" value="ci"/>
    <parameter name="browser" value="chrome"/>
    <test name="smoke" preserve-order="true">
        <groups><run>
            <include name="smoke"/>
            <exclude name="quarantine"/>
        </run></groups>
        <classes>
            <class name="com.acme.tests.LoginTest"/>
            <class name="com.acme.tests.api.ApiSmokeTest"/>
        </classes>
    </test>
    <test name="regression" group-by-instances="true">
        <parameter name="tenant" value="acme"/>
        <packages><package name="com.acme.tests.*"/></packages>
        <methods><include name=".*"/><exclude name="legacyFlow"/></methods>
        <classes><class name="com.acme.tests.PaymentDataProviderTest"/></classes>
    </test>
    <suite-files><suite-file path="ci/testng-ci.xml"/></suite-files>
    <listeners>
        <listener class-name="com.acme.listeners.ReportListener"/>
    </listeners>
</suite>
```

```bash
yq -p xml -o json poc/fixtures/testng.xml | jq -f poc/jq/suite.jq --arg file poc/fixtures/testng.xml
```

```json
{
  "name": "acme",
  "parallel": "methods",
  "threadCount": 4,
  "configFailurePolicy": "skip",
  "groupByInstances": "false",
  "preserveOrder": "true",
  "parameters": {"env": "ci", "browser": "chrome"},
  "tests": [
    {"name": "smoke", "preserveOrder": "true",
     "groups": {"include": ["smoke"], "exclude": ["quarantine"]},
     "classes": ["com.acme.tests.LoginTest", "com.acme.tests.api.ApiSmokeTest"]},
    {"name": "regression", "groupByInstances": "true",
     "parameters": {"tenant": "acme"},
     "packages": ["com.acme.tests.*"],
     "methods": {"include": [".*"], "exclude": ["legacyFlow"]},
     "classes": ["com.acme.tests.PaymentDataProviderTest"]}
  ],
  "suiteFiles": ["ci/testng-ci.xml"],
  "listeners": ["com.acme.listeners.ReportListener"]
}
```

yq prefixes attributes with `+@` and collapses single children to objects; `suite.jq` makes the
shape stable. ast-grep cannot read XML at all, which is why this is a separate stage.
