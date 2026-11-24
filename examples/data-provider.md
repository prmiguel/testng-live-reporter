# Data provider and parameters

```java
@DataProvider(name = "cards", parallel = true)
public static Object[][] cards() {
    return new Object[][]{{"visa", "ok"}, {"amex", "declined"}};
}

@Test(dataProvider = "cards", description = "Card provider drives settlement")
public void settlesCard(String card, String expected) {
    Assert.assertEquals(settle(card), expected);
}

@Test(dataProvider = "shared", dataProviderClass = SharedProviders.class)
public void sharedProvider(String value) { … }

@Test(description = "Parameters come from the suite")
@Parameters({"env", "browser"})
public void readsSuiteParameters() { … }
```

Catalog records (trimmed):

```json
{"providers": [
  {"className": "PaymentDataProviderTest", "method": "cards", "name": "cards",
   "static": true, "parallel": true, "parameters": []},
  {"className": "SharedProviders", "method": "shared", "name": "shared",
   "static": true, "parallel": false, "parameters": []}
]}
```

```json
{"tests": [
  {"method": "settlesCard", "dataProvider": "cards", "dataProviderClass": null,
   "parametersFromMethod": ["card", "expected"]},
  {"method": "sharedProvider", "dataProvider": "shared",
   "dataProviderClass": "SharedProviders"},
  {"method": "readsSuiteParameters", "parameters": ["env", "browser"]}
]}
```

`parameters` (suite parameters) and `parametersFromMethod` (the method's own arguments) are
separate on purpose: the first comes from `@Parameters`, the second from the signature.
