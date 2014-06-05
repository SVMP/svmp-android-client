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

import android.app.ActionBar;
import android.app.Fragment;
import android.os.Bundle;
import android.view.*;
import android.widget.AdapterView;
import android.widget.GridView;
import org.mitre.svmp.client.R;
import org.mitre.svmp.common.AppInfo;
import org.mitre.svmp.common.Utility;
import org.mitre.svmp.widgets.AppInfoArrayAdapter;

import java.util.List;

/**
 * This fragment is used in the AppList activity to display a list of remote apps that are available for a Connection
 * @author Joe Portner
 */
public class AppListFragment extends Fragment {
    protected AppList activity;
    protected List<AppInfo> appInfoList;
    protected GridView gridView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.app_list, container, false);

        activity = (AppList)getActivity();

        gridView = (GridView)view.findViewById(R.id.appList_gridView);
        populateLayout();

        // set title text
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setSubtitle(activity.connectionInfo.getDescription());
        }

        // enable long-click on the GridView
        registerForContextMenu(gridView);

        // Inflate the layout for this fragment
        return view;
    }

    protected void populateLayout() {
        // get the list of AppInfo objects
        if ("favorites".equals(getTag()))
            appInfoList = activity.dbHandler.getAppInfoList_Favorites(activity.connectionID);
        else
            appInfoList = activity.dbHandler.getAppInfoList_All(activity.connectionID);

        // populate the items in the ListView
        gridView.setAdapter(new AppInfoArrayAdapter(activity,
                appInfoList.toArray(new AppInfo[appInfoList.size()]))); // use app grid items
    }

    // Context Menu handles long-pressing (prompt allows user to edit or remove connections)
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if(v.getId() == R.id.appList_gridView){
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            AppInfo appInfo = appInfoList.get(info.position);

            menu.setHeaderTitle(appInfo.getAppName());
            if (appInfo.isFavorite())
                menuAdd(menu, R.string.contextMenu_appList_removeFavorite);
            else
                menuAdd(menu, R.string.contextMenu_appList_addFavorite);
            menuAdd(menu, R.string.contextMenu_appList_createShortcut);
            menuAdd(menu, R.string.contextMenu_appList_removeShortcut);
        }
    }

    private void menuAdd(ContextMenu menu, int resId) {
        menu.add(Menu.NONE, resId, Menu.NONE, resId);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item){
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        AppInfo appInfo = appInfoList.get(info.position);

        switch(item.getItemId()){
            case R.string.contextMenu_appList_addFavorite:
                // add the app to favorites in the DB
                activity.dbHandler.updateAppInfo_Favorite(appInfo, true);
                // repopulate the layout of this tab and all other tabs
                activity.populateLayout();
                activity.toastShort(R.string.appList_toast_addedFavorite);
                break;
            case R.string.contextMenu_appList_removeFavorite:
                // remove the app from favorites in the DB
                activity.dbHandler.updateAppInfo_Favorite(appInfo, false);
                // repopulate the layout of this tab and all other tabs
                activity.populateLayout();
                activity.toastShort(R.string.appList_toast_removedFavorite);
                break;
            case R.string.contextMenu_appList_createShortcut: // Create shortcut
                Utility.createShortcut(activity, appInfo);
                activity.toastShort(R.string.appList_toast_createdShortcut);
                break;
            case R.string.contextMenu_appList_removeShortcut: // Remove shortcut
                Utility.removeShortcut(activity, appInfo);
                activity.toastShort(R.string.appList_toast_removedShortcuts);
                break;
        }
        return true;
    }
}