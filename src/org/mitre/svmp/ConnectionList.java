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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
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
public class ConnectionList extends SvmpActivity {
    private static String TAG = ConnectionList.class.getName();
    private static final int REQUESTCODE_CONNECTIONDETAILS = 101;

    private List<ConnectionInfo> connectionInfoList;
    private ListView listView;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.connection_list);

        // enable long-click on the ListView
        registerForContextMenu(listView);
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
            authPrompt((ConnectionInfo) listView.getItemAtPosition(position));
            startService(new Intent(this, SessionService.class));
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
            menu.setHeaderTitle(connectionInfoList.get(info.position).getDescription());
            String[] menuItems = getResources().getStringArray(R.array.connectionList_context_items);
            for(int i = 0; i < menuItems.length; i++)
                menu.add(Menu.NONE, i, i, menuItems[i]);
        }
    }
    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        switch(item.getItemId()){
            case 0: // Connect
                authPrompt(connectionInfoList.get(info.position));
                break;
            case 1: // Edit connection
                // start a ConnectionDetails activity for editing an existing connection
                startConnectionDetails( connectionInfoList.get(info.position).getConnectionID() );
                break;
            case 2: // Remove connection
                dbHandler.deleteConnectionInfo(connectionInfoList.get(info.position).getConnectionID());
                populateLayout();
                toastLong(R.string.connectionList_toast_removed);
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
        startActivityForResult(intent, REQUESTCODE_CONNECTIONDETAILS);
    }
}