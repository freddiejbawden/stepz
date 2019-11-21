package com.specknet.orientandroid;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Toast;

public class BodyActivity extends AppCompatActivity {


    // UI elememts
    private Spinner weightSpinner;
    private Spinner heightSpinner;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();

        weightSpinner = (Spinner) findViewById(R.id.weightSpinner);
        heightSpinner = (Spinner) findViewById(R.id.heightSpinner);



        // Setting a Custom Adapter to the Spinner
        heightSpinner.setAdapter(new MyAdapter(BodyActivity.this, R.layout.height_spinner, "height"));
        weightSpinner.setAdapter(new MyAdapter(BodyActivity.this, R.layout.weight_spinner, "weight"));

    }

}
