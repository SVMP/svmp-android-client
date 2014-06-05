/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

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

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.ActionBar.*;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.GridView;
import android.widget.Toast;
import com.google.protobuf.InvalidProtocolBufferException;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.AppInfo;
import org.mitre.svmp.common.ConnectionInfo;
import org.mitre.svmp.protocol.SVMPProtocol;
import org.mitre.svmp.protocol.SVMPProtocol.Response;

import java.util.HashMap;
import java.util.List;

/**
 * This activity displays a list of remote apps that are available for a given Connection
 * @author Joe Portner
 */
public class AppList extends SvmpActivity {
    private static String TAG = AppList.class.getName();

    public static final int REQUEST_REFRESHAPPS_QUICK = 200; // send a request for the app list, get result, and finish
    public static final int REQUEST_REFRESHAPPS_FULL = 201; // send a request for the app list, get result, and finish
    public static final int REQUEST_STARTAPP_RESUME = 202; // resume the activity after returning
    public static final int REQUEST_STARTAPP_FINISH = 203; // if we return without errors, finish the activity

    protected int connectionID;
    protected ConnectionInfo connectionInfo;
    protected boolean launchedFromShortcut;
    private TabListener<AppListFragment> allApps;
    private TabListener<AppListFragment> favorites;
    private int sendRequestCode;
    private AppInfo sendAppInfo;

    public void onCreate(Bundle savedInstanceState) {
        // get intent data
        Intent intent = getIntent();
        connectionID = intent.getIntExtra("connectionID", 0);

        super.onCreate(savedInstanceState, -1);

        // Notice that setContentView() is not used, because we use the root
        // android.R.id.content as the container for each fragment

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // some functions are ICS-only
            if (API_ICS)
                onCreateICS(actionBar);
            actionBar.setDisplayHomeAsUpEnabled(true);

            // set title text
            connectionInfo = dbHandler.getConnectionInfo(connectionID);

            // setup action bar for tabs
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
            actionBar.setDisplayShowTitleEnabled(false);

            allApps = new TabListener<AppListFragment>(
                    this, "allApps", AppListFragment.class);
            Tab tab = actionBar.newTab()
                    .setText(R.string.appList_actionBar_allApps)
                    .setTabListener(allApps);
            actionBar.addTab(tab);

            favorites = new TabListener<AppListFragment>(
                    this, "favorites", AppListFragment.class);
            tab = actionBar.newTab()
                    .setText(R.string.appList_actionBar_favorites)
                    .setTabListener(favorites);
            actionBar.addTab(tab);
        }

