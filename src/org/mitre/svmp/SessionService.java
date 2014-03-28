/*
 Copyright (c) 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import org.mitre.svmp.client.R;
import org.mitre.svmp.StateMachine.STATE;

/**
 * @author Joe Portner
 * An activity should use this service as follows:
 * 1. If the state is not NEW and the connectionID is different, stop the service
 * 2. Start the service (so it doesn't stop on unbind)
 * 3. Bind to the service
 */
public class SessionService extends Service implements StateObserver {
    private static final String TAG = SessionService.class.getName();
    private static final int NOTIFICATION_ID = 0;

    // only one service is started at a time, acts as a singleton for static getters
    private static SessionService service;

    // public getters for state and connectionID (used by activities)
    public static STATE getState() {
        STATE value = STATE.NEW;
        if (service != null && service.machine != null)
            value = service.machine.getState();
        return value;
    }
    public static int getConnectionID() {
        int value = 0;
        if (service != null && service.connectionInfo != null)
            value = service.connectionInfo.getConnectionID();
        return value;
    }

    // local variables
    private IBinder binder; // Binder given to clients
    private StateMachine machine;
    private DatabaseHandler databaseHandler;
    private ConnectionInfo connectionInfo;

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");

        service = this;
        machine = new StateMachine();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");

        if (getState() == STATE.NEW) {
            // change state and get connectionID from intent
            machine.setState(STATE.STARTED, 0);
            int connectionID = intent.getIntExtra("connectionID", 0);

            // begin connecting to the server
            startup(connectionID);
        }

        return START_NOT_STICKY; // run until explicitly stopped.
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, String.format("onBind (state: %s)", getState()));

        return binder;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");

        // before we destroy this service, shut down its components
        shutdown();

        super.onDestroy();
    }

    private void startup(int connectionID) {
        Log.i(TAG, "Starting background service.");

        // connect to the database
        databaseHandler = new DatabaseHandler(this);

        // get connection information from database
        connectionInfo = databaseHandler.getConnectionInfo(connectionID);

        // create binder object
        binder = new SVMPAppRTCClient(this, machine, connectionInfo);

        // show notification
        showNotification();
    }

    private void shutdown() {
        Log.i(TAG, "Shutting down background service.");

        // reset singleton
        service = null;

        // hide notification
        hideNotification();

        // disconnect from the database
        databaseHandler.close();
    }

    private void showNotification() {
        Notification.Builder notice = new Notification.Builder(this);
        notice.setContentTitle("SVMP Session Service")
                .setContentText(String.format("Connected to '%s'", connectionInfo.getDescription()))
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(NOTIFICATION_ID, notice.build());
        else
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .notify(NOTIFICATION_ID, notice.getNotification());
    }

    private void hideNotification() {
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(NOTIFICATION_ID);
    }

    public void onStateChange(STATE oldState, STATE newState, int resID) {
    }
}
