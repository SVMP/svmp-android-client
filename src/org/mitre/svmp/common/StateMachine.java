package org.mitre.svmp.common;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joe Portner
 */
public class StateMachine {
    private static final String TAG = StateMachine.class.getName();

    // state changes in sequential order, except ERROR
    public static enum STATE {
        NEW,       // before the service has been started
        STARTED,   // after the service has been started
        AUTH,      // after we have authenticated
        CONNECTED, // after the WebSocket has been connected
        RUNNING,   // after the VM is ready and we've started proxying
        ERROR      // any other state can change to an error state
    }

    private STATE state = STATE.NEW;
    private List<StateObserver> observers = new ArrayList<StateObserver>();

    public STATE getState() {
        return state;
    }

    // sets the new state
    // resID is the message to display (0 for no message)
    public void setState(STATE newState, int resID) {
        STATE oldState = state;
        state = newState;
        Log.d(TAG, String.format("State change: %s -> %s", oldState, newState));

        // inform observers of state change
        for (StateObserver observer : observers) {
            observer.onStateChange(oldState, newState, resID);
        }
    }

    public void addObserver(StateObserver observer) {
        if (observer != null) {
            observers.add(observer);
        }
    }

    public void removeObserver(StateObserver observer) {
        if (observer != null) {
            observers.remove(observer);
        }
    }
}
