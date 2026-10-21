---
description: "Failure Intelligence in Selenium Boot: know why a test failed and what to do, with zero setup and no API key."
id: failure-intelligence
title: Failure Intelligence
sidebar_position: 12
---

# Failure Intelligence

**Failure Intelligence** takes you from *"a test failed"* to *"I know why and what to do"*, working on the first run with zero configuration and no API key.

---

## Why Failure Intelligence?

When automated test suites grow, diagnosing failures quickly becomes the biggest bottleneck:
- **Alert fatigue**: 40 tests failing due to a single backend outage read as 40 independent bugs.
- **Ambiguity**: Error messages like `TimeoutException` or `NoSuchElementException` don't distinguish between an application defect, a broken locator, or an environment timeout.
- **Trial-and-error triage**: Engineers often have to reproduce the failure locally or search through massive stack traces to figure out what to fix.
- **AI lock-in**: Most triage tools require commercial LLM API keys and send test code or stack traces to external servers.

Selenium Boot's Failure Intelligence solves this by diagnosing failures **locally**, using rule-based classification, history awareness, and root-cause grouping.

---

## 1. High-level triage classification (`FailureType`)

Every failed test is automatically classified into a high-level triage type:

| Classification | Meaning | Typical Trigger |
|---|---|---|
| **`Product bug`** | The application behavior diverged from expectations (potential regression). | Assertion failure (`AssertionError`, `ComparisonFailure`), especially when newly failing. |
| **`Test bug`** | An issue in test code, selectors, or test sequencing. | `LocatorException`, `NoSuchElementException`, `ElementNotInteractableException`, `StaleElementReferenceException`. |
| **`Environment`** | Infrastructure, network, browser, or backend server outage. | `NoSuchSessionException`, `UnreachableBrowserException`, `net::ERR_*`, DNS failures, HTTP 5xx errors. |
| **`Flaky`** | Non-deterministic test outcome due to timing or shared state. | Passed on retry, or marked `INTERMITTENT` based on run history. |
| **`Unknown`** | Unrecognised or unexpected exception, or generic timeout. Raw stack trace is preserved. | `TimeoutException` with no locator or navigation cause, or any unmapped exception. The classifier never guesses. |

Shown as a color-coded badge (`[Product bug]`, `[Test bug]`, `[Environment]`, `[Flaky]`) in the HTML report and exported as `failureType` in `selenium-boot-metrics.json`.

---

## 2. Granular category and evidence (`FailureCategory`)

Below the high-level classification, each failure is tagged with a fine-grained category and the exact evidence extracted from the exception cause chain:

| Category | Triggered by |
|---|---|
| `LOCATOR_NOT_FOUND` | `LocatorException`, `NoSuchElementException`, `NoSuchFrameException`, `NoSuchShadowRootException` anywhere in the cause chain. |
| `ELEMENT_NOT_INTERACTABLE` | `ElementNotInteractableException`, `ElementClickInterceptedException`, `InvalidElementStateException`. |
| `STALE_ELEMENT` | `StaleElementReferenceException` anywhere in the cause chain. |
| `TIMEOUT` | A `TimeoutException` with no locator or navigation cause. |
| `ASSERTION_MISMATCH` | Outermost error is an `AssertionError` (or TestNG/JUnit/`DbAssertException` equivalent), or soft assertions failed. |
| `NAVIGATION_ERROR` | `net::ERR_*`, DNS failures, renderer timeouts, page load timeouts, or HTTP 5xx status codes in the message. |
| `BROWSER_CRASH` | `NoSuchSessionException`, `UnreachableBrowserException`, "invalid session id", or browser process crash. |
| `UNKNOWN` | None of the above. |

---

## 3. Actionable remediation ("What to do", No API Key)

Failure Intelligence does not just report *why* a test failed — it provides prescriptive, context-aware remediation guidance:

- **Locator not found**: Names the exact failing locator when available and recommends checking if the selector changed, or if the element is inside an iframe, shadow DOM, or delayed by async loading.
- **Element not interactable**: Identifies whether the element is covered by an overlay/modal, disabled, or outside the viewport, recommending scroll or modal dismissal.
- **Stale element**: Recommends using dynamic `$(...)` locators which auto-wait and re-resolve instead of holding cached `WebElement` references.
- **Assertion mismatch**: Highlights expected vs. actual values. When newly failing, it advises verifying whether the application regressed or if requirements changed and test expectations need updating.
- **Navigation error (HTTP 5xx)**: Instructs checking backend service health, database connectivity, and `execution.baseUrl`.
- **Browser crash**: Suggests reviewing driver binary compatibility, memory limits, and container resource limits.
- **Flaky**: Preserves the underlying failure category (e.g. `Flaky (Locator not found)`) so you know what caused the flake, and recommends replacing arbitrary thread sleeps with explicit `Locator` waits and isolating test state.

The remediation guidance appears in an **Action** callout box in the HTML report and is exported as `suggestedAction` in metrics.

---

## 4. Root cause grouping ("N tests, same cause")

When multiple tests fail for the same reason (for example, a shared login locator change or a backend service outage), Failure Intelligence groups them by a signature:
- Failure category
- Outermost exception class
- Normalised error message (UUIDs, numbers, timestamps, and quoted strings replaced with placeholders)
- Top application stack frame (page object or test method)

The HTML report's **Failures** tab opens with a *"Same cause (N tests)"* section showing the largest failure groups first. One outage no longer reads as forty disconnected failures.

---

## 5. Retry and history status (`TestStatus`)

Failures are compared against the test's recent history in `target/metrics-history/` (or `-Dseleniumboot.history.dir`):

- **Newly failing**: Passed in all earlier runs in the window, but failed in this run. These are sorted to the top of the Failures tab as the most likely regressions.
- **Consistently failing**: Failed in this run and in earlier runs without passing.
- **Intermittent**: Alternates between passes and failures across recent runs.
- **Passed on retry**: Failed on initial attempt, but recovered upon retry.

---

## 6. Optional AI enhancement (`ai.failureAnalysis`)

When you configure an LLM API key (`ai.apiKey`), Selenium Boot passes the structured Failure Detail record (including rule-based classification, failing locator, console errors, self-healing events, and same-cause group context) to the model.

Crucially:
- **Tests in the same-cause group share one AI call**, so you make far fewer AI calls.
- **AI output adheres to the framework's classification taxonomy**:
  ```markdown
  **Classification:** (Product Bug | Test Bug | Environment | Flaky)
  **Root Cause:** (1-2 sentences explaining what likely went wrong)
  **Suggested Fix:**
  - (bullet 1)
  - (bullet 2, if needed)
  ```
- **Privacy by default**: If no API key is provided, zero data leaves your machine. Classification and remediation work 100% locally.

