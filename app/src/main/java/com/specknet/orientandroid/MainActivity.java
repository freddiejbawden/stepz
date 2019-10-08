package com.specknet.orientandroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
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

public class MainActivity extends Activity implements AdapterView.OnItemSelectedListener {

    // test device - replace with the real BLE address of your sensor, which you can find
    // by scanning for devices with the NRF Connect App

    private static final String ORIENT_BLE_ADDRESS = "D4:A3:C2:EC:49:18";

    private static final String ORIENT_QUAT_CHARACTERISTIC = "00001526-1212-efde-1523-785feabcd125";
    private static final String ORIENT_RAW_CHARACTERISTIC = "ef680406-9b35-4933-9b10-52ffa9740042";

    private static final boolean raw = true;
    private RxBleDevice orient_device;
    private Disposable scanSubscription;
    private RxBleClient rxBleClient;
    private ByteBuffer packetData;

    //private int n = 0;
    private Long connected_timestamp = null;
    private Long capture_started_timestamp = null;
    boolean connected = false;
    private float freq = 0.f;

    private int counter = 0;
    private CSVWriter writer;
    private File path;
    private File file;
    private boolean logging = false;

    private Button start_button;
    private Button stop_button;
    private Button step_on_off_button;
    private Context ctx;
    private TextView captureTimetextView;
    private TextView accelTextView;
    private TextView gyroTextView;
    private TextView freqTextView;
    private LinearLayout back;


    private TextView stepView;
    private LineGraphSeries<DataPoint> seriesX;
    private GraphView graph;
    private int inputCounter = 0;
    private final int RC_LOCATION_AND_STORAGE = 1;
    private  ArrayList<Double> mags = new ArrayList<Double>();
    private int magLimit = 10;
    private double mean_mag = 1;
    private  double std_mag = 0.1;
    private boolean calculateStd = false;
    private StepCounter sc = new StepCounter(2.75,0.33333,20,10);
    private boolean stepSwitch = false;

    private int numPeaks = 0;
    private PointsGraphSeries<DataPoint> peakDatapoints;


    /**
     *  Returns the mean of a List<Double
     * @param marks A list of values
     * @return The mean
     */
    public static double calculateAverage(List<Double> marks) {
        double sum = 0;
        if(!marks.isEmpty()) {
            for (Double mark : marks) {
                sum += mark;
            }
            return sum / marks.size();
        }
        return sum;
    }

