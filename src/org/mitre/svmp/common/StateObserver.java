package org.mitre.svmp.common;

import org.mitre.svmp.common.StateMachine.STATE;

/**
 * @author Joe Portner
 */
public interface StateObserver {
    // the observer receives a notification when the connection state changes
    public void onStateChange(STATE oldState, STATE newState, int resID);
}