        // if this was started by a desktop shortcut, just launch the requested app
        if(ACTION_LAUNCH_APP.equals(intent.getAction())) {
            launchedFromShortcut = true;
            String packageName = intent.getStringExtra("packageName");

            AppInfo appInfo = dbHandler.getAppInfo(connectionID, packageName);

            openApp(appInfo, false); // open the app; if we return without errors, finish the activity
        }
    }

    @TargetApi(14)
    private void onCreateICS(ActionBar actionBar) {
        // enable the app icon as an Up button
        actionBar.setHomeButtonEnabled(true);
    }

    // called onResume so preference changes take effect in the layout
    // repopulates all fragment layouts
    @Override
    protected void populateLayout() {
        allApps.populateLayout();
        favorites.populateLayout();
    }

    // called onResume so preference changes take effect in the layout
    @Override
    protected void refreshPreferences() {
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.applistmenu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // this method is called once the menu is selected
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch( menuItem.getItemId() ) {
            // the back button displayed as the home icon
            case android.R.id.home:
                // if we launched this activity directly from a shortcut, start a new ConnectionList activity
                if( launchedFromShortcut ) {
                    Intent intent = new Intent(this, ConnectionList.class);
                    startActivity(intent);
                }
                finish();
                return true;
            case R.id.refreshAppListQuick:
                this.sendRequestCode = REQUEST_REFRESHAPPS_QUICK;
                authPrompt(connectionInfo); // utilizes "startActivityForResult", which uses this.sendRequestCode
                return true;
            case R.id.refreshAppListFull:
                this.sendRequestCode = REQUEST_REFRESHAPPS_FULL;
                authPrompt(connectionInfo); // utilizes "startActivityForResult", which uses this.sendRequestCode
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void afterStartAppRTC(ConnectionInfo connectionInfo) {
        // after we have handled the auth prompt and made sure the service is started...

        // create explicit intent
        Intent intent = new Intent();
        if (this.sendRequestCode == REQUEST_REFRESHAPPS_QUICK || this.sendRequestCode == REQUEST_REFRESHAPPS_FULL) {
            // we're refreshing our cached list of apps that reside on the VM
            intent.setClass(AppList.this, AppRTCInfoActivity.class);
            if (this.sendRequestCode == REQUEST_REFRESHAPPS_FULL)
                intent.putExtra("fullRefresh", true);
        }
        else {
            // we're starting the video feed and launching a specific app
            intent.setClass(AppList.this, AppRTCVideoActivity.class);
            intent.putExtra("pkgName", sendAppInfo.getPackageName());
        }
        intent.putExtra("connectionID", connectionInfo.getConnectionID());

        // start the AppRTCActivity
        startActivityForResult(intent, this.sendRequestCode);
    }

    // activity returns
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        busy = false;
        if (resultCode == RESULT_CANCELED && (requestCode == REQUEST_REFRESHAPPS_QUICK || requestCode == REQUEST_REFRESHAPPS_FULL)) {
            // the activity ended before processing the Apps response
            toastShort(R.string.appList_toast_refreshFail);
        }
        else if (resultCode == RESULT_CANCELED && requestCode == REQUEST_STARTAPP_FINISH) {
            // the user intentionally canceled the activity, and we are supposed to finish this activity after resuming
            finish();
        }
        else // fall back to superclass method
            super.onActivityResult(requestCode, resultCode, data);
    }

    public void onClick_App(View view) {
        GridView gridView = (GridView)view.getParent();
        int position = gridView.getPositionForView(view);
        AppInfo appInfo = (AppInfo)gridView.getItemAtPosition(position);

        openApp(appInfo, true); // open the app, resume the activity after returning
    }

    // this can be triggered by clicking an app in this activity, or by clicking a shortcut on the desktop
    protected void openApp(AppInfo appInfo, boolean resume) {
        if (appInfo != null) {
            this.sendRequestCode = resume ? REQUEST_STARTAPP_RESUME : REQUEST_STARTAPP_FINISH;
            this.sendAppInfo = appInfo;
            authPrompt(connectionInfo); // utilizes "startActivityForResult", which uses this.sendRequestCode
        }
        else
            toastLong(R.string.appList_toast_notFound);
    }

    class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private AppListFragment mFragment;
        private final AppList mActivity;
        private final String mTag;
        private final Class<T> mClass;

        /** Constructor used each time a new tab is created.
         * @param activity  The host Activity, used to instantiate the fragment
         * @param tag  The identifier tag for the fragment
         * @param clz  The fragment's Class, used to instantiate the fragment
         */
        public TabListener(AppList activity, String tag, Class<T> clz) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
        }

        // called by the parent activity to repopulate the layout of this tab
        public void populateLayout() {
            if (mFragment != null)
                mFragment.populateLayout();
        }

    /* The following are each of the ActionBar.TabListener callbacks */

        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            // Check if the fragment is already initialized
            if (mFragment == null) {
                // If not, instantiate and add it to the activity
                mFragment = (AppListFragment)Fragment.instantiate(mActivity, mClass.getName());
                ft.add(android.R.id.content, mFragment, mTag);
            } else {
                // If it exists, simply attach it in order to show it
                ft.attach(mFragment);
            }
        }

        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
            if (mFragment != null) {
                // Detach the fragment, because another one is being attached
                ft.detach(mFragment);
            }
        }

        public void onTabReselected(Tab tab, FragmentTransaction ft) {
            // User selected the already selected tab. Usually do nothing.
        }
    }
}