package com.seleniumboot.failure;

/**
 * High-level triage classification for a failed test.
 *
 * <p>Answers whether the failure represents a defect in the application under test,
 * an issue in test code or locators, an infrastructure/environment outage, or flakiness.
 */
public enum FailureType {
    PRODUCT_BUG("Product bug"),
    TEST_BUG("Test bug"),
    ENVIRONMENT("Environment"),
    FLAKY("Flaky"),
    UNKNOWN("Unknown");

    private final String label;

    FailureType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String displayName() {
        return label;
    }
}
