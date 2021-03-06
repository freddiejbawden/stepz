package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.scan.ScanSettings;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.annotations.NonNull;
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
import static java.lang.String.format;

public class MainActivity extends Activity {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "FF:37:3C:B4:74:63";
    private static final String CHANNEL_ID = "123456";
    private final static double walkingFactor = 0.57;
    private static NumberFormat formatter = new DecimalFormat("#0.000");



    private static final String MAG_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    private Disposable scanSubscription;
    private RxBleClient rxBleClient;

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
    private LineGraphSeries<DataPoint> seriesY;
    private LineGraphSeries<DataPoint> seriesZ;
    private long timeWalked;
    private int totalSteps;
    private int inputCounter = 0;
    private final int RC_LOCATION_AND_STORAGE = 1;
    private  ArrayList<Double> mags = new ArrayList<>();

    private StepCounter scPeak = new StepCounter(2.75,0.33333,20,10);
    private StepCounter scValley = new StepCounter(2.75,0.33333,20,10);

    private boolean stepSwitch = false;
    private long init_time = 0;
    private long time_passed = 0;
    private double stride_length = 0;
    private int height = 0;
    private String gender = "Male";
    private double weight = 0.0;
    private double time_walked_init = 0;
    private ArrayList<Double> gyroSmooth = new ArrayList<>();
    private ArrayList<Double> magStdList = new ArrayList<>();

    private int rising_edge_step_cache = 0;
    private int temp_stepCount;
    private int walkingFlag;

    private StepType prevStep = StepType.UNKNOWN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
       // View decorView = getWindow().getDecorView();
       // int uiOpt = View.SYSTEM_UI_FLAG_FULLSCREEN;
        //decorView.setSystemUiVisibility(uiOpt);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        createNotificationChannel();

        height = Integer.valueOf(preferences.getString("Height","180"));
        gender = preferences.getString("Gender","Male");
        weight = Double.valueOf(preferences.getString("Weight","120"));


        ctx = this;

