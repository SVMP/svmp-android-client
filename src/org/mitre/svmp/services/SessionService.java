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
package org.mitre.svmp.services;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.*;
import android.util.Log;
import android.widget.Toast;
import org.mitre.svmp.activities.ConnectionList;
import org.mitre.svmp.apprtc.AppRTCClient;
import org.mitre.svmp.apprtc.MessageHandler;
import org.mitre.svmp.client.*;
import org.mitre.svmp.common.*;
import org.mitre.svmp.common.StateMachine.STATE;
import org.mitre.svmp.performance.PerformanceAdapter;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.AuthResponse.AuthResponseType;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

/**
 * @author Joe Portner
 * An activity should use this service as follows:
 * 1. If the state is not NEW and the connectionID is different, stop the service
 * 2. Start the service (so it doesn't stop on unbind)
 * 3. Bind to the service
 */
public class SessionService extends Service implements StateObserver, MessageHandler, SensorEventListener, Constants {
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
    public static boolean isRunningForConn(int connectionID) {
        return getConnectionID() == connectionID && getState() != StateMachine.STATE.NEW;
    }

    // local variables
    private AppRTCClient binder; // Binder given to clients
    private StateMachine machine;
    private PerformanceAdapter performanceAdapter;
    private NotificationManager notificationManager;
    private Handler handler;
    private DatabaseHandler databaseHandler;
    private ConnectionInfo connectionInfo;
    private boolean keepNotification;

    // client components
    private LocationHandler locationHandler;
    private SensorHandler sensorHandler;

    @Override
    public void onCreate() {
        Log.v(TAG, "onCreate");

        service = this;
        machine = new StateMachine();
        performanceAdapter = new PerformanceAdapter();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        handler = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand");

        if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopSelf();
        }
        else if (getState() == STATE.NEW) {
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
        binder = new AppRTCClient(this, machine, connectionInfo);

        // attach the performance adapter to the binder's performance data objects
        performanceAdapter.setPerformanceData(binder.getPerformance());

        // create a location handler object
        locationHandler = new LocationHandler(this);

        // create a sensor handler object
        sensorHandler = new SensorHandler(this, performanceAdapter);

        // show notification
        showNotification(true);
    }

    private void shutdown() {
        Log.i(TAG, "Shutting down background service.");

        // reset singleton
        service = null;

        // send intent to ConnectionList to notify it to check for active services again
        Intent intent = new Intent(ACTION_REFRESH);
        sendBroadcast(intent, PERMISSION_REFRESH);

        // hide notification
        hideNotification();

        // clean up location updates
        if (locationHandler != null)
            locationHandler.cleanupLocationUpdates();

        // clean up sensor updates
        if (sensorHandler != null)
            sensorHandler.cleanupSensors();

        // disconnect from the database
        if (databaseHandler != null)
            databaseHandler.close();

        // try to disconnect the client object
        performanceAdapter.clearPerformanceData();
        if (binder != null) {
            binder.disconnect();
            binder = null;
        }
    }

