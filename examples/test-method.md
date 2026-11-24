# Test method

```java
@Test(groups = {"smoke", "ui"})
public class LoginTest extends BaseTest {

    @Test(description = "Valid credentials open the dashboard", groups = {"login"}, priority = 1)
    @TmsId("TMS-101")
    public void validCredentialsOpenDashboard() {
        Assert.assertEquals(login("user", "secret"), "dashboard");
    }
}
```

Catalog record (trimmed):

```json
{
  "className": "LoginTest",
  "method": "validCredentialsOpenDashboard",
  "description": "Valid credentials open the dashboard",
  "groups": ["login"],
  "classGroups": ["smoke", "ui"],
  "effectiveGroups": ["login", "smoke", "ui"],
  "priority": 1,
  "customAnnotations": {"TmsId": "TMS-101"},
  "assertionCalls": ["assertEquals"]
}
```

`groups` comes from the method annotation, `classGroups` from the class annotation, and the
union is computed in `poc/jq/catalog.jq`. No rule is specific to `description` or `priority`.
