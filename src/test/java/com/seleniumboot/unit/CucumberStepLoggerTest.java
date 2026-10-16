package com.seleniumboot.unit;

import com.seleniumboot.cucumber.CucumberStepLogger;
import com.seleniumboot.steps.StepLogger;
import com.seleniumboot.steps.StepStatus;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.Step;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.TestStepStarted;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Unit tests for {@link CucumberStepLogger}.
 */
public class CucumberStepLoggerTest {

    private MockedStatic<StepLogger> stepLoggerMock;

    @BeforeMethod
    public void setup() {
        stepLoggerMock = mockStatic(StepLogger.class);
    }

    @AfterMethod
    public void teardown() {
        stepLoggerMock.close();
    }

    @Test
    public void registersHandlersAndLogsSuccessfulStep() {
        CucumberStepLogger cucumberStepLogger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);

        ArgumentCaptor<EventHandler> handlerCaptor = ArgumentCaptor.forClass(EventHandler.class);
        cucumberStepLogger.setEventPublisher(publisher);

        verify(publisher).registerHandlerFor(eq(TestStepStarted.class), handlerCaptor.capture());
        verify(publisher).registerHandlerFor(eq(TestStepFinished.class), handlerCaptor.capture());

        EventHandler<TestStepStarted> startHandler = handlerCaptor.getAllValues().get(0);
        EventHandler<TestStepFinished> finishHandler = handlerCaptor.getAllValues().get(1);

        // Given a pickle step
        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn("Given user navigates to login");
        when(pickleStep.getStep()).thenReturn(step);

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(pickleStep);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(pickleStep);
        Result result = mock(Result.class);
        when(result.getStatus()).thenReturn(Status.PASSED);
        when(finishEvent.getResult()).thenReturn(result);

        // When step starts and finishes
        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        // Then logged to StepLogger with PASS and no screenshot
        stepLoggerMock.verify(() -> StepLogger.step("Given user navigates to login", StepStatus.PASS, false), times(1));
    }

    @Test
    public void logsFailedStepWithScreenshot() {
        CucumberStepLogger cucumberStepLogger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);

        ArgumentCaptor<EventHandler> handlerCaptor = ArgumentCaptor.forClass(EventHandler.class);
        cucumberStepLogger.setEventPublisher(publisher);

        verify(publisher).registerHandlerFor(eq(TestStepStarted.class), handlerCaptor.capture());
        verify(publisher).registerHandlerFor(eq(TestStepFinished.class), handlerCaptor.capture());

        EventHandler<TestStepStarted> startHandler = handlerCaptor.getAllValues().get(0);
        EventHandler<TestStepFinished> finishHandler = handlerCaptor.getAllValues().get(1);

        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn("When user enters invalid password");
        when(pickleStep.getStep()).thenReturn(step);

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(pickleStep);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(pickleStep);
        Result result = mock(Result.class);
        when(result.getStatus()).thenReturn(Status.FAILED);
        when(finishEvent.getResult()).thenReturn(result);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        // Failure must trigger screenshot = true
        stepLoggerMock.verify(() -> StepLogger.step("When user enters invalid password", StepStatus.FAIL, true), times(1));
    }

    @Test
    public void mapsSkippedStatusToWarn() {
        CucumberStepLogger cucumberStepLogger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);

        ArgumentCaptor<EventHandler> handlerCaptor = ArgumentCaptor.forClass(EventHandler.class);
        cucumberStepLogger.setEventPublisher(publisher);

        verify(publisher).registerHandlerFor(eq(TestStepStarted.class), handlerCaptor.capture());
        verify(publisher).registerHandlerFor(eq(TestStepFinished.class), handlerCaptor.capture());

        EventHandler<TestStepStarted> startHandler = handlerCaptor.getAllValues().get(0);
        EventHandler<TestStepFinished> finishHandler = handlerCaptor.getAllValues().get(1);

        PickleStepTestStep pickleStep = mock(PickleStepTestStep.class);
        Step step = mock(Step.class);
        when(step.getText()).thenReturn("Then dashboard should display");
        when(pickleStep.getStep()).thenReturn(step);

        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(pickleStep);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(pickleStep);
        Result result = mock(Result.class);
        when(result.getStatus()).thenReturn(Status.SKIPPED);
        when(finishEvent.getResult()).thenReturn(result);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        stepLoggerMock.verify(() -> StepLogger.step("Then dashboard should display", StepStatus.WARN, false), times(1));
    }

    @Test
    public void ignoresHookTestSteps() {
        CucumberStepLogger cucumberStepLogger = new CucumberStepLogger();
        EventPublisher publisher = mock(EventPublisher.class);

        ArgumentCaptor<EventHandler> handlerCaptor = ArgumentCaptor.forClass(EventHandler.class);
        cucumberStepLogger.setEventPublisher(publisher);

        verify(publisher).registerHandlerFor(eq(TestStepStarted.class), handlerCaptor.capture());
        verify(publisher).registerHandlerFor(eq(TestStepFinished.class), handlerCaptor.capture());

        EventHandler<TestStepStarted> startHandler = handlerCaptor.getAllValues().get(0);
        EventHandler<TestStepFinished> finishHandler = handlerCaptor.getAllValues().get(1);

        HookTestStep hookStep = mock(HookTestStep.class);
        TestStepStarted startEvent = mock(TestStepStarted.class);
        when(startEvent.getTestStep()).thenReturn(hookStep);

        TestStepFinished finishEvent = mock(TestStepFinished.class);
        when(finishEvent.getTestStep()).thenReturn(hookStep);

        startHandler.receive(startEvent);
        finishHandler.receive(finishEvent);

        // Hook step must never be logged to StepLogger
        stepLoggerMock.verify(() -> StepLogger.step(any(), any(), anyBoolean()), never());
    }
}

