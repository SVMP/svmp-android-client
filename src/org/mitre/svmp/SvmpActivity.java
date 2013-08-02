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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import org.mitre.svmp.client.R;

/**
 * @author Joe Portner
 */
public class SvmpActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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

    @Override
    public void onResume() {
        super.onResume();
        // change layout depending on preferences
        refreshPreferences();
    }

    // override this method in child classes
    protected void refreshPreferences() {}
}