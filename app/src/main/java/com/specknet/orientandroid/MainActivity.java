package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
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

import static java.lang.Integer.toUnsignedLong;

public class MainActivity extends Activity implements SensorEventListener {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "FF:37:3C:B4:74:63";

    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "0000a001-0000-1000-8000-00805f9b34fb";

    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    //private int n = 0;

    boolean connected = false;
    private int counter = 0;


    private Button step_on_off_button;
    private Button reset;
    private Context ctx;
    private ImageView back;


    private TextView stepView;
    private LineGraphSeries<DataPoint> seriesX;
    private LineGraphSeries<DataPoint> seriesY;

    private GraphView graph;
    private int inputCounter = 0;
    private int inputCounter2 = 0;
    private final int RC_LOCATION_AND_STORAGE = 1;
    private  ArrayList<Double> mags = new ArrayList<Double>();
    private StepCounter sc = new StepCounter(2.75,0.33333,20,10);
    private StepCounter scGyro = new StepCounter(2.75,0.2,20,10);

    private boolean stepSwitch = false;
    private PointsGraphSeries<DataPoint> peakDatapoints;
    private long init_time = 0;
    private long time_passed = 0;

    private SensorManager sensorManager;
    private Sensor accSensor;
    private Sensor gyroSensor;
    private ArrayList<Double> gyroSmooth = new ArrayList<>();
    private ArrayList<Double> magSmooth = new ArrayList<>();

    private int walkingFlag;
    private long timeSinceSwitch = 0L;
    private  long switchThresh = 1000L;
    private long switchStartTime = 0L;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this,gyroSensor,SensorManager.SENSOR_DELAY_UI);

        sensorManager.registerListener(this,accSensor,SensorManager.SENSOR_DELAY_UI);
        ctx = this;
        graph = (GraphView) findViewById(R.id.graph);
        Viewport vp = graph.getViewport();
        vp.setXAxisBoundsManual(true);
        vp.setMinX(0);
        vp.setMaxX(100);
        seriesX = new LineGraphSeries<>();
        seriesX.setColor(Color.BLUE);
        seriesY = new LineGraphSeries<>();
        seriesY.setColor(Color.RED);

        graph.addSeries(seriesX);
        graph.addSeries(seriesY);

        peakDatapoints = new PointsGraphSeries<>();
        graph.addSeries(peakDatapoints);
        reset = findViewById(R.id.reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sc.resetSteps();
                scGyro.resetSteps();
            }
        });

        getPermissions();
    }
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
    }
    public double calcMagFromVals(float[] values){
        double sum = 0.0;
        for (float v : values){
            sum += Math.pow((((double) v)),2.0);
        }
        return Math.sqrt(sum)-9.8;
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double mag = calcMagFromVals(event.values);
            magSmooth.add(mag);
            if (magSmooth.size() > 3) {
                magSmooth.remove(0);
            }
            double sum = 0;
            for (double m : magSmooth) {
                sum += m;
            }

            mags.add(mag);
            int new_walk_d = this.isWalking(mags);
            if (new_walk_d != walkingFlag) {
                switchStartTime = System.currentTimeMillis();
                timeSinceSwitch = 0L;
            } else {
                timeSinceSwitch += System.currentTimeMillis() - switchStartTime;
            }
            walkingFlag = new_walk_d;
            if (walkingFlag == 2 && timeSinceSwitch > switchThresh) {
                seriesX.appendData(new DataPoint(inputCounter,sum/magSmooth.size()),true,500);
                inputCounter++;
                sc.stepDetection(sum/magSmooth.size(), (double) System.currentTimeMillis());
            }

            Log.d("STEP", "WALKING: " + walkingFlag);

        } else {
            double axisX = (double) event.values[0];
            double axisY = (double) event.values[1];
            double axisZ = (double) event.values[2];
            double omegaMagnitude = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            gyroSmooth.add(axisX);
            if (gyroSmooth.size() > 3) {
                gyroSmooth.remove(0);
            }
            double sum = 0;
            for (double g : gyroSmooth) {
                sum += g;
            }
            if (walkingFlag == 1 || walkingFlag == 0) {
                seriesX.appendData(new DataPoint(inputCounter,sum/gyroSmooth.size()),true,500);
                inputCounter++;
                if (walkingFlag == 1 && timeSinceSwitch > switchThresh){
                    scGyro.stepDetection(sum/gyroSmooth.size(), (double) System.currentTimeMillis());

                }
            }
            //seriesX.appendData(new DataPoint(inputCounter, sum/gyroSmooth.size()),true,500);
            //inputCounter++;
        }
        runOnUiThread(() -> {
            stepView.setText(""+(sc.getCount()+scGyro.getCount()));
            if (walkingFlag == 2 && timeSinceSwitch > switchThresh) {
                back.setColorFilter(Color.GREEN);
            } else if (walkingFlag == 1 && timeSinceSwitch > switchThresh) {
                back.setColorFilter(Color.YELLOW);
            } else if (timeSinceSwitch > switchThresh && walkingFlag == 0)  {
                back.setColorFilter(Color.RED);
            }
        });


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
        step_on_off_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stepSwitch = !stepSwitch;
            }
        });
        back = findViewById(R.id.movingIndicator);




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
    private int isWalking(List<Double> magnitudes) {
        double mag_th_1 = 0.6;
        double mag_th_2 = 2.0;

        boolean is_under = true;
        List<Double> slicedMags = magnitudes;
        if (magnitudes.size() > 10) {
            slicedMags = magnitudes.subList(magnitudes.size() - 10, magnitudes.size());
        }
        boolean shuffling = false;
        for (double mag : slicedMags) {
            if (mag > mag_th_2) {
                return 2;
            }
            if (mag < mag_th_2 && mag > mag_th_1) {
                shuffling = true;
            }
        }
        return (shuffling) ? 1 : 0;
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
            time_passed =(System.currentTimeMillis() - init_time);
            Log.d("TimePassed", time_passed/1e3+"");
        }
        init_time = System.currentTimeMillis();
        Log.d("STEP", "dat");
        long mag_l = bytesToInt(bytes,0);
        double mag = ((float) mag_l)/1e6;
        Log.d("STEP",""+mag);

        mags.add(mag);
        final boolean moving = isWalking(mags) == 2 && mags.size() > 10;
        if (moving) {
            if (stepSwitch) {
                sc.stepDetection(mag, (double) System.currentTimeMillis());
                Log.d("STEP", sc.getPeaks() + "");

            }
        } else {
            Log.d("STARTSTOP","stopped");
        }

        Log.d("STEP", ""+sc.getCount());
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
}
