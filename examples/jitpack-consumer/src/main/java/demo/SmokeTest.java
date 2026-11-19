package demo;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Minimal fixture: enough to produce PASSED, FAILED and two data-provider executions.
 */
public class SmokeTest {

    @Test
    public void passes() {
        Assert.assertTrue(true);
    }

    @Test
    public void fails() {
        Assert.assertTrue(false, "intentional failure so the report has a FAILED event");
    }

    @DataProvider(name = "rows")
    public Object[][] rows() {
        return new Object[][]{{1}, {2}};
    }

    @Test(dataProvider = "rows")
    public void dataProviderRow(int row) {
        Assert.assertTrue(row > 0);
    }
}
