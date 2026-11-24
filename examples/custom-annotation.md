# Custom annotation

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface TmsId {
    String value();
}
```

```java
@Test(description = "Valid credentials open the dashboard")
@TmsId("TMS-101")
public void validCredentialsOpenDashboard() { … }
```

```json
{
  "customAnnotations": {"TmsId": "TMS-101"}
}
```

The annotation name and its positional string are captured generically. Which names count as
TMS ids is configuration:

```json
// poc/config/tms-annotations.json
["TmsId", "TmsLink"]
```

```text
# poc/config/tms-id.regex
^TMS-[0-9]+$
```

Policy then produces `policy.testng.invalid-tms-id`, `…missing-tms-id` and
`…duplicate-tms-id`. No YAML changes when the annotation is renamed.
