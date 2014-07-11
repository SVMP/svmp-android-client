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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import org.mitre.svmp.auth.module.PasswordModule;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.common.Utility;
import org.mitre.svmp.services.SessionService;
import org.mitre.svmp.client.R;
import org.mitre.svmp.widgets.ConnectionInfoArrayAdapter;

import java.util.List;

/**
 * @author Joe Portner & David Schoenheit
 */
public class ConnectionList extends SvmpActivity {
    private static String TAG = ConnectionList.class.getName();
    private static final int REQUEST_CONNECTIONDETAILS = 100;
    private static final int REQUEST_CONNECTIONAPPLIST = 101;
    private static final int REQUEST_STARTVIDEO = 102;        // opens AppRTCVideoActivity
    private static final int REQUEST_CHANGEPASSWORD = 103;    // opens AppRTCChangePasswordActivity

    private List<ConnectionInfo> connectionInfoList;
    private ListView listView;
    private BroadcastReceiver receiver;
    private int sendRequestCode = REQUEST_STARTVIDEO; // used in the "afterStartAppRTC" method to determine what activity gets started

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.connection_list);
        // title has to be set here instead of in Manifest, for compatibility with shortcuts
        setTitle(R.string.connectionList_title);

        // enable long-click on the ListView
        registerForContextMenu(listView);

        // register a BroadcastReceiver that will allow for triggered layout refreshes
        // when a running SessionService stops, it sends this broadcast to notify the ConnectionList to update its
        // entries (i.e. revert "running" entry green text back to gray text)
        IntentFilter filter = new IntentFilter(ACTION_REFRESH);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                populateLayout();
            }
        };
        registerReceiver(receiver, filter, PERMISSION_REFRESH, null);

        // if we received an intent indicating which connection to open, act upon it
        Intent intent = getIntent();
        if (intent.hasExtra("connectionID")) {
            int id = intent.getIntExtra("connectionID", 0);
            ConnectionInfo connectionInfo = dbHandler.getConnectionInfo(id);
            if (connectionInfo != null) {
                // if we allow use of desktop mode, start the connection; otherwise take us to this connection's app list
                if (Utility.getPrefBool(this, R.string.preferenceKey_connection_useDesktopMode, R.string.preferenceValue_connection_useDesktopMode)) {
                    this.sendRequestCode = REQUEST_STARTVIDEO;
                    authPrompt(connectionInfo);
                }
                else {
                    startConnectionAppList(connectionInfo);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // unregister BroadcastReceiver
        unregisterReceiver(receiver);
    }

    // called onResume so preference changes take effect in the layout
    @Override
    protected void refreshPreferences() {
    }

    // queries the database for the list of connections and populates the ListView in the layout
    @Override
    protected void populateLayout() {
        listView = (ListView)findViewById(R.id.connectionList_listView_connections);

        // get the list of ConnectionInfo objects
        connectionInfoList = dbHandler.getConnectionInfoList();
        // populate the items in the ListView
        //listView.setAdapter(new ArrayAdapter<ConnectionInfo>(getApplicationContext(), android.R.layout.simple_list_item_1, connectionInfoList));
        listView.setAdapter(new ConnectionInfoArrayAdapter(this,
                connectionInfoList.toArray(new ConnectionInfo[connectionInfoList.size()]))); // use two-line list items
    }

    public void onClick_Item(View view) {
        try {
            int position = listView.getPositionForView(view);
            ConnectionInfo connectionInfo = (ConnectionInfo)listView.getItemAtPosition(position);
            // if we allow use of desktop mode, start the connection; otherwise take us to this connection's app list
            if (Utility.getPrefBool(this, R.string.preferenceKey_connection_useDesktopMode, R.string.preferenceValue_connection_useDesktopMode)) {
                this.sendRequestCode = REQUEST_STARTVIDEO;
                authPrompt(connectionInfo);
            }
            else {
                startConnectionAppList(connectionInfo);
            }
        } catch( Exception e ) {
            // don't care
        }
    }

    public void onClick_Apps(View view) {
        try {
            View item = (View)view.getParent();
            int position = listView.getPositionForView(item);
            ConnectionInfo connectionInfo = (ConnectionInfo)listView.getItemAtPosition(position);
            startConnectionAppList(connectionInfo);
        } catch( Exception e ) {
            // don't care
        }
    }

    // new button is clicked
    public void onClick_New(View v) {
        // start a ConnectionDetails activity for creating a new connection
        startConnectionDetails(0);
    }

    // exit button is clicked
    public void onClick_Exit(View v) {
        finish();
    }

    // Context Menu handles long-pressing (prompt allows user to edit or remove connections)
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(v.getId() == R.id.connectionList_listView_connections){
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            ConnectionInfo connectionInfo = connectionInfoList.get(info.position);
            menu.setHeaderTitle(connectionInfo.getDescription());

            // if our preferences are set to allow use of "Desktop mode", enable that context button
            if (Utility.getPrefBool(this, R.string.preferenceKey_connection_useDesktopMode, R.string.preferenceValue_connection_useDesktopMode))
                menu.add(Menu.NONE, 0, 0, R.string.connectionList_context_connectToDesktop_text);

            menu.add(Menu.NONE, 1, 1, R.string.connectionList_context_editConnection_text);
            menu.add(Menu.NONE, 2, 2, R.string.connectionList_context_removeConnection_text);

            // if this uses password authentication, add an option to change the password
            if ((connectionInfo.getAuthType() & PasswordModule.AUTH_MODULE_ID) == PasswordModule.AUTH_MODULE_ID)
                menu.add(Menu.NONE, 99, 99, R.string.connectionList_context_changePassword_text);

            // if this connection is running, display the context option to close it
            if (SessionService.isRunningForConn(connectionInfo.getConnectionID()))
                menu.add(Menu.NONE, 100, 100, R.string.connectionList_context_stop_text);
        }
    }
    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        ConnectionInfo connectionInfo = connectionInfoList.get(info.position);
        switch(item.getItemId()){
            case 0: // Connect
                this.sendRequestCode = REQUEST_STARTVIDEO;
                authPrompt(connectionInfo);
                break;
            case 1: // Edit connection
                // start a ConnectionDetails activity for editing an existing connection
                startConnectionDetails( connectionInfo.getConnectionID() );
                break;
            case 2: // Remove connection
                // find the connectionID; if that connection is running, stop the session service
                int connectionID = connectionInfo.getConnectionID();
                if (SessionService.isRunningForConn(connectionID))
                    stopService(new Intent(ConnectionList.this, SessionService.class));
                // delete the connection info and repopulate the layout to reflect changes
                dbHandler.deleteConnectionInfo(connectionID);
                populateLayout();
                toastLong(R.string.connectionList_toast_removed);
                break;
            case 99: // Change password
                this.sendRequestCode = REQUEST_CHANGEPASSWORD;
                passwordChangePrompt(connectionInfo);
                break;
            case 100: // Stop
                stopService(new Intent(ConnectionList.this, SessionService.class)); // stop the service that's running
                break;
        }
        return true;
    }

    // starts a ConnectionDetails activity for editing an existing connection or creating a new connection
    private void startConnectionDetails(int id) {
        // create explicit intent
        Intent intent = new Intent(ConnectionList.this, ConnectionDetails.class);

        // if the given ID is valid (i.e. greater than zero), we are editing an existing connection
        if( id > 0 )
            intent.putExtra("id", id);

        // start the activity and expect a result intent when it is finished
        startActivityForResult(intent, REQUEST_CONNECTIONDETAILS);
    }

    private void startConnectionAppList(ConnectionInfo connectionInfo) {
        // create explicit intent
        Intent intent = new Intent(ConnectionList.this, AppList.class);
        intent.putExtra("connectionID", connectionInfo.getConnectionID());
        intent.putExtra("description", connectionInfo.getDescription());

        // start the activity and expect a result intent when it is finished
        startActivityForResult(intent, REQUEST_CONNECTIONAPPLIST);
    }

    @Override
    protected void afterStartAppRTC(ConnectionInfo connectionInfo) {
        // after we have handled the auth prompt and made sure the service is started...

        // create explicit intent
        Intent intent = new Intent();
        if (sendRequestCode == REQUEST_STARTVIDEO) {
            intent.setClass(ConnectionList.this, AppRTCVideoActivity.class);
        }
        else if (sendRequestCode == REQUEST_CHANGEPASSWORD) {
            intent.setClass(ConnectionList.this, AppRTCChangePasswordActivity.class);
        }
        intent.putExtra("connectionID", connectionInfo.getConnectionID());

        // start the AppRTCActivity
        startActivityForResult(intent, sendRequestCode);
    }

    // activity returns
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        busy = false;
        if (requestCode == REQUEST_CHANGEPASSWORD && resultCode == RESULT_OK) {
            toastShort(R.string.connectionList_toast_passwordChange_success);
        }
        else // fall back to superclass method
            super.onActivityResult(requestCode, resultCode, data);
    }
}