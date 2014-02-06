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

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import org.mitre.svmp.protocol.SVMPProtocol.Response;

import android.R;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class SessionService extends Service {
    final static String TAG = "SessionService";

    public static enum STATE {
        NEW, AUTH, READY, RUNNING
    };
    
    static final int NOTIFICATION_ID = 0;
    
    Messenger messenger,client;    
    DatabaseHandler databaseHandler;
    
    protected static STATE sessionState;
    ListenThread listener;

    public ListenThread getListener() {
        return listener;
    }
    
	@Override
    public void onCreate() {
        // connect to the database
    	sessionState = STATE.NEW;
        databaseHandler = new DatabaseHandler(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting background service. (STATE = " + sessionState);
        
        if (sessionState == STATE.NEW ) {
            //Shutdown in case something is already running.
            shutdown();

            // change state to New
            sessionState = STATE.NEW;

            sessionState = STATE.RUNNING;
            
            try {
                startup();
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        return START_NOT_STICKY; // run until explicitly stopped.
    }

    private void startup() throws RemoteException {
        listener = new ListenThread(this, client);
        listener.start();
    }
    
    private void shutdown() {
    	Log.i(TAG, "Service Stopped.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdown();
    }
    

    public class ListenThread extends Thread {
        Messenger client;

        SessionService parent;
        boolean isRunning = true;

        public ListenThread(SessionService parent, Messenger client) {
            this.parent = parent;
            this.client = client;
        }

        public void setClient(Messenger client) {
            this.client = client;
        }

        @SuppressWarnings("deprecation")
        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        @Override
        public void run() {
            int returnVal = 0; // resID of the message to display to the client (0 means no message)

            try {
                // Setup persistent notification.
                Notification.Builder notice = new Notification.Builder(parent);
                notice.setContentTitle("SVMP Message Receiver")
                        .setContentText("Listening for incoming messages...")
                        .setSmallIcon(org.mitre.svmp.client.R.drawable.logo)
                        .setOngoing(true);
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                            .notify(NOTIFICATION_ID, notice.build());
                else
                    ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                            .notify(NOTIFICATION_ID, notice.getNotification());
                isRunning = true;

                // Start listening loop.
                Log.i(TAG, "Server connection receive thread starting");
                while (isRunning) {
                    Log.d(TAG, "Waiting for incoming message");


                    Log.d(TAG, "Current state is: " + sessionState.toString());

                    Thread.sleep(5*1000);
                }

                // Cleanup and exit.
                Log.i(TAG, "Server connection receive thread exiting");
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                        .cancel(NOTIFICATION_ID);
            } catch (Exception e) {

                e.printStackTrace();
            }


            //state = STATE.NEW;
            //databaseHandler = new DatabaseHandler(parent);
            //videoInfo = null;
            Stop();
        }

        public synchronized void Stop() {
            Log.d(TAG, "ListenThread.Stop()");
            isRunning = false;
        }
    }


}
