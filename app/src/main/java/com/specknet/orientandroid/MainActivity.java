package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.opencsv.CSVWriter;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.reactivex.disposables.Disposable;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;


import org.w3c.dom.Text;

import static java.lang.Integer.toUnsignedLong;

public class MainActivity extends Activity {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "FF:37:3C:B4:74:63";
    private static final String CHANNEL_ID = "123456";


    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";
    private final static double walkingFactor = 0.57;


    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    //private int n = 0;

    boolean connected = false;
    private int counter = 0;

    // UI elements
    private Button step_on_off_button;
    private Button reset;
    private Context ctx;
    private ImageView back;
    private TextView distText;
    private TextView calsText;
    private TextView timeText;


    private TextView stepView;
    private LineGraphSeries<DataPoint> seriesX;
    private GraphView graph;
    private int inputCounter = 0;
    private final int RC_LOCATION_AND_STORAGE = 1;
    private  ArrayList<Double> mags = new ArrayList<Double>();
    private StepCounter sc = new StepCounter(2.75,0.33333,20,10);
    private boolean stepSwitch = false;
    private PointsGraphSeries<DataPoint> peakDatapoints;
    private long init_time = 0;
    private long time_passed = 0;
    private double stride_length = 0;
    private int height = 0;
    private String gender = "Male";
    private double weight = 0.0;
    private double time_walked_init = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View decorView = getWindow().getDecorView();
        int uiOpt = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOpt);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);



        height = Integer.valueOf(preferences.getString("Height","180"));
        gender = preferences.getString("Gender","Male");
        weight = Double.valueOf(preferences.getString("Weight","120"));


        ctx = this;

        distText = (TextView) findViewById(R.id.dist_walked_text);
        calsText = (TextView) findViewById(R.id.cals_burned_text);
        timeText = (TextView) findViewById(R.id.time_walked_text);


        seriesX = new LineGraphSeries<>();
        seriesX.setColor(Color.BLUE);

        peakDatapoints = new PointsGraphSeries<>();


        stride_length = strideLength(height, gender);

        getPermissions();
    }

    @AfterPermissionGranted(RC_LOCATION_AND_STORAGE)
    private void getPermissions() {
        String[] perms = {Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(this, perms)) {
            // Already have permission, do the thing
            // ...
            runApp();

        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.location_and_storage_rationale),
                    RC_LOCATION_AND_STORAGE, perms);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private void runApp() {
        stepView = findViewById(R.id.steps);


        step_on_off_button = findViewById(R.id.onOff);
        step_on_off_button.setText(R.string.resume);

        step_on_off_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stepSwitch = !stepSwitch;
                if(stepSwitch) {
                    step_on_off_button.setBackground(getDrawable(R.drawable.roundcornergreen));
                    step_on_off_button.setText(R.string.pause);
                } else {
                    step_on_off_button.setBackground(getDrawable(R.drawable.roundedcorner));
                    step_on_off_button.setText(R.string.resume);

                }
            }
        });




        packetData = ByteBuffer.allocate(18);
        packetData.order(ByteOrder.LITTLE_ENDIAN);

        rxBleClient = RxBleClient.create(this);

        scanSubscription = rxBleClient.scanBleDevices(
                new ScanSettings.Builder()
                        // .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // change if needed
                        // .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES) // change if needed
                        .build()
                // add filters if needed
        )
                .subscribe(
                        scanResult -> {
                            Log.i("OrientAndroid", "FOUND: " + scanResult.getBleDevice().getName() + ", " +
                                    scanResult.getBleDevice().getMacAddress());
                            // Process scan result here.
                            if (scanResult.getBleDevice().getMacAddress().equals(ORIENT_BLE_ADDRESS)) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ctx, "Found " + scanResult.getBleDevice().getName() + ", " +
                                                    scanResult.getBleDevice().getMacAddress(),
                                            Toast.LENGTH_SHORT).show();
                                });
                                connectToOrient(ORIENT_BLE_ADDRESS);
                                scanSubscription.dispose();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            runOnUiThread(() -> {
                                Toast.makeText(ctx, "BLE scanning error",
                                        Toast.LENGTH_SHORT).show();
                            });
                        }
                );
    }
    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
       characteristic = ORIENT_RAW_CHARACTERISTIC;
        Log.d("BLE", "Connecting");
        orient_device.establishConnection(true)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            //n += 1;
                            // Given characteristic has been changes, here is the value.

                            Log.i("STEPS", "Received " + bytes.length + " bytes");
                            if (!connected) {
                                connected = true;
                                runOnUiThread(() -> {
                                                               Toast.makeText(ctx, "Connected To Step Counter",
                                                                       Toast.LENGTH_SHORT).show();
                                                           });
                            }
                            if (raw) handleRawPacket(bytes); else handleQuatPacket(bytes);
                        },
                        throwable -> {
                            // Handle an error here.
                            Log.e("OrientAndroid", "Error: " + throwable.toString());
                            throwable.printStackTrace();
                        }
                );
    }

    private void handleQuatPacket(final byte[] bytes) {
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        int w = packetData.getInt();
        int x = packetData.getInt();
        int y = packetData.getInt();
        int z = packetData.getInt();

        double dw = w / 1073741824.0;  // 2^30
        double dx = x / 1073741824.0;
        double dy = y / 1073741824.0;
        double dz = z / 1073741824.0;

        Log.i("OrientAndroid", "QuatInt: (w=" + w + ", x=" + x + ", y=" + y + ", z=" + z + ")");
        Log.i("OrientAndroid", "QuatDbl: (w=" + dw + ", x=" + dx + ", y=" + dy + ", z=" + dz + ")");
    }
    private boolean isWalking(List<Double> magnitudes) {
        double mag_th = 1.1;
        boolean is_under = true;
        List<Double> slicedMags = magnitudes;
        if (magnitudes.size() > 10) {
            slicedMags = magnitudes.subList(magnitudes.size() - 10, magnitudes.size());
        }
        for (double mag : slicedMags) {
            if (mag > mag_th) {
                return true;
            }
        }
        return false;
    }
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
    public static long bytesToInt(final byte[] array, final int start)
    {
        Log.d("STEPS",bytesToHex(array));
        final ByteBuffer buf = ByteBuffer.wrap(array); // big endian by default
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(start);
        return Integer.toUnsignedLong(buf.getInt());
    }
    private void handleRawPacket(final byte[] bytes) {
        if (init_time==0.0) {
            init_time = System.currentTimeMillis();
        } else {
            time_passed = System.currentTimeMillis() - init_time;
            Log.d("TimePassed", time_passed+"");

        }


        Log.d("STEP", "dat");
        long mag_l = bytesToInt(bytes,0);
        double mag = ((float) mag_l)/1e6;
        Log.d("STEP",""+mag);

        mags.add(mag);
        final boolean moving = isWalking(mags) && mags.size() > 10;
        if (moving) {
            if (stepSwitch) {
                sc.stepDetection(mag, (double) System.currentTimeMillis());
                Log.d("STEP", sc.getPeaks() + "");

            }
        } else {
            Log.d("STARTSTOP","stopped");
        }

        Log.d("STEP", ""+sc.getCount());

        if(moving){

            if (time_walked_init==0.0) {
                time_walked_init = System.currentTimeMillis();
            } else {
                time_passed = System.currentTimeMillis() - init_time;
                if(time_passed>30000) {

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_happy)
                            .setContentTitle("Movement alert!")
                            .setContentText("You haven't moved in " + time_passed + "!")
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText("Much longer text that cannot fit one line..."))
                            .setPriority(NotificationCompat.PRIORITY_HIGH);

                }

            }

            long time = System.currentTimeMillis();
        }


        double dist  = distanceTravelled(sc.getCount(),stride_length);

        double cals_burned = caloriesBurned(weight, dist, sc.getCount(), stride_length);

        distText.setText(dist+"");
        calsText.setText(cals_burned+"");




        if (counter % 4 == 0 && stepSwitch) {
            seriesX.appendData(new DataPoint(inputCounter, mag),true,500);

            inputCounter+=1;
        }
        counter += 1;
        runOnUiThread(() -> {
            stepView.setText(""+sc.getCount());
            if (moving) {
                back.setColorFilter(Color.GREEN);
            } else {
                back.setColorFilter(Color.RED);

            }
        });


    }


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private double strideLength(Integer height, String gender) {
        switch (gender) {

            case "Male": return height*39.37*0.415;

            case "Female": return height*39.37*0.413;

            default:
                throw new IllegalStateException("Unexpected value: " + gender);
        }
    }

    private double distanceTravelled(Integer steps, double stride_length) {
        return steps*stride_length;
    }

    /*
    Taken from https://fitness.stackexchange.com/questions/25472/how-to-calculate-calorie-from-pedometer and adapted using formula found online.
    Calculates the calories burned for the user using his distance, weight and height.
     */
    public double caloriesBurned(double weight, Double distance, Integer stepsCount, double stride_length) {

        double step_size_feet = stride_length / 12; //feet/stride

        double distance_feet = distance*3280.84;


        Log.d("MILEAGE", Double.toString(distance_feet));

        Log.d("STEPSIZE", Double.toString(step_size_feet));


        Log.d("FITBIT", Double.toString(stepsCount));


        double caloriesBurnedPerMile = walkingFactor * (weight * 2.2);


        double stepCountMile = 5280 / step_size_feet;

        double conversationFactor = caloriesBurnedPerMile / stepCountMile;

        return stepsCount * conversationFactor;
    }
}