        distText = (TextView) findViewById(R.id.dist_walked_text);
        calsText = (TextView) findViewById(R.id.cals_burned_text);
        timeText = (TextView) findViewById(R.id.time_walked_text);
        stepView = (TextView) findViewById(R.id.steps);


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
    public void onRequestPermissionsResult(int requestCode, @NonNull  String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    /**
     * runApp: start BLE client
     */
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

        ByteBuffer packetData = ByteBuffer.allocate(18);
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
                                runOnUiThread(() ->
                                    Toast.makeText(ctx, "Found " + scanResult.getBleDevice().getName() + ", " +
                                                    scanResult.getBleDevice().getMacAddress(),
                                            Toast.LENGTH_SHORT).show()
                                );
                                connectToOrient(ORIENT_BLE_ADDRESS);
                                scanSubscription.dispose();
                            }
                        },
                        throwable -> {
                            // Handle an error here.
                            runOnUiThread(() ->
                                Toast.makeText(ctx, "BLE scanning error",
                                        Toast.LENGTH_SHORT).show()
                            );
                        }
                );
    }

    /**
     * connectToOrient; connects to device at addr
     * @param addr; MAC address of the device
     */
    private void connectToOrient(String addr) {
        RxBleDevice orient_device = rxBleClient.getBleDevice(addr);
        Log.d("BLE", "Connecting");
        orient_device.establishConnection(true)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(MAG_CHARACTERISTIC)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(bytes -> {
                    if (!connected) {
                        connected = true;
                        runOnUiThread(() -> {
                            Toast.makeText(ctx, "Connected To Step Counter",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }
                    handleRawPacket(bytes);
                });
    }

    /**
     * isWalking: uses the magitude to figure out if we are moving or not
     *
     * @param magnitudeStd: a list of magnitideStd
     * @return
     */
    private int isWalking(List<Double> magnitudeStd) {
        List<Double> slicedMags;
        final int start = (magnitudeStd.size() > 10) ? magnitudeStd.size() - 10 : 0;
        slicedMags = magnitudeStd.subList(start, magnitudeStd.size());
        int thresh = 0;
        int rising = 0;
        double prev_std = 0;
        for (double std : slicedMags) {
            if (std > 0.05) {
                thresh++;
            }
            if (std > prev_std) {
                rising++;
            }
            prev_std = std;
        }
        if (thresh > 6) {
            return 2;
        } else if (rising > 8) {
            return 1;
        }
        return 0;
        /*
        double mag_th = 1.2;
        List<Double> slicedMags = magnitudes;
        if (magnitudes.size() > 10) {
            slicedMags = magnitudes.subList(magnitudes.size() - 10, magnitudes.size());
        }
        for (double mag : slicedMags) {
            if (mag > mag_th) {
                return 1;
            }
            }
        }
        */
    }

    /**
     *
     * bytesToBuffer, converts a byte array to a Java byte buffer
     *
     * @param array; a byte array
     * @param start; the position to start our buffer at
     * @return ByteBuffer cotnaining array, beginning at start
     */
    public static ByteBuffer bytesToBuffer(final byte[] array, final int start)
    {
        final ByteBuffer buf = ByteBuffer.wrap(array); // big endian by default
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(start);
        return buf;
    }

    /**
     *  Handles the recieivng of data from the sensor
     *  @param bytes a collection of bytes
     *
     */

    private void handleRawPacket(final byte[] bytes) {

        ByteBuffer buf = bytesToBuffer(bytes, 0);
        double mag = buf.getInt() / 1e6;
        double gyro = buf.getInt() / 1e8;

        // Smooth the gyroscope data
        gyroSmooth.add(gyro);
        if (gyroSmooth.size() > 3) {
            gyroSmooth.remove(0);
        }
        double sum = 0;
        for (double g : gyroSmooth) {
            sum += g;
        }
        final double averageGyro = sum / 3;

        // Calculate new std value
        mags.add(mag);
        final int start = (mags.size() > 10) ? mags.size() - 10 : 0;
        List<Double> mags_slice = mags.subList(start, mags.size() - 1);
        final double mag_sd = StepCounter.sd(mags_slice, StepCounter.calculateAverage(mags_slice));
        magStdList.add(mag_sd);

        // check if we are moving according to the std
        int moving = this.isWalking(magStdList);

        // if we are moving
        if (moving > 0 && stepSwitch) {

            // check if there was a step from either the positive or negated gyro
            boolean peakStep = scPeak.stepDetection(averageGyro, (double) System.currentTimeMillis());
            boolean valleyStep = scValley.stepDetection(averageGyro * -1, (double) System.currentTimeMillis());

            // if we are on the rising edge, and a step has happened, add it cache it
            if (moving == 1 && (peakStep || valleyStep)){
                rising_edge_step_cache += 1;
            } else if (moving == 2) {
                Log.d("STEP", ""+prevStep);
                // if a step occured count it
                if (peakStep && prevStep != StepType.PEAK || valleyStep && prevStep != StepType.VALLEY) {
                    if (walkingFlag == 1) {
                        totalSteps += 1;
                        init_time = System.currentTimeMillis();
                    } else {
                        init_time = System.currentTimeMillis();
                        temp_stepCount += 1;
                    }
                } else if (System.currentTimeMillis() - init_time > 1000 && walkingFlag == 1) {
                    // not step occured and it has been longer than 1000 milliseconds since the last step
                    // then reset
                    init_time = System.currentTimeMillis();
                    temp_stepCount = 0;
                    walkingFlag = 0;
                    rising_edge_step_cache = 0;
                    prevStep = StepType.UNKNOWN;
                }

                // if our step count is larger than 3, and we aren't walking, trigger a WD
                if (temp_stepCount >= 3 && walkingFlag == 0) {
                    walkingFlag = 1;
                    totalSteps += temp_stepCount;
                    totalSteps += rising_edge_step_cache;
                    rising_edge_step_cache = 0;
                    temp_stepCount = 0;
                }
            }
            if (peakStep) {
                prevStep = StepType.PEAK;
            } else if (valleyStep && !peakStep) {
                prevStep = StepType.VALLEY;
            }
        } else if (System.currentTimeMillis() - init_time > 1000) {
            // if time since last step is > 1000 then cancel
            temp_stepCount = 0;
            walkingFlag = 0;
            rising_edge_step_cache = 0;
            prevStep = StepType.UNKNOWN;
        }


        if(moving != 2 && stepSwitch){

            if (time_walked_init==0.0) {
                time_walked_init = System.currentTimeMillis();
            } else {
                time_passed = (long) (System.currentTimeMillis() - time_walked_init);
                Log.d("TIME", time_passed+"");
                if(time_passed>60000) {
                    Log.d("TIME", "noftify!");
                    Intent fullScreenIntent = new Intent(this, MainActivity.class);
                    PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                            fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);


                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_happy)
                            .setContentTitle("Movement alert!")
                            .setContentText("You haven't moved in " + Math.round(time_passed/(1000*60)) + "minutes !")
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText("Get up and move around!"))
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setFullScreenIntent(fullScreenPendingIntent, true);
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                    notificationManager.notify(0, builder.build());
                    time_walked_init = System.currentTimeMillis();



                }

            }

        } else {
            // if moving
            if( time_walked_init != 0) {
                timeWalked += System.currentTimeMillis() - time_walked_init;
            }
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            notificationManager.cancel(0);
            time_walked_init = System.currentTimeMillis();
        }


        double dist  = distanceTravelled(totalSteps,stride_length);
        double cals_burned = Math.round(caloriesBurned(weight, dist, totalSteps, stride_length));

        runOnUiThread(() -> {
            distText.setText(format("%s km",formatter.format(dist)));
            calsText.setText(cals_burned+" kcal");
            stepView.setText(totalSteps+"");
            timeText.setText(Math.round(timeWalked/(60*1000))+" mins");

        });

       
    }


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
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

            case "Male": return height*0.3937*0.415;

            case "Female": return height*0.3937*0.413;

            default:
                throw new IllegalStateException("Unexpected value: " + gender);
        }
    }

    private double distanceTravelled(Integer steps, double stride_length) {
        Log.d("distanceTravelled", steps*stride_length*2.54e-5+"");
        return steps*stride_length*2.54e-5;
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




        double caloriesBurnedPerMile = walkingFactor * (weight * 2.2);


        double stepCountMile = 5280 / step_size_feet;

        double conversationFactor = caloriesBurnedPerMile / stepCountMile;

        return stepsCount * conversationFactor;
    }
}

