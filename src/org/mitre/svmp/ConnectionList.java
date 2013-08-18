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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import org.mitre.svmp.client.R;
import org.mitre.svmp.widgets.ConnectionInfoArrayAdapter;

import java.util.List;

/**
 * @author Joe Portner & David Schoenheit
 */
public class ConnectionList extends SvmpActivity implements Constants {
    private static String TAG = ConnectionList.class.getName();
    private static final int REQUESTCODE_VIDEO = 100;
    private static final int REQUESTCODE_CONNECTIONDETAILS = 101;

    private DatabaseHandler dbHandler;
    private List<ConnectionInfo> connectionInfoList;
    private LinearLayout quickConnect_layout;
    private ListView listView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set the layout
        setContentView(R.layout.connection_list);

        // connect to the database
        dbHandler = new DatabaseHandler(this);

        // get views
        quickConnect_layout = (LinearLayout)findViewById(R.id.connectionList_layout_quickConnect);
        listView = (ListView)findViewById(R.id.connectionList_listView_connections);

        // create listeners for buttons
        Button quickConnect_button = (Button) findViewById(R.id.connectionList_button_quickConnect),
                new_button = (Button) findViewById(R.id.connectionList_button_new),
                exit_button = (Button) findViewById(R.id.connectionList_button_exit);
        quickConnect_button.setOnClickListener(quickConnectHandler);
        new_button.setOnClickListener(newHandler);
        exit_button.setOnClickListener(exitHandler);

