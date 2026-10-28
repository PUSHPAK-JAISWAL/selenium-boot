---
description: "Why accessibility-first locators: targeting the accessibility tree (getByRole, getByLabel, getByText) survives CSS rewrites and markup refactors without hiding Selenium."
id: why-accessibility-first-locators
title: Why accessibility-first locators?
sidebar_label: Why accessibility-first locators?
sidebar_position: 4
---

# Why accessibility-first locators?

Automated tests break for two reasons: real regressions, and brittle selectors. The second kind is wasted time.

Most Selenium test suites are packed with CSS selectors and XPath expressions tied directly to DOM hierarchies or styling hooks:

```java
// Brittle: bound to exact tag hierarchy and volatile utility classes
driver.findElement(By.cssSelector("form.auth-form div.input-group > input.email-input"))
      .sendKeys("user@example.com");
driver.findElement(By.cssSelector("form.auth-form div.actions button.btn-primary"))
      .click();
```

When frontend developers restructure a component, migrate CSS frameworks (e.g. from Bootstrap to Tailwind), or wrap a field in an extra `<div>`, the application still works perfectly for end users. But the test suite turns red.

---

## What accessibility-first locators do

Users and assistive technologies (like screen readers) do not interact with CSS class names or DOM parent-child nesting. They interact with **accessible roles** and **names**:

- A button labeled **"Sign In"**
- A text input labeled **"Email address"**
- A heading at **Level 1** reading **"Dashboard"**

Selenium Boot introduces accessibility-first locators (`getByRole`, `getByLabel`, `getByText`, `getByPlaceholder`, `getByTestId`) available directly in `BaseTest` and `BasePage`:

```java
// Resilient: targets what the user sees and perceives
getByLabel("Email address").type("user@example.com");
getByRole(Role.BUTTON).withName("Sign In").click();
```

---

## Surviving real markup refactors

Consider a button that goes through common frontend iterations across three sprints:

```html
<!-- Sprint 1: Native button styled with CSS class -->
<button class="btn btn-primary">Sign In</button>

<!-- Sprint 2: Replaced with styled anchor for design system requirements -->
<a href="#" role="button" class="inline-flex items-center px-4 py-2 bg-blue-600 rounded">Sign In</a>

<!-- Sprint 3: Form converted to standard HTML input -->
<input type="submit" value="Sign In" class="cta-submit" />
```

Here is how different locator strategies hold up:

| Locator Strategy | Sprint 1 | Sprint 2 | Sprint 3 | Why |
|---|---|---|---|---|
| `By.cssSelector("button.btn-primary")` | Pass | **Fail** | **Fail** | Tag changed to `<a>`, class changed |
| `By.xpath("//button[text()='Sign In']")` | Pass | **Fail** | **Fail** | `button` tag is gone |
| `getByRole(Role.BUTTON).withName("Sign In")` | **Pass** | **Pass** | **Pass** | All 3 expose the accessible role `button` and name `"Sign In"` |

`Role.BUTTON` targets both native HTML elements with implicit roles (`<button>`, `<input type="submit">`, `<summary>`) and any element with an explicit `role="button"`. The accessible name calculation follows standard W3C ARIA precedence (`aria-label` → `aria-labelledby` → `<label>` → text content → `value`).

---

## Before & after

### Form filling

**Plain Selenium (brittle selectors + explicit wait boilerplate):**

```java
// Fragile against label ID changes or container restructuring
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
WebElement emailInput = wait.until(
    ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[@id='user-fields']//input[1]"))
);
emailInput.sendKeys("alex@example.com");

WebElement submitBtn = wait.until(
    ExpectedConditions.elementToBeClickable(By.cssSelector("div.dialog-actions > button.submit-btn"))
);
submitBtn.click();
```

**Selenium Boot (accessibility-first + auto-waiting):**

```java
// Reads like user intent, survives markup restructuring
getByLabel("Email address").type("alex@example.com");
getByRole(Role.BUTTON).withName("Submit").click();
```

---

## …without hiding Selenium

Accessibility-first locators do not hide `WebDriver` behind a restrictive proprietary abstraction:

1. **Transparent Selenium By**: Every semantic locator can produce a standard Selenium `By` instance using `.toBy()`:
   ```java
   By buttonBy = Locator.byRole(Role.BUTTON).withName("Submit").toBy();
   WebElement rawElement = getDriver().findElement(buttonBy); // or driver.findElement(...) in BasePage
   ```
2. **Mix and match**: Use `getByRole` for UI actions and drop down to `getDriver().findElement(By.id("canvas"))` whenever you need low-level WebDriver control.
3. **Chainable auto-waiting**: Locators return `Locator` objects that poll up to `timeouts.explicit` and automatically re-resolve stale DOM elements, eliminating `StaleElementReferenceException` without extra code.

---

## Locator comparison summary

| Feature | CSS / XPath | Accessibility-First (`getBy*`) |
|---|---|---|
| Target | DOM structure / CSS classes | Accessibility tree (Role + Name) |
| Survives CSS refactoring | No | **Yes** |
| Survives tag change (`<button>` to `<a role="button">`) | No | **Yes** |
| Auto-waiting on action (`click()`, `type()`) | No | **Yes** |
| Re-resolves stale DOM references | No | **Yes** |
| Escape hatch to raw `By` | Native | `.toBy()` |

---

## Next steps

- [Accessibility-First Locators Guide](/docs/guides/semantic-locators) — complete `getByRole`, `getByLabel`, `getByText` reference
- [Smart Locator](/docs/guides/smart-locator) — the chainable `$()` API
- [Why WaitEngine?](/docs/why/why-wait-engine) — centralized waiting vs scattered `WebDriverWait`
- [Why Selenium Boot?](/docs/why/why-selenium-boot) — the overall philosophy

