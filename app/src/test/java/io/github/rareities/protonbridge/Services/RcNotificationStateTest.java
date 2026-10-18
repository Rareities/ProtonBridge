package io.github.rareities.protonbridge.Services;

import org.junit.Test;
import static org.junit.Assert.*;

public class RcNotificationStateTest {
    @Test public void runningPollsDoNotRepeatIdenticalNotifications() {
        RcNotificationState state = new RcNotificationState();
        assertTrue(state.update(2, 0, 0));
        for (int poll = 0; poll < 100; poll++) assertFalse(state.update(2, 0, 0));
    }

    @Test public void completionAndFailureChangesPublishImmediately() {
        RcNotificationState state = new RcNotificationState();
        assertTrue(state.update(2, 0, 0));
        assertTrue(state.update(1, 1, 0));
        assertTrue(state.update(0, 1, 1));
        assertFalse(state.update(0, 1, 1));
    }

    @Test public void newJobWithRetainedHistoryPublishes() {
        RcNotificationState state = new RcNotificationState();
        assertTrue(state.update(0, 3, 1));
        assertTrue(state.update(1, 3, 1));
    }

    @Test public void foregroundPlaceholderForcesNextRealSummary() {
        RcNotificationState state = new RcNotificationState();
        assertTrue(state.update(1, 0, 0));
        state.reset();
        assertTrue(state.update(1, 0, 0));
        assertFalse(state.update(1, 0, 0));
    }
}