        // create listeners for ListView
        listView.setOnItemClickListener( new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                try {
                    passwordPrompt((ConnectionInfo) listView.getItemAtPosition(position));
                } catch( Exception e ) {
                    // don't care
                }

            }
        });
        registerForContextMenu(listView);

        // change layout depending on preferences
        refreshPreferences();

        // populate the ListView
        populateConnections();
    }

    // connect now button is clicked (temporary, for development use)
    View.OnClickListener quickConnectHandler = new View.OnClickListener() {
        public void onClick(View v) {

            // Check preferences
            final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            final String quickConnectPrefix = Utility.getPrefString(ConnectionList.this,
                    R.string.preferenceKey_interface_quickConnect_prefix,
                    R.string.preferenceValue_interface_quickConnect_prefix);
            final String quickConnectSuffix = settings.getString("quickConnectSuffix", "");

            // create the password input
            final EditText input = new EditText(ConnectionList.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER);
            input.setText(quickConnectSuffix);

            // show a dialog
            new AlertDialog.Builder(ConnectionList.this)
                    .setTitle("Enter last octet of IP")
                    .setMessage(quickConnectPrefix)
                    .setView(input)
                    .setPositiveButton("Submit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // get value
                            Editable value = input.getText();

                            // save value in preferences
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString("quickConnectSuffix", value.toString());
                            editor.commit();

                            startVideo(
                                    new ConnectionInfo(
                                            "FAKEDESCRIPTION",
                                            "FAKEUSERNAME",
                                            quickConnectPrefix + value.toString(),
                                            DEFAULT_PORT,
                                            DEFAULT_ENCRYPTION_TYPE ),
                                    "FAKEPASSWORD");
                        }
                    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
        }
    };

    // new button is clicked
    View.OnClickListener newHandler = new View.OnClickListener() {
        public void onClick(View v) {
            // start a ConnectionDetails activity for creating a new connection
            startConnectionDetails();
        }
    };

    // exit button is clicked
    View.OnClickListener exitHandler = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    // ConnectionDetails activity returns
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if( requestCode == REQUESTCODE_VIDEO || requestCode == REQUESTCODE_CONNECTIONDETAILS ) {
            // if this result has an intent, and the intent has a message, display a Toast
            if( data != null && data.hasExtra("message") ) {
                int resId = data.getIntExtra("message", 0);
                if( resId > 0 )
                    doToast(resId);
            }

            // repopulate the ListView in case the list has changed
            populateConnections();
        }
    }

    // Dialog for entering a password when a connection is opened
    private void passwordPrompt(final ConnectionInfo connectionInfo) {
        if( connectionInfo == null ) {
            Log.d(TAG, "Selected ConnectionInfo is null");
        }
        else {
            // create the password input
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            // show a dialog
            new AlertDialog.Builder(ConnectionList.this)
                    .setTitle( R.string.connectionList_dialog_passwordPrompt_title )
                    .setMessage( R.string.connectionList_dialog_passwordPrompt_message )
                    .setView(input)
                    .setPositiveButton(R.string.connectionList_dialog_passwordPrompt_positiveButton,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    Editable value = input.getText();

                                    startVideo(connectionInfo, value.toString());
                                }
                            }).setNegativeButton( R.string.connectionList_dialog_passwordPrompt_negativeButton,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // Do nothing.
                        }
                    }).show();
        }
    }

    // Context Menu handles long-pressing (prompt allows user to edit or remove connections)
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(v.getId() == R.id.connectionList_listView_connections){
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle(connectionInfoList.get(info.position).getDescription());
            String[] menuItems = getResources().getStringArray(R.array.connection_list_context_items);
            for(int i = 0; i < menuItems.length; i++){
                menu.add(Menu.NONE, i, i, menuItems[i]);
            }
        }
    }
    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch(item.getItemId()){
            case 0:
                // start a ConnectionDetails activity for editing an existing connection
                startConnectionDetails( connectionInfoList.get(info.position).getID() );
                break;
            case 1:
                dbHandler.deleteConnectionInfo(connectionInfoList.get(info.position).getID());
                populateConnections();
                doToast(R.string.connectionList_toast_removed);
                break;
        }
        return true;
    }

    // starts a ClientSideActivityDirect activity for connecting to a server
    private void startVideo(ConnectionInfo connectionInfo, String password) {
        // authorize user credentials
        AuthData.init(connectionInfo.getUsername(), password);

        // create explicit intent
        //Intent intent = new Intent(ConnectionList.this, ClientSideActivityDirect.class);
        Intent intent = new Intent(ConnectionList.this, AppRTCDemoActivity.class);

        // add data to intent
        intent.putExtra("host", connectionInfo.getHost());
        intent.putExtra("port", connectionInfo.getPort());
        intent.putExtra("encryptionType", connectionInfo.getEncryptionType());

        // start the activity without expecting a result
        startActivityForResult(intent, REQUESTCODE_VIDEO);
    }

    // starts a ConnectionDetails activity for editing an existing connection or creating a new connection
    private void startConnectionDetails(int id) {
        // create explicit intent
        Intent intent = new Intent(ConnectionList.this, ConnectionDetails.class);

        // if the given ID is valid (i.e. greater than zero), we are editing an existing connection
        if( id > 0 )
            intent.putExtra("id", id);

        // start the activity and expect a result intent when it is finished
        startActivityForResult(intent, REQUESTCODE_CONNECTIONDETAILS);
    }
    // overload, starts a ConnectionDetails activity for creating a new connection
    private void startConnectionDetails() { startConnectionDetails(0); }

    // called onResume so preference changes take effect in the layout
    @Override
    protected void refreshPreferences() {
        // get preferences
        boolean quickConnectEnabled = Utility.getPrefBool(this,
                R.string.preferenceKey_interface_quickConnect_enabled,
                R.string.preferenceValue_interface_quickConnect_enabled);

        // change layout depending on preferences
        quickConnect_layout.setVisibility( quickConnectEnabled ? View.VISIBLE : View.GONE);
    }

    // queries the database for the list of connections and populates the ListView in the layout
    private void populateConnections() {
        // get the list of ConnectionInfo objects
        connectionInfoList = dbHandler.getConnectionInfoList();
        // populate the items in the ListView
        //listView.setAdapter(new ArrayAdapter<ConnectionInfo>(getApplicationContext(), android.R.layout.simple_list_item_1, connectionInfoList));
        listView.setAdapter(new ConnectionInfoArrayAdapter(this,
                connectionInfoList.toArray(new ConnectionInfo[connectionInfoList.size()]))); // use two-line list items
    }

    private void doToast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }
}