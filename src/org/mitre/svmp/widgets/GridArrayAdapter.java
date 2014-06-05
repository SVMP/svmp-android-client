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
package org.mitre.svmp.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import org.mitre.svmp.client.R;

/**
 * @author Joe Portner
 */
public abstract class GridArrayAdapter<T> extends ArrayAdapter<T> {
    private int mListItemLayoutResId;

    public GridArrayAdapter(Context context, T[] ts) {
        this(context, R.layout.app_list_item, ts);
    }

    public GridArrayAdapter(
            Context context,
            int listItemLayoutResourceId,
            T[] ts) {
        super(context, listItemLayoutResourceId, ts);
        mListItemLayoutResId = listItemLayoutResourceId;
    }

    @Override
    public View getView(
            int position,
            View convertView,
            ViewGroup parent) {


        LayoutInflater inflater = (LayoutInflater)getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View listItemView = convertView;
        if (null == convertView) {
            listItemView = inflater.inflate(
                    mListItemLayoutResId,
                    parent,
                    false);
        }

        // get the child views in the layout
        ImageView imageView = (ImageView)listItemView.findViewById(
                R.id.appListItem_imageView);
        TextView textView = (TextView)listItemView.findViewById(
                R.id.appListItem_textView);

        T t = (T)getItem(position);

        // try to decode the AppInfo's "icon" byte array into an image
        Bitmap bitmap = image(t);

//        if (bitmap == null)
//            bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.icon_default);

        // if decoding the bitmap was not successful, use the default icon
        if (bitmap != null)
            imageView.setImageBitmap(bitmap);
        else
            imageView.setImageResource(R.drawable.ic_launcher);

        textView.setText(text(t));

        return listItemView;
    }

    public abstract String text(T t);

    public abstract Bitmap image(T t);
}