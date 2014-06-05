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
package org.mitre.svmp.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.mitre.svmp.auth.AuthData;
import org.mitre.svmp.auth.AuthRegistry;
import org.mitre.svmp.auth.module.IAuthModule;
import org.mitre.svmp.auth.type.IAuthType;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.DatabaseHandler;
import org.mitre.svmp.protocol.SVMPProtocol.*;
import org.mitre.svmp.common.StateMachine.STATE;
import org.mitre.svmp.services.SessionService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Joe Portner
 */
public class SvmpActivity extends Activity implements Constants {
    public static final int REQUEST_STARTVIDEO = 100;
    public final static int RESULT_REPOPULATE = 100; // refresh the layout of the parent activity
    public final static int RESULT_REFRESHPREFS = 101; // preferences have changed, update the layout accordingly
    public final static int RESULT_FINISH = 102; // finish the parent activity
    public final static int RESULT_NEEDAUTH = 103; // need to authenticate

    // database handler
    protected DatabaseHandler dbHandler;
    protected boolean repopulateOnResume = true; // default behavior: repopulate layout during onResume()
    protected boolean busy = false; // is set to 'true' immediately after starting a connection, set to 'false' when resuming

    public void onCreate(Bundle savedInstanceState, int layoutId) {
        super.onCreate(savedInstanceState);

        // if layoutId is -1, we don't use a layout, we are using fragment activities within tab views

        // set the layout
        if (layoutId > -1)
            setContentView(layoutId);

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // initial layout population
        if (layoutId > -1)
            populateLayout();

        // change layout depending on preferences
        if (layoutId > -1)
            refreshPreferences();
    }

    // override this method in child classes
    protected void populateLayout() {}

    // override this method in child classes
    protected void refreshPreferences() {}

