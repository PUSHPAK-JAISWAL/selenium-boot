package com.seleniumboot.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seleniumboot.cucumber.CucumberHooks;
import com.seleniumboot.driver.DriverManager;
import org.mockito.MockedStatic;
import com.seleniumboot.junit5.SeleniumBootExtension;
import com.seleniumboot.listeners.TestExecutionListener;
import com.seleniumboot.metrics.ExecutionMetrics;
import com.seleniumboot.metrics.TestTiming;
import io.cucumber.java.Scenario;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;
import org.testng.IClass;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * Unit tests verifying tag and group capture across TestNG, Cucumber, and JUnit 5.
 */
public class TestTagsCaptureTest {

    @BeforeMethod
    @AfterMethod
    public void cleanup() {
        ExecutionMetrics.reset();
        try { DriverManager.quitDriver(); } catch (Exception ignored) {}
    }

    @Test
    public void testNg_onTestStart_capturesGroups() {
        TestExecutionListener listener = new TestExecutionListener();
        ITestResult result = Mockito.mock(ITestResult.class);
        ITestNGMethod method = Mockito.mock(ITestNGMethod.class);
        IClass testClass = Mockito.mock(IClass.class);
        org.testng.ITestContext context = Mockito.mock(org.testng.ITestContext.class);
        org.testng.xml.XmlTest xmlTest = Mockito.mock(org.testng.xml.XmlTest.class);

        when(result.getMethod()).thenReturn(method);
        when(result.getTestClass()).thenReturn(testClass);
        when(result.getTestContext()).thenReturn(context);
        when(context.getCurrentXmlTest()).thenReturn(xmlTest);
        when(method.getQualifiedName()).thenReturn("com.example.TestNgSuite.loginTest");
        when(method.getMethodName()).thenReturn("loginTest");
        when(method.getConstructorOrMethod()).thenReturn(new org.testng.internal.ConstructorOrMethod(dummyMethod()));
        when(testClass.getRealClass()).thenReturn((Class) DummyTestClass.class);
        when(method.getGroups()).thenReturn(new String[]{"smoke", "regression"});

        listener.onTestStart(result);

        TestTiming timing = ExecutionMetrics.getTiming("com.example.TestNgSuite.loginTest");
        assertNotNull(timing);
        assertEquals(timing.getTags(), List.of("smoke", "regression"));
    }

    @Test
    public void testNg_onTestSkipped_capturesGroups() {
        TestExecutionListener listener = new TestExecutionListener();
        ITestResult result = Mockito.mock(ITestResult.class);
        ITestNGMethod method = Mockito.mock(ITestNGMethod.class);
        IClass testClass = Mockito.mock(IClass.class);

        when(result.getMethod()).thenReturn(method);
        when(result.getTestClass()).thenReturn(testClass);
        when(testClass.getRealClass()).thenReturn((Class) DummyTestClass.class);
        when(method.getQualifiedName()).thenReturn("com.example.TestNgSuite.skippedTest");
        when(method.getMethodName()).thenReturn("skippedTest");
        when(method.getConstructorOrMethod()).thenReturn(new org.testng.internal.ConstructorOrMethod(dummyMethod()));
        when(method.getGroups()).thenReturn(new String[]{"nightly"});

        listener.onTestSkipped(result);

        TestTiming timing = ExecutionMetrics.getTiming("com.example.TestNgSuite.skippedTest");
        assertNotNull(timing);
        assertEquals(timing.getStatus(), "SKIPPED");
        assertEquals(timing.getTags(), List.of("nightly"));
    }

    @Test
    public void cucumber_beforeScenario_capturesAndNormalizesTags() {
        CucumberHooks hooks = new CucumberHooks();
        Scenario scenario = Mockito.mock(Scenario.class);

        when(scenario.getUri()).thenReturn(URI.create("classpath:features/checkout.feature"));
        when(scenario.getName()).thenReturn("Order item");
        when(scenario.getLine()).thenReturn(15);
        when(scenario.getSourceTagNames()).thenReturn(Arrays.asList("@smoke", "@checkout", "fast"));

        try (MockedStatic<DriverManager> mocked = Mockito.mockStatic(DriverManager.class)) {
            hooks.beforeScenario(scenario);
        } catch (Exception ignored) {
        }

        String testId = "checkout.feature#Order item[L15]";
        TestTiming timing = ExecutionMetrics.getTiming(testId);
        assertNotNull(timing);
        assertEquals(timing.getTags(), List.of("smoke", "checkout", "fast"));
    }

    @Test
    public void junit5_beforeEach_capturesTags() throws Exception {
        SeleniumBootExtension extension = new SeleniumBootExtension();
        ExtensionContext context = Mockito.mock(ExtensionContext.class);

        when(context.getRequiredTestClass()).thenReturn((Class) DummyTestClass.class);
        when(context.getRequiredTestMethod()).thenReturn(dummyMethod());
        when(context.getDisplayName()).thenReturn("dummyMethod()");
        when(context.getTags()).thenReturn(Set.of("junit5-tag", "@annotated-tag"));

        try (MockedStatic<DriverManager> mocked = Mockito.mockStatic(DriverManager.class)) {
            extension.beforeEach(context);
        } catch (Exception ignored) {}

        String testId = DummyTestClass.class.getName() + "#" + dummyMethod().getName();
        TestTiming timing = ExecutionMetrics.getTiming(testId);
        assertNotNull(timing);
        assertTrue(timing.getTags().contains("junit5-tag"));
        assertTrue(timing.getTags().contains("annotated-tag"));
    }

    @Test
    public void endToEnd_tagsRecordedAndExportedToJson() throws Exception {
        ExecutionMetrics.markStart("test1");
        ExecutionMetrics.recordTags("test1", List.of("@smoke", "api"));
        ExecutionMetrics.recordStatus("test1", "PASSED");
        ExecutionMetrics.markEnd("test1");

        ExecutionMetrics.markStart("test2");
        ExecutionMetrics.recordTags("test2", List.of("regression"));
        ExecutionMetrics.recordStatus("test2", "FAILED");
        ExecutionMetrics.markEnd("test2");

        ExecutionMetrics.exportToJson();

        JsonNode root = new ObjectMapper().readTree(new File("target/selenium-boot-metrics.json"));
        JsonNode tests = root.path("tests");
        assertEquals(tests.size(), 2);

        JsonNode t1Node = null;
        JsonNode t2Node = null;
        for (JsonNode t : tests) {
            if ("test1".equals(t.path("testId").asText())) t1Node = t;
            if ("test2".equals(t.path("testId").asText())) t2Node = t;
        }

        assertNotNull(t1Node);
        assertNotNull(t2Node);

        JsonNode t1Tags = t1Node.path("tags");
        assertEquals(t1Tags.size(), 2);
        assertEquals(t1Tags.get(0).asText(), "smoke");
        assertEquals(t1Tags.get(1).asText(), "api");

        JsonNode t2Tags = t2Node.path("tags");
        assertEquals(t2Tags.size(), 1);
        assertEquals(t2Tags.get(0).asText(), "regression");
    }

    private static java.lang.reflect.Method dummyMethod() {
        try {
            return DummyTestClass.class.getDeclaredMethod("dummyMethod");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @com.seleniumboot.test.NoBrowser
    static class DummyTestClass {
        @com.seleniumboot.test.NoBrowser
        void dummyMethod() {}
    }
}

