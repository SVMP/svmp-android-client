package org.mitre.svmp;

import org.mitre.svmp.StateMachine.STATE;

/**
 * @author Joe Portner
 */
public interface StateObserver {
    // the observer receives a notification when the connection state changes
    public void onStateChange(STATE oldState, STATE newState, int resID);
}
