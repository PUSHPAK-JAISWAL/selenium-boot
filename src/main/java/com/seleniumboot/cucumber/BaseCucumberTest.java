package com.seleniumboot.cucumber;

import com.seleniumboot.api.SeleniumBootApi;
import com.seleniumboot.listeners.SuiteExecutionListener;
import com.seleniumboot.listeners.TestExecutionListener;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import org.testng.annotations.Listeners;

/**
 * Base class for Cucumber runner classes in Selenium Boot.
 *
 * <p>Annotate your runner with {@code @CucumberOptions} and extend this class:
 * <pre>
 * {@literal @}CucumberOptions(
 *     features = "src/test/resources/features",
 *     glue     = {"com.myapp.steps", "com.seleniumboot.cucumber"},
 *     plugin   = {"pretty", "com.seleniumboot.cucumber.CucumberStepLogger"}
 * )
 * public class CucumberRunner extends BaseCucumberTest {}
 * </pre>
 *
 * <p>The framework lifecycle is fully automatic:
 * <ul>
 *   <li>Driver created and destroyed per scenario by {@link CucumberHooks}.</li>
 *   <li>Metrics, screenshots, step timeline, and HTML report handled by framework listeners.</li>
 *   <li>All {@code selenium-boot.yml} settings (browser, timeouts, retry, etc.) apply.</li>
 * </ul>
 *
 * <p>Step definition classes should extend {@link BaseCucumberSteps} to get
 * {@code getDriver()}, {@code open()}, {@code $()} and {@code assertThat()}.
 *
 * @deprecated since 3.9.0. {@link AbstractTestNGCucumberTests} has been deprecated for
 *     removal upstream in Cucumber 8.x. New suites should use JUnit Platform Engine
 *     ({@code cucumber-junit-platform-engine}) with {@code @Suite}. This class will be
 *     removed in 4.0.0 once Cucumber drops TestNG runner support.
 */
@Deprecated(since = "3.9.0")
@SuppressWarnings({"deprecation", "removal"})
@SeleniumBootApi(since = "1.9.0")
@Listeners({SuiteExecutionListener.class, TestExecutionListener.class})
public abstract class BaseCucumberTest extends AbstractTestNGCucumberTests {
    // Lifecycle is handled by CucumberHooks (driver) and SuiteExecutionListener (reports).
}