    /**
     *  Generic function for finding the standard devation of a List<Double>
     *
     * @param table     A list of values
     * @param mean_a    The mean of the values
     * @return          The standard deviation
     */
    public static double sd (List<Double> table, Double mean_a)
    {
        if (table.size() == 0) {
            return 0;
        }
        // Step 1:
        double temp = 0;

        for (int i = 0; i < table.size(); i++)
        {
            Double val = table.get(i);

            // Step 2:
            double squrDiffToMean = Math.pow(val - mean_a, 2);

            // Step 3:
            temp += squrDiffToMean;
        }

        // Step 4:
        double meanOfDiffs = (double) temp / (double) (table.size());

        // Step 5:
        return Math.sqrt(meanOfDiffs);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;
        graph = (GraphView) findViewById(R.id.graph);
        Viewport vp = graph.getViewport();
        vp.setXAxisBoundsManual(true);
        vp.setMinX(0);
        vp.setMaxX(100);
        vp.setScalable(true);
        stepView = findViewById(R.id.steps);
        seriesX = new LineGraphSeries<>();
        seriesX.setColor(Color.BLUE);
        graph.addSeries(seriesX);
        peakDatapoints = new PointsGraphSeries<>();
        graph.addSeries(peakDatapoints);
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
        path = Environment.getExternalStorageDirectory();
        step_on_off_button = (Button) findViewById(R.id.onOff);
        step_on_off_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stepSwitch = !stepSwitch;
            }
        });
        start_button = findViewById(R.id.start_button);
        stop_button = findViewById(R.id.stop_button);
        captureTimetextView = findViewById(R.id.captureTimetextView);
        accelTextView = findViewById(R.id.accelTextView);
        gyroTextView = findViewById(R.id.gyroTextView);
        freqTextView = findViewById(R.id.freqTextView);





        List<String> position_list = new ArrayList<String>();
        position_list.add("---");
        position_list.add("Wrist");
        position_list.add("Upper arm");
        position_list.add("Torso");
        position_list.add("Upper Leg");
        position_list.add("Lower Leg");
        position_list.add("Foot");



        start_button.setOnClickListener(v-> {
            logging = true;
            capture_started_timestamp = System.currentTimeMillis();
            counter = 0;
            Toast.makeText(this, "Start logging",
                    Toast.LENGTH_SHORT).show();
            stop_button.setEnabled(true);
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

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position,
                               long id) {
        return;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    private void connectToOrient(String addr) {
        orient_device = rxBleClient.getBleDevice(addr);
        String characteristic;
        if (raw) characteristic = ORIENT_RAW_CHARACTERISTIC; else characteristic = ORIENT_QUAT_CHARACTERISTIC;

        orient_device.establishConnection(false)
                .flatMap(rxBleConnection -> rxBleConnection.setupNotification(UUID.fromString(characteristic)))
                .doOnNext(notificationObservable -> {
                    // Notification has been set up
                })
                .flatMap(notificationObservable -> notificationObservable) // <-- Notification has been set up, now observe value changes.
                .subscribe(
                        bytes -> {
                            //n += 1;
                            // Given characteristic has been changes, here is the value.

                            //Log.i("OrientAndroid", "Received " + bytes.length + " bytes");
                            if (!connected) {
                                connected = true;
                                connected_timestamp = System.currentTimeMillis();
                                runOnUiThread(() -> {
                                                               Toast.makeText(ctx, "Receiving sensor data",
                                                                       Toast.LENGTH_SHORT).show();
                                    start_button.setEnabled(true);
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
    private void handleRawPacket(final byte[] bytes) {
        long ts = System.currentTimeMillis();
        packetData.clear();
        packetData.put(bytes);
        packetData.position(0);

        float accel_x = packetData.getShort() / 1024.f;  // integer part: 6 bits, fractional part 10 bits, so div by 2^10
        float accel_y = packetData.getShort() / 1024.f;
        float accel_z = packetData.getShort() / 1024.f;

        float gyro_x = packetData.getShort() / 32.f;  // integer part: 11 bits, fractional part 5 bits, so div by 2^5
        float gyro_y = packetData.getShort() / 32.f;
        float gyro_z = packetData.getShort() / 32.f;

        if (logging) {

            double mag = Math.sqrt(Math.pow(accel_x, 2) + Math.pow(accel_y, 2) + Math.pow(accel_z, 2));
            mags.add(mag);

            back = findViewById(R.id.background);

            if (isWalking(mags) && mags.size() > 10) {
                if (stepSwitch) {
                    Log.d("STARTSTOP","walking");
                    // feed
                    calculateStd = true;
                    back.setBackgroundColor(Color.GREEN);

                    sc.stepDetection(mag, (double) System.currentTimeMillis());
                    Log.d("STEP", sc.getPeaks() + "");
                }
            } else {
                back.setBackgroundColor(Color.RED);
                Log.d("STARTSTOP","stopped");
            }

            Log.d("STEP", ""+sc.getCount());
            stepView.setText("Steps: " + sc.getCount());
            if (counter % 4 == 0 && stepSwitch) {
                seriesX.appendData(new DataPoint(inputCounter, mag),true,100);

                inputCounter+=1;
            }

            if (counter % 12 == 0) {
                long elapsed_time = System.currentTimeMillis() - capture_started_timestamp;
                int total_secs = (int)elapsed_time / 1000;
                int s = total_secs % 60;
                int m = total_secs / 60;

                String m_str = Integer.toString(m);
                if (m_str.length() < 2) {
                    m_str = "0" + m_str;
                }

                String s_str = Integer.toString(s);
                if (s_str.length() < 2) {
                    s_str = "0" + s_str;
                }


                Long elapsed_capture_time = System.currentTimeMillis() - capture_started_timestamp;
                float connected_secs = elapsed_capture_time / 1000.f;
                freq = counter / connected_secs;

                String time_str = m_str + ":" + s_str;

                String accel_str = "Accel: (" + accel_x + ", " + accel_y + ", " + accel_z + ")";
                String gyro_str = "Gyro: (" + gyro_x + ", " + gyro_y + ", " + gyro_z + ")";
                String freq_str = "Freq: " + freq;
                Log.d("ACC", accel_str);

                runOnUiThread(() -> {
                    captureTimetextView.setText(time_str);
                    accelTextView.setText(accel_str);
                    gyroTextView.setText(gyro_str);
                    freqTextView.setText(freq_str);
                });
            }

            counter += 1;
        }
    }
}