    // override this method in child classes(

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.mainmenu, menu);
        return true;
    }

    // this method is called once the menu is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch( menuItem.getItemId() ) {
            case R.id.preferences:
                Intent intent = new Intent(this, SvmpPreferences.class);
                startActivity(intent);
                break;
        }
        return true;
    }

    // activity returns
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        busy = false; // onActivityResult is called before onResume

        // if this result has an intent, and the intent has a message, display a Toast
        int resId;
        if( data != null && (resId = data.getIntExtra("message", 0)) > 0 )
            toastShort(resId);

        switch (resultCode) {
            default:
            case RESULT_CANCELED:
                break;
            case RESULT_REPOPULATE:
                populateLayout();
                break;
            case RESULT_REFRESHPREFS:
                refreshPreferences();
                break;
            case RESULT_FINISH:
                finish();
                break;
            case RESULT_NEEDAUTH:
                // we tried to authenticate but failed... try to find the ConnectionInfo we were connecting to
                if (data != null ) {
                    int connectionID = data.getIntExtra("connectionID", 0);
                    ConnectionInfo connectionInfo = dbHandler.getConnectionInfo(connectionID);

                    // check to see if we found the ConnectionInfo we were connecting to
                    if (connectionInfo != null) {
                        // find out if we previously used a session token to authenticate
                        String sessionToken = dbHandler.getSessionToken(connectionInfo);

                        if (sessionToken.length() > 0) {
                            // we used session token authentication and it failed
                            // discard it and retry normal authentication
                            dbHandler.clearSessionInfo(connectionInfo);
                            authPrompt(connectionInfo, true);
                        }
                        else {
                            // we used normal authentication and it failed
                            IAuthType authType = AuthRegistry.getAuthType(connectionInfo.getAuthType());
                            // check to see if this AuthType needs user input (ex: CertificateType doesn't)
                            // if it does, re-prompt the user
                            if (authType.needsUserInput())
                                authPrompt(connectionInfo, true);
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        busy = false;
        if (repopulateOnResume) {
            // repopulate the layout in case DB information has changed
            populateLayout();
        }
        // change layout depending on preferences
        refreshPreferences();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // close database connection
        dbHandler.close();
    }

    // overload
    protected void authPrompt(ConnectionInfo connectionInfo) {
        authPrompt(connectionInfo, false);
    }
    // Dialog for entering a password when a connection is opened
    protected void authPrompt(final ConnectionInfo connectionInfo, boolean forceAuth) {
        // 'forceAuth' is used if we need to re-authenticate but the session service might not be fully shut down yet

        // prevent messes from double-tapping
        if (busy)
            return;
        busy = true;

        // if the service is already running for this connection, no need to prompt or authenticate
        boolean serviceIsRunning = SessionService.getState() != STATE.NEW
                && SessionService.getConnectionID() == connectionInfo.getConnectionID();

        // if we have a session token, try to authenticate with it
        String sessionToken = dbHandler.getSessionToken(connectionInfo);

        if (!forceAuth && (serviceIsRunning || sessionToken.length() > 0)) {
            startAppRTC(connectionInfo);
        }
        // we don't have a session token, so prompt for authentication input
        else {
            // create the input container
            final LinearLayout inputContainer = (LinearLayout) getLayoutInflater().inflate(R.layout.auth_prompt, null);

            // set the message
            TextView message = (TextView)inputContainer.findViewById(R.id.authPrompt_textView_message);
            message.setText(connectionInfo.getUsername());

            IAuthModule[] authModules = AuthRegistry.getAuthModules();
            final HashMap<IAuthModule, View> moduleViewMap = new HashMap<IAuthModule, View>();
            boolean inputRequired = false;
            // loop through the available Auth Modules;
            for (IAuthModule module : authModules)
                // if one should be used for this Connection, let it add a View to the UI and store it in a map
                if ((connectionInfo.getAuthType() & module.getID()) == module.getID()) {
                //if (module.isModuleUsed(connectionInfo.getAuthType())) {
                    View view = module.generateUI(this);
                    moduleViewMap.put(module, view);
                    // add the View to the UI if it's not null; it may be null if a module doesn't require UI interaction
                    if (view != null) {
                        inputContainer.addView(view);
                        inputRequired = true;
                    }
                }

            if (inputRequired) {
                // create a dialog
                final AlertDialog dialog = new AlertDialog.Builder(SvmpActivity.this)
                        .setCancelable(false)
                        .setTitle( R.string.authPrompt_title)
                                //                .setMessage(connectionInfo.domainUsername())
                        .setView(inputContainer)
                        .setPositiveButton(R.string.authPrompt_button_positive_text,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        startAppRTCWithAuth(connectionInfo, moduleViewMap);
                                    }
                                })
                        .setNegativeButton(R.string.authPrompt_button_negative_text,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        busy = false;
                                    }
                                }).create();
                // show the dialog
                dialog.show();
                // request keyboard
                dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            else {
                // no input is required for the selected AuthType, so just start the next activity
                startAppRTCWithAuth(connectionInfo, moduleViewMap);
            }
        }
    }

    private void startAppRTCWithAuth(ConnectionInfo connectionInfo, HashMap<IAuthModule, View> moduleViewMap) {
        // create an Intent to send for authorization
        Request authRequest = buildAuthRequest(
                connectionInfo.getAuthType(),
                connectionInfo.getUsername(),
                moduleViewMap);
        // authorize user credentials
        AuthData.setAuthRequest(connectionInfo, authRequest);
        // legacy code (will change when Service is implemented)
        startAppRTC(connectionInfo);
    }

    private Request buildAuthRequest(int authTypeID, String domainUsername, HashMap<IAuthModule, View> moduleViewMap) {
        // create an Authentication protobuf
        AuthRequest.Builder aBuilder = AuthRequest.newBuilder();
        aBuilder.setType(AuthRequest.AuthRequestType.AUTHENTICATION);
        // the full domain username is used (i.e. "domain\\username", or "username" if domain is blank)
        aBuilder.setUsername(domainUsername);

        // loop through the Auth module(s) we're using, get the values, & put them in the Intent
        for (Map.Entry<IAuthModule, View> entry : moduleViewMap.entrySet()) {
            // add the value from the AuthModule, which may use input from a View from the Authentication prompt
            entry.getKey().addRequestData(aBuilder, entry.getValue());
        }

        // package the Authentication protobuf in a Request wrapper and return it
        Request.Builder rBuilder = Request.newBuilder();
        rBuilder.setType(Request.RequestType.AUTH);
        rBuilder.setAuthRequest(aBuilder);
        return rBuilder.build();
    }

    private void startAppRTC(ConnectionInfo connectionInfo) {
        // if the session service is running for a different connection, stop it
        boolean stopService = SessionService.getConnectionID() != connectionInfo.getConnectionID()
                && SessionService.getState() != STATE.NEW;
        if (stopService)
            stopService(new Intent(this, SessionService.class));

        // make sure the session service is running for this connection
        if (stopService || SessionService.getState() == STATE.NEW)
            startService(new Intent(this, SessionService.class).putExtra("connectionID", connectionInfo.getConnectionID()));

        // after we make sure the service is started, we can start the AppRTC actions for this activity
        afterStartAppRTC(connectionInfo);
    }

    // override this method in child classes
    protected void afterStartAppRTC(ConnectionInfo connectionInfo) {
    }

    protected void toastShort(int resId) {
        toast(resId, Toast.LENGTH_SHORT);
    }

    protected void toastLong(int resId) {
        toast(resId, Toast.LENGTH_LONG);
    }

    private void toast(int resId, int length) {
        Toast toast = Toast.makeText(this, resId, length);
        toast.show();
    }

    protected void finishMessage(int resId, int resultCode) {
        Intent intent = new Intent();
        intent.putExtra("message", resId);
        setResult(resultCode, intent);
        finish();
    }
}