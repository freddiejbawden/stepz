package com.specknet.orientandroid;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;

// Creating an Adapter Class
public class MyAdapter extends ArrayAdapter {

    private String type = "";

    public MyAdapter(Context context, int textViewResourceId, String type) {

        super(context, textViewResourceId);
        Log.d("TEST", "HERE");

        this.type = type;
    }

    public View getCustomView(int position, View convertView,
                              ViewGroup parent) {

        Log.d("TEST", "HERE");

        if(type.equals("height")) {
            // Inflating the layout for the custom Spinner
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View layout = inflater.inflate(R.layout.height_spinner, parent, false);

            // Declaring and Typecasting the textview in the inflated layout
            NumberPicker height = (NumberPicker) layout
                    .findViewById(R.id.heightPicker);

            NumberPicker units = (NumberPicker) layout
                    .findViewById(R.id.unitPicker);

            height.setMinValue(0);
            height.setMaxValue(300);
            units.setMinValue(0);
            units.setMaxValue(1);
            units.setDisplayedValues( new String[] { "cm", "feet/inches"} );

            return layout;
        } else {
            // Inflating the layout for the custom Spinner
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View layout = inflater.inflate(R.layout.weight_spinner, parent, false);

            // Declaring and Typecasting the textview in the inflated layout
            NumberPicker weight1 = (NumberPicker) layout
                    .findViewById(R.id.weightPicker1);

            // Declaring and Typecasting the textview in the inflated layout
            NumberPicker weight2 = (NumberPicker) layout
                    .findViewById(R.id.weightPicker2);

            NumberPicker units = (NumberPicker) layout
                    .findViewById(R.id.unitPicker1);

            weight1.setMinValue(0);
            weight1.setMaxValue(300);
            weight2.setMinValue(0);
            weight2.setMaxValue(9);
            units.setMinValue(0);
            units.setMaxValue(1);
            units.setDisplayedValues( new String[] { "kg", "lbs"} );

            return layout;
        }


    }

    // It gets a View that displays in the drop down popup the data at the specified position
    @Override
    public View getDropDownView(int position, View convertView,
                                ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }

    // It gets a View that displays the data at the specified position
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getCustomView(position, convertView, parent);
    }
}