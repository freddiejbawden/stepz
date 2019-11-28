package com.specknet.orientandroid;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);



        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean init  = preferences.getBoolean("Init", false);

        if(init) {
            startActivity(new Intent(SplashScreenActivity.this, MainActivity.class));
        } else {
            startActivity(new Intent(SplashScreenActivity.this, SexActivity.class));
        }

    }
}
