package com.seleniumboot.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

public class FailureDetailReportTest {

    @Test
    public void reportShowsRecordAndEscapesCapturedValues() throws Exception {
        JsonNode detail = new ObjectMapper().readTree("""
                {
                  "failingLocator": "By.cssSelector: <script>",
                  "applicationFrame": "example.Page.submit(Page.java:12)",
                  "lastSuccessfulStep": "Fill form",
                  "pageUrl": "https://app.example/checkout?a=1&b=2",
                  "pageTitle": "Checkout",
                  "consoleErrors": ["TypeError: <bad>"],
                  "healEvents": [{"originalLocator":"By.id: old","healedLocator":"By.id: new","strategy":"id"}],
                  "groupContext": {"size":2,"representativeTest":"example.First.run","representativeTrace":"traces/first.zip"}
                }
                """);
        Method method = com.seleniumboot.reporting.HtmlReportGenerator.class
                .getDeclaredMethod("buildFailureDetailPanel", JsonNode.class);
        method.setAccessible(true);
        String html = (String) method.invoke(null, detail);

        Assert.assertTrue(html.contains("Failure detail"), html);
        Assert.assertTrue(html.contains("Failing locator"), html);
        Assert.assertTrue(html.contains("Self-healing events"), html);
        Assert.assertTrue(html.contains("Console errors"), html);
        Assert.assertTrue(html.contains("Same-cause group"), html);
        Assert.assertTrue(html.contains("View Trace"), html);
        Assert.assertTrue(html.contains("&lt;script&gt;"), html);
        Assert.assertTrue(html.contains("a=1&amp;b=2"), html);
        Assert.assertFalse(html.contains("<script>"), html);
    }

    @Test
    public void reportShowsFailureTypeAndSuggestedAction() throws Exception {
        JsonNode test = new ObjectMapper().readTree("""
                {
                  "status": "FAILED",
                  "failureCategory": "LOCATOR_NOT_FOUND",
                  "failureEvidence": "NoSuchElementException in cause chain",
                  "failureType": "TEST_BUG",
                  "suggestedAction": "Check if locator '#submit' changed"
                }
                """);
        Method method = com.seleniumboot.reporting.HtmlReportGenerator.class
                .getDeclaredMethod("buildFailureWhy", JsonNode.class);
        method.setAccessible(true);
        String html = (String) method.invoke(null, test);

        Assert.assertTrue(html.contains("failure-type-badge"), html);
        Assert.assertTrue(html.contains("failure-type-test-bug"), html);
        Assert.assertTrue(html.contains("Test bug"), html);
        Assert.assertTrue(html.contains("failure-badge"), html);
        Assert.assertTrue(html.contains("Locator not found"), html);
        Assert.assertTrue(html.contains("failure-action"), html);
        Assert.assertTrue(html.contains("Check if locator"), html);
    }
}
