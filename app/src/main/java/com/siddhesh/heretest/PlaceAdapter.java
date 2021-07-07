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
    private Boolean isRestaurant;
    public PlaceAdapter(Context context, ArrayList<Place> Places, Boolean isRestaurant) {
        super(context, 0, Places);
        this.isRestaurant=isRestaurant;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Place place = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
        }
        TextView tvName = convertView.findViewById(android.R.id.text1);

        if(isRestaurant){
            tvName.setText(place.getAddress().addressText);
        }else{
            tvName.setText(place.getTitle());

        }
        return convertView;
    }
}
