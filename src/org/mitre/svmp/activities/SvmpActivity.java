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
import android.util.Log;
import android.view.*;
import android.widget.*;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.svmp.auth.AuthData;
import org.mitre.svmp.auth.AuthRegistry;
import org.mitre.svmp.auth.module.IAuthModule;
import org.mitre.svmp.auth.module.PasswordChangeModule;
import org.mitre.svmp.auth.module.PasswordModule;
import org.mitre.svmp.auth.type.IAuthType;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Constants;
import org.mitre.svmp.common.DatabaseHandler;
import org.mitre.svmp.common.SessionInfo;
import org.mitre.svmp.common.StateMachine.STATE;
import org.mitre.svmp.services.SessionService;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Joe Portner
 */
public class SvmpActivity extends Activity implements Constants {
    private static final String TAG = SvmpActivity.class.getName();

    public final static int RESULT_REPOPULATE = 100; // refresh the layout of the parent activity
    public final static int RESULT_REFRESHPREFS = 101; // preferences have changed, update the layout accordingly
    public final static int RESULT_FINISH = 102; // finish the parent activity
    public final static int RESULT_NEEDAUTH = 103; // need to authenticate
    public final static int RESULT_NEEDPASSWORDCHANGE = 104; // need to authenticate

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
                        SessionInfo sessionInfo = dbHandler.getSessionInfo(connectionInfo);

