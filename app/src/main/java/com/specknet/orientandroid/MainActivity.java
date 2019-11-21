package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class MainActivity extends Activity {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "FF:37:3C:B4:74:63";

    private static final String MAG_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    private Disposable scanSubscription;
    private RxBleClient rxBleClient;

    boolean connected = false;

    private Context ctx;
    private ImageView back;

    private TextView stepView;
    private LineGraphSeries<DataPoint> seriesX;
    private LineGraphSeries<DataPoint> seriesY;
    private LineGraphSeries<DataPoint> seriesZ;

    private int totalSteps;
    private int inputCounter = 0;
    private final int RC_LOCATION_AND_STORAGE = 1;
    private  ArrayList<Double> mags = new ArrayList<>();

    private StepCounter scPeak = new StepCounter(2.75,0.33333,20,10);
    private StepCounter scValley = new StepCounter(2.75,0.33333,20,10);

    private boolean stepSwitch = false;
    private long init_time = 0;
    private ArrayList<Double> gyroSmooth = new ArrayList<>();
    private ArrayList<Double> magStdList = new ArrayList<>();

    private int rising_edge_step_cache = 0;
    private int temp_stepCount;
    private int walkingFlag;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;
        GraphView graph = findViewById(R.id.graph);
        Viewport vp = graph.getViewport();
        vp.setXAxisBoundsManual(true);
        vp.setMinX(0);
        vp.setMaxX(100);
        seriesX = new LineGraphSeries<>();
        seriesX.setColor(Color.BLUE);
        seriesY = new LineGraphSeries<>();
        seriesY.setColor(Color.RED);
        seriesZ = new LineGraphSeries<>();
        seriesZ.setColor(Color.GREEN);


        graph.addSeries(seriesX);
        graph.addSeries(seriesY);
        graph.addSeries(seriesZ);

        Button reset = findViewById(R.id.reset);

        reset.setOnClickListener((View view) -> totalSteps = 0);

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

    private void runApp() {
        stepView = findViewById(R.id.steps);

        Button step_on_off_button = findViewById(R.id.onOff);
        step_on_off_button.setOnClickListener((View v) -> stepSwitch = !stepSwitch);
        back = findViewById(R.id.movingIndicator);

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

    private int isWalking(List<Double> magnitudes) {
        List<Double> slicedMags;
        final int start = (mags.size() > 10) ? mags.size() - 10 : 0;
        slicedMags = magnitudes.subList(start, magnitudes.size());
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
        if (thresh > 5) {
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
        */
    }

    public static ByteBuffer bytesToInt(final byte[] array, final int start)
    {
        final ByteBuffer buf = ByteBuffer.wrap(array); // big endian by default
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(start);
        return buf;
    }
    private void handleRawPacket(final byte[] bytes) {
        ByteBuffer buf = bytesToInt(bytes, 0);
        double mag = buf.getInt() / 1e6;
        double gyro = buf.getInt() / 1e8;
        gyroSmooth.add(gyro);
        if (gyroSmooth.size() > 3) {
            gyroSmooth.remove(0);
        }
        double sum = 0;
        for (double g : gyroSmooth) {
            sum += g;
        }
        final double averageGyro = sum / 3;
        mags.add(mag);
        final int start = (mags.size() > 10) ? mags.size() - 10 : 0;
        List<Double> mags_slice = mags.subList(start, mags.size() - 1);
        final double mag_sd = StepCounter.sd(mags_slice, StepCounter.calculateAverage(mags_slice));
        magStdList.add(mag_sd);
        int moving = this.isWalking(magStdList);
        if (moving > 0) {
            boolean peakStep = scPeak.stepDetection(averageGyro, (double) System.currentTimeMillis());
            boolean valleyStep = scValley.stepDetection(averageGyro * -1, (double) System.currentTimeMillis());
            if (moving == 1 && (peakStep || valleyStep)){
                rising_edge_step_cache += 1;
                Log.d("STEP","Cache="+rising_edge_step_cache);
            } else {
                // if 500 ms passes without a step, don't count, only count if 3 in sequqnce
                if (peakStep || valleyStep) {
                    if (walkingFlag == 1) {
                        totalSteps += 1;
                    } else {
                        init_time = System.currentTimeMillis();
                        temp_stepCount += 1;
                    }
                } else if (System.currentTimeMillis() - init_time > 1000 && walkingFlag == 0) {
                    init_time = System.currentTimeMillis();
                    temp_stepCount = 0;
                    walkingFlag = 0;
                    rising_edge_step_cache = 0;
                }
                if (temp_stepCount >= 3 && walkingFlag == 0) {
                    walkingFlag = 1;
                    totalSteps += temp_stepCount;
                    totalSteps += rising_edge_step_cache;
                    Log.d("STEP","Cache2="+rising_edge_step_cache);

                    rising_edge_step_cache = 0;
                    temp_stepCount = 0;
                }
            }
        } else {
            temp_stepCount = 0;
            walkingFlag = 0;
            rising_edge_step_cache = 0;
        }
        runOnUiThread(() -> {
            seriesY.appendData(new DataPoint(inputCounter, mag_sd),true,500);
            seriesX.appendData(new DataPoint(inputCounter, averageGyro/3),true,500);
            seriesZ.appendData(new DataPoint(inputCounter, -1*averageGyro/3),true,500);

            inputCounter+=1;
            stepView.setText(""+totalSteps);
            if (moving == 2) {
                back.setColorFilter(Color.GREEN);
            } else if (moving == 1){
                back.setColorFilter(Color.YELLOW);
            } else {
                back.setColorFilter(Color.RED);
            }
        });


    }
}
