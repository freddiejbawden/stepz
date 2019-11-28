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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.Toast;

public class BodyActivity extends AppCompatActivity {


    // UI elememts
    private EditText etHeight;
    private EditText etWeight;
    private Button continueButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();






        etHeight = (EditText) findViewById(R.id.etRheight);
        etWeight = (EditText) findViewById(R.id.etRweight);
        continueButton = (Button) findViewById(R.id.button_continue);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String height_text = etHeight.getText().toString().trim();
                String weight_text = etWeight.getText().toString().trim();


                // Make sure heights not empty
                if (height_text.isEmpty()) {
                    etHeight.setError("Height is required");
                    etHeight.requestFocus();
                    return;
                }

                // Make sure weights not empty
                if (weight_text.isEmpty()) {
                    etWeight.setError("Weight is required");
                    etWeight.requestFocus();
                    return;
                }

                editor.putString("Height",height_text);
                editor.putString("Weight",weight_text);
                editor.putBoolean("Init",true);
                editor.apply();

                startActivity(new Intent(BodyActivity.this, MainActivity.class));

            }
        });






    }

}
