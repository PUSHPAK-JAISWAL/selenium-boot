package com.seleniumboot.failure;

import com.seleniumboot.flakiness.TestStatus;
import com.seleniumboot.metrics.TestTiming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tags a failed test with a {@link FailureCategory}, high-level {@link FailureType}, evidence,
 * and actionable remediation advice, using only the error message, stack trace, and test metrics
 * the framework already records. No AI, no network.
 *
 * <p>The exception class chain is read from the printed stack trace (the outer throwable on the
 * first line, each cause on a {@code Caused by:} line), so a {@code LocatorException} whose cause
 * is a {@code TimeoutException} is a locator problem, while a bare {@code TimeoutException} is not.
 * Anything the rules do not recognise is {@link FailureCategory#UNKNOWN}; the classifier never guesses.
 */
public final class FailureClassifier {

    /** Category, high-level triage type, the one-line reason, and actionable remediation advice. */
    public record Result(FailureCategory category, FailureType type, String evidence, String suggestedAction) {
        public Result(FailureCategory category, String evidence) {
            this(category, category != null ? category.defaultType() : FailureType.UNKNOWN,
                    evidence, category != null ? category.defaultAction() : "");
        }

        public Result(FailureCategory category, FailureType type, String evidence) {
            this(category, type, evidence, category != null ? category.defaultAction() : "");
        }
    }

    private static final Pattern THROWABLE_LINE = Pattern.compile(
            "^(?:Caused by: )?((?:[A-Za-z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*|[A-Za-z_$][\\w$]*(?:Exception|Error))"
            + "(?::\\s?(.*))?$");

    private static final int EVIDENCE_MAX = 140;

    private FailureClassifier() { }

    public static Result classify(TestTiming timing) {
        return classify(timing, null);
    }

    public static Result classify(TestTiming timing, TestStatus status) {
        if (timing == null) return unknown("no failure data");
        Result base = classify(timing.getErrorMessage(), timing.getStackTrace(), status);
        if (base.category() == FailureCategory.LOCATOR_NOT_FOUND) {
            String locator = timing.getFailingLocator();
            if (locator != null && !locator.isBlank()) {
                String enrichedAction = "Check if locator '" + locator
                        + "' has changed or if the element is rendered in an iframe/shadow DOM or delayed by async loading.";
                return new Result(base.category(), base.type(), base.evidence(), enrichedAction);
            }
        }
        return base;
    }

    public static Result classify(String errorMessage, String stackTrace) {
        return classify(errorMessage, stackTrace, null);
    }

    public static Result classify(String errorMessage, String stackTrace, TestStatus status) {
        List<String> chain = causeChain(stackTrace);
        String outer = chain.isEmpty() ? null : chain.get(0);
        String haystack = ((errorMessage != null ? errorMessage : "") + "\n"
                + (stackTrace != null ? stackTrace : "")).toLowerCase(Locale.ROOT);

        if (chain.isEmpty() && (errorMessage == null || errorMessage.isBlank())) {
            return unknown("no error message or stack trace recorded");
        }

        FailureCategory category = null;
        String evidence = null;

        // A failed assertion is the test doing its job, whatever text its message happens to contain.
        if (outer != null && isAssertion(outer)) {
            category = FailureCategory.ASSERTION_MISMATCH;
            evidence = "assertion failed (" + simple(outer) + ")";
        } else {
            String hit = firstMatch(chain, "NoSuchSessionException", "UnreachableBrowserException",
                    "SessionNotCreatedException");
            if (hit != null) {
                category = FailureCategory.BROWSER_CRASH;
                evidence = simple(hit) + " in cause chain";
            } else {
                String crash = containsAny(haystack, "invalid session id", "session deleted because of page crash",
                        "chrome not reachable", "not connected to devtools", "target frame detached",
                        "browser has closed the connection", "tab crashed");
                if (crash != null) {
                    category = FailureCategory.BROWSER_CRASH;
                    evidence = "message contains \"" + crash + "\"";
                } else {
                    String nav = containsAny(haystack, "net::err_", "err_name_not_resolved", "dnsnotfound",
                            "reached error page", "unknownhostexception", "timed out receiving message from renderer",
                            "page load timeout", "pageloadstrategy");
                    if (nav != null) {
                        category = FailureCategory.NAVIGATION_ERROR;
                        evidence = "message contains \"" + nav + "\"";
                    } else {
                        Matcher http = Pattern.compile("(?:http|status)[ :=]+(5\\d\\d)\\b").matcher(haystack);
                        if (http.find()) {
                            category = FailureCategory.NAVIGATION_ERROR;
                            evidence = "HTTP " + http.group(1) + " in message";
                        } else {
                            hit = firstMatch(chain, "StaleElementReferenceException");
                            if (hit != null) {
                                category = FailureCategory.STALE_ELEMENT;
                                evidence = simple(hit) + " in cause chain";
                            } else {
                                hit = firstMatch(chain, "ElementNotInteractableException", "ElementClickInterceptedException",
                                        "InvalidElementStateException", "ElementNotSelectableException");
                                if (hit != null) {
                                    category = FailureCategory.ELEMENT_NOT_INTERACTABLE;
                                    evidence = simple(hit) + " in cause chain";
                                } else {
                                    hit = firstMatch(chain, "LocatorException", "NoSuchElementException", "NoSuchFrameException",
                                            "NoSuchShadowRootException");
                                    if (hit != null) {
                                        category = FailureCategory.LOCATOR_NOT_FOUND;
                                        evidence = simple(hit) + " in cause chain";
                                    } else {
                                        hit = firstMatch(chain, "TimeoutException");
                                        if (hit != null) {
                                            category = FailureCategory.TIMEOUT;
                                            evidence = simple(hit) + " with no locator or navigation cause";
                                        } else {
                                            for (String c : chain) {
                                                if (isAssertion(c)) {
                                                    category = FailureCategory.ASSERTION_MISMATCH;
                                                    evidence = simple(c) + " in cause chain";
                                                    break;
                                                }
                                            }
                                            if (category == null && haystack.contains("the following asserts failed")) {
                                                category = FailureCategory.ASSERTION_MISMATCH;
                                                evidence = "soft assertions failed";
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (category == null) {
            return unknown(outer != null ? "unrecognised " + simple(outer) : "unrecognised error");
        }

        FailureType type = category.defaultType();
        String action = category.defaultAction();

        if (status == TestStatus.PASSED_ON_RETRY || status == TestStatus.INTERMITTENT) {
            type = FailureType.FLAKY;
            action = "Test is non-deterministic (" + status.label().toLowerCase()
                    + "). Replace arbitrary sleeps with explicit Locator waits and ensure tests do not share state.";
        } else if (status == TestStatus.NEWLY_FAILING && category == FailureCategory.ASSERTION_MISMATCH) {
            type = FailureType.PRODUCT_BUG;
            action = "Newly failing assertion (passed in previous runs). Check recent application changes or deployments for a regression.";
        } else if (category == FailureCategory.NAVIGATION_ERROR && evidence != null && evidence.startsWith("HTTP 5")) {
            action = "Server returned an HTTP 5xx error. Inspect backend service logs and verify application server health.";
        }

        return result(category, type, evidence, action);
    }

    static List<String> causeChainOf(String stackTrace) {
        return causeChain(stackTrace);
    }

    /** Fully-qualified class names of the outer throwable followed by each cause, in order. */
    static List<String> causeChain(String stackTrace) {
        List<String> chain = new ArrayList<>();
        if (stackTrace == null) return chain;
        boolean first = true;
        for (String line : stackTrace.split("\\R")) {
            if (line.isEmpty() || Character.isWhitespace(line.charAt(0))) continue;
            Matcher m = THROWABLE_LINE.matcher(line);
            boolean cause = line.startsWith("Caused by: ");
            if (first) {
                first = false;
                if (m.matches()) chain.add(m.group(1));
                else return chain;
            } else if (cause && m.matches()) {
                chain.add(m.group(1));
            }
        }
        return chain;
    }

    private static boolean isAssertion(String className) {
        String s = simple(className);
        return s.equals("AssertionError") || s.equals("AssertionFailedError")
                || s.equals("ComparisonFailure") || s.equals("DbAssertException")
                || s.equals("MultipleFailuresError");
    }

    private static String firstMatch(List<String> chain, String... simpleNames) {
        for (String c : chain) {
            for (String n : simpleNames) {
                if (simple(c).equals(n)) return c;
            }
        }
        return null;
    }

    private static String containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return n;
        }
        return null;
    }

    private static String simple(String className) {
        int i = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return i >= 0 ? className.substring(i + 1) : className;
    }

    private static Result result(FailureCategory c, FailureType type, String evidence, String action) {
        String boundedEvidence = evidence.length() > EVIDENCE_MAX ? evidence.substring(0, EVIDENCE_MAX) + "…" : evidence;
        return new Result(c, type, boundedEvidence, action);
    }

    private static Result unknown(String evidence) {
        return new Result(FailureCategory.UNKNOWN, FailureType.UNKNOWN, evidence, FailureCategory.UNKNOWN.defaultAction());
    }
}
