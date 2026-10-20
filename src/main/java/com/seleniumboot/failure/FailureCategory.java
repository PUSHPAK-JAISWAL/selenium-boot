package com.seleniumboot.failure;

/** Why a test failed, as decided by {@link FailureClassifier}. */
public enum FailureCategory {
    LOCATOR_NOT_FOUND("Locator not found"),
    ELEMENT_NOT_INTERACTABLE("Element not interactable"),
    STALE_ELEMENT("Stale element"),
    TIMEOUT("Timeout"),
    ASSERTION_MISMATCH("Assertion mismatch"),
    NAVIGATION_ERROR("Navigation error"),
    BROWSER_CRASH("Browser crash"),
    UNKNOWN("Unknown");

    private final String label;

    FailureCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The default high-level {@link FailureType} associated with this category. */
    public FailureType defaultType() {
        return switch (this) {
            case ASSERTION_MISMATCH -> FailureType.PRODUCT_BUG;
            case LOCATOR_NOT_FOUND, ELEMENT_NOT_INTERACTABLE, STALE_ELEMENT -> FailureType.TEST_BUG;
            case NAVIGATION_ERROR, BROWSER_CRASH -> FailureType.ENVIRONMENT;
            case TIMEOUT, UNKNOWN -> FailureType.UNKNOWN;
        };
    }

    /** Actionable remediation advice for this failure category without an API key. */
    public String defaultAction() {
        return switch (this) {
            case LOCATOR_NOT_FOUND ->
                "Check if the element selector has changed in the application, or if it renders inside an iframe, shadow DOM, or after an asynchronous animation.";
            case ELEMENT_NOT_INTERACTABLE ->
                "Element is present in the DOM but not clickable or visible. Check if obscured by an overlay/modal, disabled, or needs scrolling into view.";
            case STALE_ELEMENT ->
                "Element detached from DOM after retrieval. Use dynamic Locator actions $(...) which auto-wait and re-resolve instead of caching WebElement references.";
            case TIMEOUT ->
                "Operation timed out waiting for condition. Check if page response or network is unusually slow, increase timeouts.explicit if expected, or adjust wait condition.";
            case ASSERTION_MISMATCH ->
                "Assertion failed. Compare expected vs actual values; check if product functionality changed or test expectation is outdated.";
            case NAVIGATION_ERROR ->
                "Page navigation or network request failed. Verify target URL, server status (HTTP 5xx), DNS resolution, and execution.baseUrl configuration.";
            case BROWSER_CRASH ->
                "Browser process or driver session closed unexpectedly. Check driver binary compatibility, available container memory, and headless mode options.";
            case UNKNOWN ->
                "Unclassified failure. Review the root exception and application stack trace below for details.";
        };
    }
}