    private void showNotification(boolean connected) {
        Notification.Builder notice = new Notification.Builder(this);
        Resources resources = getResources();
        CharSequence contentTitle = resources.getText(R.string.sessionService_notification_contentTitle);

        String contentText;
        if (connected) {
            contentText = (String)resources.getText(R.string.sessionService_notification_contentText_connected);
            notice.setSmallIcon(R.drawable.svmp_status_green);
        }
        else {
            // we need authentication, indicate that in the notification
            contentText = (String)resources.getText(R.string.sessionService_notification_contentText_disconnected);
            notice.setSmallIcon(R.drawable.svmp_status_yellow);
        }
        notice.setContentTitle(contentTitle)
                .setContentText(String.format(contentText, connectionInfo.getDescription()))
                .setOngoing(true);

        // Creates an explicit intent for the ConnectionList
        Intent resultIntent = new Intent(this, ConnectionList.class);
        resultIntent.putExtra("connectionID", connectionInfo.getConnectionID());
        PendingIntent resultPendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // The stack builder object will contain an artificial back stack for the
            // started Activity.
            // This ensures that navigating backward from the Activity leads out of
            // your application to the Home screen.
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            // Adds the back stack for the Intent (but not the Intent itself)
            stackBuilder.addParentStack(ConnectionList.class);
            // Adds the Intent that starts the Activity to the top of the stack
            stackBuilder.addNextIntent(resultIntent);
            resultPendingIntent =  stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        else {
            resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        notice.setContentIntent(resultPendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // add "Open" action
            CharSequence openText = resources.getText(R.string.sessionService_notification_action_open);
            notice.addAction(android.R.drawable.ic_media_play, openText, resultPendingIntent);

            // add "Exit" action
            CharSequence exitText = resources.getText(R.string.sessionService_notification_action_exit);
            Intent stopServiceIntent = new Intent(ACTION_STOP_SERVICE, null, this, SessionService.class);
            PendingIntent stopServicePendingIntent = PendingIntent.getService(this, 0, stopServiceIntent, 0);
            notice.addAction(android.R.drawable.ic_menu_close_clear_cancel, exitText, stopServicePendingIntent);

            notificationManager.notify(NOTIFICATION_ID, notice.build());
        }
        else {
            notificationManager.notify(NOTIFICATION_ID, notice.getNotification());
        }
    }

    private void hideNotification() {
        // hide the notification if we aren't supposed to keep it past the service life
        if (!keepNotification)
            notificationManager.cancel(NOTIFICATION_ID);
    }

    public void onStateChange(STATE oldState, STATE newState, int resID) {
        if (newState == STATE.ERROR)
            stopSelf();
    }

    // Google AppEngine message handler method
    @Override
    public void onOpen() {
        locationHandler.initLocationUpdates();
        sensorHandler.initSensors(); // start forwarding sensor data
    }

    // Google AppEngine message handler method
    // Handler for receiving SVMP protocol messages and dispatching them appropriately
    // Returns true if the message is consumed, false if it is not
    @Override
    public boolean onMessage(Response data) {
        boolean consumed = true;
        switch (data.getType()) {
            case AUTH:
                AuthResponseType type = data.getAuthResponse().getType();
                if (type == AuthResponseType.SESSION_MAX_TIMEOUT) {

                    // if we are using the background service preference, change the notification icon to indicate that the connection has been halted
                    boolean useBackground = Utility.getPrefBool(this, R.string.preferenceKey_connection_useBackground, R.string.preferenceValue_connection_useBackground);
                    if (useBackground) {
                        keepNotification = true;
                        showNotification(false);
                    }

                    // the activity isn't running...
                    if (!binder.isBound()) {
                        // clear timed out session information from memory
                        databaseHandler.clearSessionInfo(connectionInfo);

                        // create a toast
                        doToast(R.string.svmpActivity_toast_sessionMaxTimeout);
                    }
                }
            case SCREENINFO:
            case WEBRTC:
                consumed = false; // pass this message on to the activity message handler
                break;
            case LOCATION:
                locationHandler.handleLocationResponse(data);
                break;
            // This is an ACK to the video STOP request.
            case INTENT:
                // handler is needed, we might create a toast from a background thread
                final Response finalData = data;
                handler.post(new Runnable() {
                    public void run() {
                        IntentHandler.inspect(finalData, SessionService.this);
                    }
                });
                break;
            case NOTIFICATION:
                NotificationHandler.inspect(data, SessionService.this, getConnectionID());
                break;
            case PING:
                long endDate = System.currentTimeMillis(); // immediately get end date
                if (data.hasPingResponse())
                    performanceAdapter.setPing(data.getPingResponse().getStartDate(), endDate);
                break;
            case APPS:
                consumed = false; // pass this message on to the activity message handler
                break;
            default:
                Log.e(TAG, "Unexpected protocol message of type " + data.getType().name());
        }
        return consumed;
    }

    // used by LocationHandler and SensorHandler to send messages
    public void sendMessage(SVMPProtocol.Request request) {
        if (binder != null)
            binder.sendMessage(request);
    }

    // Bridge the SensorEventListener callbacks to the SensorHandler
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (getState() == STATE.RUNNING)
            sensorHandler.onAccuracyChanged(sensor, accuracy);
    }

    // Bridge the SensorEventListener callbacks to the SensorHandler
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (getState() == STATE.RUNNING)
            sensorHandler.onSensorChanged(event);
    }

    private void doToast(final int resID) {
        // handler is needed to create a toast from a background thread
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(SessionService.this, resID, Toast.LENGTH_LONG).show();
            }
        });
    }
}
