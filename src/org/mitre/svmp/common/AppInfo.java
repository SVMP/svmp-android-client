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
package org.mitre.svmp.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * @author Joe Portner
 */
public class AppInfo {
    private static String TAG = AppInfo.class.getName();

    private int connectionID; // foreign key
    private String packageName;
    private String appName;
    private boolean favorite;
    private byte[] icon;
    private byte[] iconHash;

    public AppInfo(int connectionID, String packageName, String appName, boolean favorite, byte[] icon, byte[] iconHash) {
        this.connectionID = connectionID;
        this.packageName = packageName;
        this.appName = appName;
        this.favorite = favorite;
        this.icon = icon;
        this.iconHash = iconHash;
    }

    public int getConnectionID() {
        return connectionID;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public byte[] getIcon() {
        if (icon != null && icon.length > 0)
            return icon.clone();
        return null;
    }

    public byte[] getIconHash() {
        if (iconHash != null && iconHash.length > 0)
            return iconHash.clone();
        return null;
    }

    public String toString() {
        return appName;
    }

    // tries to construct a bitmap from the icon byte array, returns null in case of failure
    public Bitmap getBitmap() {
        Bitmap bitmap = null;

        if (icon != null && icon.length > 0) {
            try {
                bitmap = BitmapFactory.decodeByteArray(icon, 0, icon.length);
            } catch (Exception e) {
                Log.e(TAG, String.format("Failed decoding icon for app: " +
                        "[connectionID '%d', packageName '%s', appName '%s']",
                        connectionID, packageName, appName));
            }
        }

        return bitmap;
    }
}
