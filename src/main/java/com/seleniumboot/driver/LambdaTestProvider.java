package com.seleniumboot.driver;

import com.seleniumboot.config.SeleniumBootConfig;
import com.seleniumboot.internal.SeleniumBootContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.net.URL;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates a RemoteWebDriver session against LambdaTest.
 */
public class LambdaTestProvider implements DriverProvider {

    public static final String HUB_URL
            = "https://hub.lambdatest.com/wd/hub";

    public static final String SESSION_URL_PREFIX
            = "https://automation.lambdatest.com/logs/?sessionID=";

    @Override
    public WebDriver createDriver() {
        SeleniumBootConfig cfg = SeleniumBootContext.getConfig();
        SeleniumBootConfig.Execution.LambdaTest lt
                = cfg.getExecution().getLambdatest();

        String username = BrowserStackProvider.resolveEnv(lt.getUsername());
        String accessKey = BrowserStackProvider.resolveEnv(lt.getAccessKey());
        String browser = lt.getBrowser() != null
                ? lt.getBrowser() : "chrome";
        String testId = SeleniumBootContext.getCurrentTestId();

        if (isBlank(username)) {
            throw new IllegalStateException(
                    "[LambdaTest] execution.lambdatest.username is required");
        }

        if (isBlank(accessKey)) {
            throw new IllegalStateException(
                    "[LambdaTest] execution.lambdatest.accessKey is required");
        }

        Map<String, Object> ltOptions = new LinkedHashMap<>();
        ltOptions.put("username", username);
        ltOptions.put("accessKey", accessKey);

        if (!isBlank(testId)) {
            ltOptions.put("name", testId);
        }

        // User-provided LT:Options values override defaults.
        if (lt.getCapabilities() != null) {
            ltOptions.putAll(lt.getCapabilities());
        }

        AbstractDriverOptions<?> options = buildOptions(
                browser,
                lt.getBrowserVersion(),
                lt.getPlatform(),
                ltOptions
        );

        try {
            WebDriver driver
                    = new RemoteWebDriver(new URL(HUB_URL), options);

            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            driver.manage().timeouts().pageLoadTimeout(
                    Duration.ofSeconds(cfg.getTimeouts().getPageLoad()));

            return driver;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "[LambdaTest] Failed to create session: "
                    + e.getMessage(), e);
        }
    }

    private static AbstractDriverOptions<?> buildOptions(
            String browser,
            String browserVersion,
            String platform,
            Map<String, Object> ltOptions) {

        AbstractDriverOptions<?> options;

        switch (browser.toLowerCase()) {
            case "firefox":
                options = new FirefoxOptions();
                break;
            case "edge":
                options = new EdgeOptions();
                break;
            case "safari":
                options = new SafariOptions();
                break;
            default:
                options = new ChromeOptions();
                break;
        }

        if (!isBlank(browserVersion)) {
            options.setBrowserVersion(browserVersion);
        }

        if (!isBlank(platform)) {
            options.setPlatformName(platform);
        }

        options.setCapability("LT:Options", ltOptions);
        return options;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
