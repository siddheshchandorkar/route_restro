package com.siddhesh.heretest;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.here.sdk.search.Place;

import java.util.ArrayList;

public class PlaceAdapter extends ArrayAdapter<Place> {
    public PlaceAdapter(Context context, ArrayList<Place> Places) {
        super(context, 0, Places);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        Place place = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        }
        TextView tvName = convertView.findViewById(android.R.id.text1);

        tvName.setText(place.getTitle());
        return convertView;
    }
}
