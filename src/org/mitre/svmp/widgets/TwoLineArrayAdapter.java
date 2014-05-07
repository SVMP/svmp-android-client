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
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import org.mitre.svmp.client.R;

/**
 * @author Joe Portner
 */
public abstract class TwoLineArrayAdapter<T> extends ArrayAdapter<T> {
    private int mListItemLayoutResId;

    public TwoLineArrayAdapter(Context context, T[] ts) {
        this(context, R.layout.connection_list_item, ts);
    }

    public TwoLineArrayAdapter(
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
        TextView lineOneView = (TextView)listItemView.findViewById(
                R.id.connectionListItem_text1);
        TextView lineTwoView = (TextView)listItemView.findViewById(
                R.id.connectionListItem_text2);

        T t = (T)getItem(position);
        lineOneView.setText(lineOneText(t));
        lineTwoView.setText(lineTwoText(t));

        // if the session service is running for this connection, make the text green; otherwise, make it light gray
        if (isActive(t)) {
            lineOneView.setTextColor(Color.GREEN);
            lineTwoView.setTextColor(Color.GREEN);
        } else {
            lineOneView.setTextColor(Color.LTGRAY);
            lineTwoView.setTextColor(Color.LTGRAY);
        }

        ImageView lockImageView = (ImageView)listItemView.findViewById(
                R.id.connectionListItem_lockImage);

        if (!hasEncryption(t))
            lockImageView.setVisibility(View.GONE);

        return listItemView;
    }

    public abstract String lineOneText(T t);

    public abstract String lineTwoText(T t);

    public abstract boolean isActive(T t);

    public abstract boolean hasEncryption(T t);
}