                        if (sessionInfo != null) {
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
            case RESULT_NEEDPASSWORDCHANGE:
                // either we need to change our password, or we tried to do so and that failed
                if (data != null ) {
                    int connectionID = data.getIntExtra("connectionID", 0);
                    ConnectionInfo connectionInfo = dbHandler.getConnectionInfo(connectionID);

                    // check to see if we found the ConnectionInfo we were connecting to
                    if (connectionInfo != null)
                        passwordChangePrompt(connectionInfo);
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
    // if necessary, generates a dialog for entering a password when a connection is opened
    // if the background service is running, or if the user has a session token, the dialog is bypassed
    protected void authPrompt(final ConnectionInfo connectionInfo, boolean forceAuth) {
        // 'forceAuth' is used if we need to re-authenticate but the session service might not be fully shut down yet

        // prevent messes from double-tapping
        if (busy)
            return;
        busy = true;

        // if the service is already running for this connection, no need to prompt or authenticate
        boolean serviceIsRunning = SessionService.isRunningForConn(connectionInfo.getConnectionID());

        // if we have a session token, try to authenticate with it
        SessionInfo sessionInfo = dbHandler.getSessionInfo(connectionInfo);

        if (!forceAuth && (serviceIsRunning || sessionInfo != null)) {
            startAppRTC(connectionInfo);
        }
        // we don't have a session token, so prompt for authentication input
        else {
            // create the input container
            final LinearLayout inputContainer = (LinearLayout) getLayoutInflater().inflate(R.layout.auth_prompt, null);

            // set the message
            TextView message = (TextView)inputContainer.findViewById(R.id.authPrompt_textView_message);
            message.setText(connectionInfo.getUsername());

            final HashMap<IAuthModule, View> moduleViewMap = new HashMap<IAuthModule, View>();
            // populate module view map, add input views for each required auth module, and check whether input is required
            boolean inputRequired = addAuthModuleViews(connectionInfo, moduleViewMap, inputContainer);

            if (inputRequired) {
                // create a dialog
                final AlertDialog dialog = new AlertDialog.Builder(SvmpActivity.this)
                        .setCancelable(false)
                        .setTitle(R.string.authPrompt_title_normal)
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

    // generates a dialog for a password change prompt
    protected void passwordChangePrompt(final ConnectionInfo connectionInfo) {
        // if this connection uses password authentication, proceed
        if ((connectionInfo.getAuthType() & PasswordModule.AUTH_MODULE_ID) == PasswordModule.AUTH_MODULE_ID) {

            // the service is running for this connection, stop it so we can re-authenticate
            if (SessionService.isRunningForConn(connectionInfo.getConnectionID()))
                stopService(new Intent(SvmpActivity.this, SessionService.class));

            // create the input container
            final LinearLayout inputContainer = (LinearLayout) getLayoutInflater().inflate(R.layout.auth_prompt, null);

            // set the message
            TextView message = (TextView)inputContainer.findViewById(R.id.authPrompt_textView_message);
            message.setText(connectionInfo.getUsername());

            final HashMap<IAuthModule, View> moduleViewMap = new HashMap<IAuthModule, View>();
            // populate module view map, add input views for each required auth module
            // (we know at least password input is required)
            addAuthModuleViews(connectionInfo, moduleViewMap, inputContainer);

            // loop through the Auth module(s) to find the View for the old password input (needed for validation)
            View oldPasswordView = null;
            for (Map.Entry<IAuthModule, View> entry : moduleViewMap.entrySet()) {
                if (entry.getKey().getID() == PasswordModule.AUTH_MODULE_ID) {
                    oldPasswordView = entry.getValue();
                    break;
                }
            }

            // add "new password" and "confirm new password" views
            final PasswordChangeModule module = new PasswordChangeModule(oldPasswordView);
            View moduleView = module.generateUI(this);
            moduleViewMap.put(module, moduleView);
            inputContainer.addView(moduleView);

            // create a dialog
            final AlertDialog dialog = new AlertDialog.Builder(SvmpActivity.this)
                    .setCancelable(false)
                    .setTitle(R.string.authPrompt_title_passwordChange)
                    .setView(inputContainer)
                    .setPositiveButton(R.string.authPrompt_button_positive_text, null)
                    .setNegativeButton(R.string.authPrompt_button_negative_text, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            busy = false;
                        }
                    }).create();

            // override positive button
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    if (positive != null) {
                        positive.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // before continuing, validate the new password inputs
                                int resId = module.areInputsValid();
                                if (resId == 0) {
                                    dialog.dismiss(); // inputs are valid, dismiss the dialog
                                    startAppRTCWithAuth(connectionInfo, moduleViewMap);
                                }
                                else {
                                    // tell the user that the new password is not valid
                                    toastShort(resId);
                                }
                            }
                        });
                    }
                }
            });

            // show the dialog
            dialog.show();
            // request keyboard
            dialog.getWindow().setSoftInputMode (WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    // populates module view map and adds input views for each required auth module
    // returns true if any auth module(s) require input, otherwise returns false
    private boolean addAuthModuleViews(ConnectionInfo connectionInfo, HashMap<IAuthModule, View> moduleViewMap, LinearLayout inputContainer) {
        boolean inputRequired = false;

        IAuthModule[] authModules = AuthRegistry.getAuthModules();
        // loop through the available Auth Modules;
        for (IAuthModule module : authModules)
            // if one should be used for this Connection, let it add a View to the UI and store it in a map
            if ((connectionInfo.getAuthType() & module.getID()) == module.getID()) {
                View view = module.generateUI(this);
                moduleViewMap.put(module, view);
                // add the View to the UI if it's not null; it may be null if a module doesn't require UI interaction
                if (view != null) {
                    inputContainer.addView(view);
                    inputRequired = true;
                }
            }

        return inputRequired;
    }

    // prepares a JSONObject using the auth dialog input, then starts the AppRTC connection
    private void startAppRTCWithAuth(ConnectionInfo connectionInfo, HashMap<IAuthModule, View> moduleViewMap) {
        // create a JSON Object
        String arg = String.format("{type: 'AUTHENTICATION', username: '%s'}", connectionInfo.getUsername());
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(arg);
        } catch (JSONException e) {
            Log.e(TAG, "startAppRTCWithAuth failed:", e);
            return;
        }

        // loop through the Auth module(s) we're using, get the values, & put them in the Intent
        for (Map.Entry<IAuthModule, View> entry : moduleViewMap.entrySet()) {
            // add the value from the AuthModule, which may use input from a View from the Authentication prompt
            entry.getKey().addRequestData(jsonObject, entry.getValue());
        }

        // store the user credentials to be used by the AppRTCClient
        AuthData.setAuthJSON(connectionInfo, jsonObject);
        // start the connection
        startAppRTC(connectionInfo);
    }

    // Start the AppRTC service and allow child to start correct AppRTC activity
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

    // override this method in child classes, this is where we start the appropriate AppRTC activity
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