# TestNG test catalog

Extracted with ast-grep 0.51.3; no timestamps, so the output is diffable.

## Counts

- packages: 3
- classes: 6
- tests: 11
- providers: 2
- hooks: 5
- violations: 25

## Tests

| Class | Method | Groups | Provider | Description |
| --- | --- | --- | --- | --- |
| BadlyNamed | noAssertion |  | - | - |
| BadlyNamed | typoGroup | payment | - | Typo in the group name |
| LoginTest | validCredentialsOpenDashboard | login,smoke,ui | - | Valid credentials open the dashboard |
| LoginTest | rejectsExpiredPassword | login,smoke,ui | - | This description is deliberately far longer than sixty characters so the policy check fires |
| LoginTest | legacyFlow | smoke,ui | - | - |
| PaymentDataProviderTest | settlesCard | payments | cards | Card provider drives settlement |
| PaymentDataProviderTest | sharedProvider | payments | shared | Shared provider resolves |
| PaymentDataProviderTest | readsSuiteParameters | payments | - | Parameters come from the suite |
| PaymentDataProviderTest | usesMissingProvider | payments | missing | Provider name is not declared |
| PaymentDataProviderTest | repeatedInvocation | payments | - | Repeated invocation |
| ApiSmokeTest | health | api,smoke | - | Health endpoint responds |

## Violations

| Rule | Severity | Location | Message |
| --- | --- | --- | --- |
| violate.testng.class-suffix | error | poc/fixtures/java/com/acme/tests/BadlyNamed.java:6 | Test class 'BadlyNamed' should end with the configured suffix (default 'Test') |
| violate.testng.missing-assertion | error | poc/fixtures/java/com/acme/tests/BadlyNamed.java:8 | @Test method 'noAssertion' contains no assertion call |
| violate.testng.missing-description | error | poc/fixtures/java/com/acme/tests/BadlyNamed.java:8 | @Test method 'noAssertion' has no description |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/BadlyNamed.java:9 | No TMS id on BadlyNamed.noAssertion |
| policy.testng.invalid-tms-id | error | poc/fixtures/java/com/acme/tests/BadlyNamed.java:15 | TMS id 'TMS-ABC' does not match ^TMS-[0-9]+$ |
| policy.testng.unknown-group | warning | poc/fixtures/java/com/acme/tests/BadlyNamed.java:15 | Unknown group 'payment' on BadlyNamed.typoGroup |
| violate.testng.always-run-on-setup | error | poc/fixtures/java/com/acme/tests/BaseTest.java:8 | alwaysRun = true belongs on teardown, not on @Before* setup |
| violate.testng.priority-ordering | error | poc/fixtures/java/com/acme/tests/LoginTest.java:9 | @Test(priority) orders methods inside a class; use it only for a real dependency |
| policy.testng.duplicate-tms-id | error | poc/fixtures/java/com/acme/tests/LoginTest.java:11 | TMS id TMS-101 is used by more than one test |
| policy.testng.description-too-long | warning | poc/fixtures/java/com/acme/tests/LoginTest.java:18 | Description is 91 characters (max 60): LoginTest.rejectsExpiredPassword |
| policy.testng.duplicate-tms-id | error | poc/fixtures/java/com/acme/tests/LoginTest.java:18 | TMS id TMS-101 is used by more than one test |
| violate.testng.disabled-test | error | poc/fixtures/java/com/acme/tests/LoginTest.java:23 | @Test is disabled with enabled = false; remove it or move it to the skip group |
| violate.testng.missing-assertion | error | poc/fixtures/java/com/acme/tests/LoginTest.java:23 | @Test method 'legacyFlow' contains no assertion call |
| violate.testng.missing-description | error | poc/fixtures/java/com/acme/tests/LoginTest.java:23 | @Test method 'legacyFlow' has no description |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/LoginTest.java:24 | No TMS id on LoginTest.legacyFlow |
| violate.testng.system-out | error | poc/fixtures/java/com/acme/tests/LoginTest.java:25 | System.out bypasses the logging facade and the configured log levels |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:24 | No TMS id on PaymentDataProviderTest.settlesCard |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:30 | No TMS id on PaymentDataProviderTest.sharedProvider |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:36 | No TMS id on PaymentDataProviderTest.readsSuiteParameters |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:41 | No TMS id on PaymentDataProviderTest.usesMissingProvider |
| policy.testng.unresolved-provider | error | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:41 | dataProvider 'missing' not found for PaymentDataProviderTest.usesMissingProvider |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/PaymentDataProviderTest.java:48 | No TMS id on PaymentDataProviderTest.repeatedInvocation |
| violate.testng.broad-expected-exceptions | error | poc/fixtures/java/com/acme/tests/api/ApiSmokeTest.java:19 | expectedExceptions without expectedExceptionsMessageRegExp passes on any matching throw |
| policy.testng.missing-tms-id | warning | poc/fixtures/java/com/acme/tests/api/ApiSmokeTest.java:21 | No TMS id on ApiSmokeTest.health |
| violate.testng.thread-sleep | error | poc/fixtures/java/com/acme/tests/api/ApiSmokeTest.java:23 | Thread.sleep is a fixed wait; use an explicit condition or the framework's wait |

