package com.specknet.orientandroid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

public class SexActivity extends AppCompatActivity {


    private ImageButton femaleBtn;
    private ImageButton maleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sex);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();



        femaleBtn = (ImageButton) findViewById(R.id.femaleButton);
        maleBtn = (ImageButton) findViewById(R.id.maleButton);

        femaleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putString("Gender","Female");
                editor.apply();
                startActivity(new Intent(SexActivity.this, BodyActivity.class));
            }
        });


        maleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putString("Gender","Male");
                editor.apply();
                startActivity(new Intent(SexActivity.this, BodyActivity.class));
            }
        });



    }
}
