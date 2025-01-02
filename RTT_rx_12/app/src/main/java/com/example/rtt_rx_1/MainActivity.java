package com.example.rtt_rx_1;

import android.os.Bundle;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private WifiRttManager wifiRttManager;
    private WifiManager wifiManager;
    private TextView rttResultTextView;
    private TextView rawResultTextView;
    private Spinner frequencySpinner;    // Spinner for frequency
    private Spinner ranging_mode_spinner;  // Spinner for selecting ranging mode

    private Button startButton;
    private Button stopButton;

    private EditText labelInput;  // For user input

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static int INTERVAL = 1000;  // Default interval is 1 second (1 Hz)
    private Handler rttHandler = new Handler(Looper.getMainLooper());
    private Runnable rttRunnable;
    private boolean isRangingActive = false;  // Tracks whether ranging is active

    // File I/O objects
    private FileOutputStream fileOutputStream;
    private File rttLogFile;  // The file where results will be logged
    private static final String TAG = "MainActivity";  // For logging


//    private static final String targetBssid = "50:64:2b:9c:86:f2";

    List<String> targetBssids = new ArrayList<>();

    private int selectedRangingMode = 0; // 0 for specific BSSID, 1 for 802.11mc APs

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // the specifi BSSID
        targetBssids.add("24:29:34:e1:ef:d4");
        targetBssids.add("e4:5e:1b:a0:5e:85");
        targetBssids.add("24:29:34:e2:4c:36");  // Add as many BSSIDs as you want
        targetBssids.add("b0:e4:d5:88:16:86");


        //initialize view
        rttResultTextView = findViewById(R.id.rtt_result);
        rawResultTextView = findViewById(R.id.raw_result);
        frequencySpinner = findViewById(R.id.frequency_spinner);

        ranging_mode_spinner = findViewById(R.id.ranging_mode_spinner);

        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);

        labelInput = findViewById(R.id.label_input);  // Initialize EditText for label input

//        // Set up the spinner to change the frequency
//        frequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
//                switch (position) {
//                    case 0:  // 1 Hz
//                        INTERVAL = 1000;  // 1 second
//                        break;
//                    case 1:  // 5 Hz
//                        INTERVAL = 200;   // 0.2 seconds
//                        break;
//                    case 2:  // 10 Hz
//                        INTERVAL = 100;   // 0.1 seconds
//                        break;
//                }
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parentView) {
//                // Default to 1 Hz if nothing is selected
//                INTERVAL = 1000;
//            }
//        });
//
//        // Set up the ranging mode spinner to select ranging mode
//        ranging_mode_spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
//                selectedRangingMode = position; // 0 for specific BSSID, 1 for 802.11mc APs
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> parentView) {
//                // Default to specific BSSID if nothing is selected
//                selectedRangingMode = 0;
//            }
//        });

        // Handle Start button click
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disable the Start button
                startButton.setEnabled(false);

                // Get the selected frequency and ranging mode only when Start button is clicked
                int selectedFrequencyPosition = frequencySpinner.getSelectedItemPosition();
                switch (selectedFrequencyPosition) {
                    case 0:  // 1 Hz
                        INTERVAL = 1000;  // 1 second
                        break;
                    case 1:  // 5 Hz
                        INTERVAL = 200;   // 0.2 seconds
                        break;
                    case 2:  // 10 Hz
                        INTERVAL = 100;   // 0.1 seconds
                        break;
                }

                // Get the selected ranging mode when Start button is clicked
                selectedRangingMode = ranging_mode_spinner.getSelectedItemPosition(); // 1 for specific BSSID, 0 for 802.11mc APs

                // Start the ranging process
                startRangingProcess();

            }
        });

        // Handle Stop button click
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Enable the Start button when the Stop button is clicked
                startButton.setEnabled(true);

                // Stop the ranging process
                stopRangingProcess();
            }
        });

        // Check if the device supports Wi-Fi RTT
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            // Device does not support Wi-Fi RTT
            rttResultTextView.setText("Wi-Fi RTT is not supported on this device");
            return;
        }

        //initialize wifi manger
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            // If WifiManager is not available, show an error
            rttResultTextView.setText("Wi-Fi Manager is not available");
            return;
        }

        // Initialize WifiRttManager
        wifiRttManager = (WifiRttManager) getSystemService(WIFI_RTT_RANGING_SERVICE);
        if (wifiRttManager == null) {
            rttResultTextView.setText("Wi-Fi RTT Manager is not available");
            return;
        }

        // Check for permissions and start ranging if granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Request location permission
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }




    }

    // Handle the result of the location permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start ranging
//                startRanging();
                startRangingProcess();
            } else {
                // Permission denied, show a message to the user
                rttResultTextView.setText("Location permission is required for Wi-Fi RTT.");
            }
        }
    }


    // Start the ranging process (resume if stopped)
    private void startRangingProcess() {
        if (isRangingActive) {
            return;  // Don't start if already active
        }

//        String filenameStemp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        // Retrieve the label from the EditText
        String filenameStemp = labelInput.getText().toString().trim();

        // Create or open the log file
        try {
            rttLogFile = new File(getExternalFilesDir(null), filenameStemp.toString() + "_rtt_log.txt");
            Log.d(TAG, "Log file path: " + rttLogFile.getAbsolutePath());  // Log the file path
            Toast.makeText(this, "Log file path: " + rttLogFile.getAbsolutePath(), Toast.LENGTH_LONG).show();

            fileOutputStream = new FileOutputStream(rttLogFile, true);  // Append mode
            fileOutputStream.write("Starting Wi-Fi RTT Ranging...\n".getBytes());
            fileOutputStream.flush();  // Ensure data is written
        } catch (IOException e) {
            Log.e(TAG, "Error creating/opening file", e);  // Log any errors during file creation
            Toast.makeText(this, "No store", Toast.LENGTH_LONG).show();
        }

        // Define a Runnable that performs RTT ranging
        rttRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedRangingMode == 0) {
                    startRanging_mc();  // Perform the ranging operation to 802.11mc APs
                } else {
                    startRanging_selected(targetBssids);  // Perform ranging to specific BSSID
                }
                rttHandler.postDelayed(this, INTERVAL);  // Re-run this task after INTERVAL ms
            }
        };
        rttHandler.post(rttRunnable);  // Start the first execution
        isRangingActive = true;
    }

    // Stop the ranging process
    private void stopRangingProcess() {
        if (!isRangingActive) {
            return;  // Don't stop if not active
        }

        rttHandler.removeCallbacks(rttRunnable);  // Stop the Handler
        isRangingActive = false;

        // Close and save the file
        try {
            if (fileOutputStream != null) {
                fileOutputStream.write("Stopping Wi-Fi RTT Ranging...\n".getBytes());
                fileOutputStream.flush();  // Ensure final data is written
                fileOutputStream.close();
                fileOutputStream = null;  // Clear the reference
                Toast.makeText(this, "Store file path: " + rttLogFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                Log.d(TAG, "Store file path:" + rttLogFile.getAbsolutePath());
            }
        } catch (IOException e) {
            Toast.makeText(this, "No file store" , Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error closing file", e);  // Log any errors during file close
        }
    }

    private void startRanging_selected(List<String> targetBssids){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Perform a Wi-Fi scan and get the list of ScanResults
        wifiManager.startScan(); // Start scan to get fresh results
        List<ScanResult> scanResults = wifiManager.getScanResults();

        List<ScanResult> selectedAps = new ArrayList<>();

        // Find the ScanResult that matches each specified BSSID in targetBssids
        for (String targetBssid : targetBssids) {
            for (ScanResult result : scanResults) {
                if (result.BSSID.equals(targetBssid)) {
                    selectedAps.add(result);  // Add the matching AP to the list
                    break;
                }
            }
        }

        if (selectedAps.isEmpty()) {
            // No matching BSSID found
            rttResultTextView.setText("No matching BSSIDs found.");
            return;
        }

        // Build the RangingRequest with the selected BSSIDs
        RangingRequest.Builder builder = new RangingRequest.Builder();
        for (ScanResult selectedAp : selectedAps) {
            builder.addAccessPoint(selectedAp);  // Add each selected AP to the request
        }

        RangingRequest rangingRequest = builder.build();

        try {
            wifiRttManager.startRanging(rangingRequest, getMainExecutor(), new RangingResultCallback() {
                @Override
                public void onRangingFailure(int code) {
                    rttResultTextView.setText("Ranging failed with code: " + code);
                }

                @Override
                public void onRangingResults(@NonNull List<RangingResult> results) {
                    processRangingResults(results);  // Handle the results
                }
            });
        } catch (SecurityException e) {
            rttResultTextView.setText("Permission not granted.");
        }
//        // Perform a Wi-Fi scan and get the list of ScanResults
//        List<ScanResult> scanResults = wifiManager.getScanResults();
//
//        // Find the ScanResult that matches the pre-selected BSSID
//        ScanResult selectedAp = null;
//        for (ScanResult result : scanResults) {
//            if (result.BSSID.equals(targetBssid)) {
//                selectedAp = result;
//                break;
//            }
//        }
//        if (selectedAp == null) {
//            // No matching BSSID found
//            rttResultTextView.setText("BSSID not found: " + targetBssid);
//            return;
//        }
//
//        // Build the RangingRequest with the selected BSSID
//        RangingRequest rangingRequest = new RangingRequest.Builder()
//                .addAccessPoint(selectedAp)  // Add only the selected BSSID
//                .build();
//
//        try {
//            wifiRttManager.startRanging(rangingRequest, getMainExecutor(), new RangingResultCallback() {
//                @Override
//                public void onRangingFailure(int code) {
//                    rttResultTextView.setText("Ranging failed with code: " + code);
//                }
//
//                @Override
//                public void onRangingResults(@NonNull List<RangingResult> results) {
//                    processRangingResults(results);  // Handle the results
//                }
//            });
//        } catch (SecurityException e) {
//            rttResultTextView.setText("Permission not granted.");
//        }

    }

    private void startRanging_mc() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<ScanResult> scanResults = wifiManager.getScanResults();

        // Filter for both 802.11mc-capable APs and non-802.11mc-capable APs
        List<ScanResult> rttCapableAps = filterRttCapableAps(scanResults);

        if (rttCapableAps.isEmpty()) {
            rttResultTextView.setText("No RTT capable Access Points found");
            return;
        }


//      The original code that can only range to 802.11mc AP
        // Limit the number of APs to a maximum of 8 (or a reasonable number)
        int maxPeers = 8;
        if (rttCapableAps.size() > maxPeers) {
            rttCapableAps = rttCapableAps.subList(0, maxPeers);
        }

        RangingRequest rangingRequest = new RangingRequest.Builder()
                .addAccessPoints(rttCapableAps)
                .build();


        try {
            wifiRttManager.startRanging(rangingRequest, getMainExecutor(), new RangingResultCallback() {
                @Override
                public void onRangingFailure(int code) {
                    rttResultTextView.setText("Ranging failed with code: " + code);
                }

                @Override
                public void onRangingResults(@NonNull List<RangingResult> results) {
                    // Handle ranging results
                    processRangingResults(results);
                }
            });
        } catch (SecurityException e) {
            rttResultTextView.setText("Permission not granted.");
        }
    }

    // function to store result and show on screen
    private void processRangingResults(List<RangingResult> results) {
        StringBuilder resultBuilder = new StringBuilder();
        StringBuilder rawResultBuilder = new StringBuilder();

        // Get the current timestamp
        long timeInMillis = System.currentTimeMillis();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(timeInMillis));

        // Retrieve the label from the EditText
        String userLabel = labelInput.getText().toString().trim();

        for (RangingResult result : results) {
            if (result.getStatus() == RangingResult.STATUS_SUCCESS) {
                String apBssid = result.getMacAddress().toString();
                String apSsid = getSsidForBssid(apBssid);
                int distance = result.getDistanceMm();
                int StdDev = result.getDistanceStdDevMm();
                int Rssi = result.getRssi();
                long timeStemp = result.getRangingTimestampMillis();
                boolean mcOn = result.is80211mcMeasurement();

                resultBuilder
                        .append("Label: ").append(userLabel.isEmpty() ? "No Label" : userLabel).append(", ")
                        .append("Timestamp: ").append(timeStamp).append(", ")
                        .append("AP SSID: ").append(apSsid).append(", ")
                        .append("BSSID: ").append(apBssid).append(", ")
                        .append("Rssi: ").append(Rssi).append(", ")
                        .append("Distance: ").append(distance).append(" mm").append(", ")
                        .append("StdDev: ").append(StdDev).append(" mm").append(", ")
                        .append("timeStemp: ").append(timeStemp).append(", ")
                        .append("mcOn: ").append(mcOn).append("\n");

                rawResultBuilder.append("Timestamp: ").append(timeStamp)
                        .append(result.toString())
                        .append("\n");

                // Write the result to the log file
                writeResultToFile(resultBuilder.toString());

            } else {
                resultBuilder.append("Timestamp: ").append(timeStamp).append(", Ranging failed for one or more APs\n");
                rawResultBuilder.append("Timestamp: ").append(timeStamp).append(", Ranging failed\n");
            }
        }
        rttResultTextView.setText(resultBuilder.toString());
        rawResultTextView.setText(rawResultBuilder.toString());

        // Scroll to bottom of the TextViews
        ScrollView rttScrollView = findViewById(R.id.rtt_scrollview);
        ScrollView rawScrollView = findViewById(R.id.raw_scrollview);

        scrollToBottom(rttScrollView);
        scrollToBottom(rawScrollView);

    }

    private void scrollToBottom(final ScrollView scrollView) {
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, 0);  // Scroll to the top
            }
        });
    }

    private void writeResultToFile(String data) {
        try {
            if (fileOutputStream != null) {
                fileOutputStream.write(data.getBytes());
                fileOutputStream.flush();  // Ensure data is written to the file
            } else {
                Log.e(TAG, "FileOutputStream is null. Cannot write to file.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error writing to file", e);
            Toast.makeText(this, "Error writing to file", Toast.LENGTH_SHORT).show();
        }
    }


    private List<ScanResult> filterRttCapableAps(List<ScanResult> scanResults) {
        // Filter scan results for RTT capable APs
        return scanResults.stream()
                .filter(ScanResult::is80211mcResponder)
                .collect(Collectors.toList());
    }

    // Function to filter non-802.11mc capable APs
    private List<ScanResult> filterNon80211mcAps(List<ScanResult> scanResults) {
        // Filter scan results for APs that are NOT 802.11mc-capable
        return scanResults.stream()
                .filter(scanResult -> !scanResult.is80211mcResponder())
                .collect(Collectors.toList());
    }

    // Helper function to get the SSID for a given BSSID
    private String getSsidForBssid(String bssid) {
        List<ScanResult> scanResults = wifiManager.getScanResults();
        for (ScanResult scanResult : scanResults) {
            if (scanResult.BSSID.equals(bssid)) {
                return scanResult.SSID;  // Return SSID if BSSID matches
            }
        }
        return "Unknown SSID";  // Return this if no match is found
    }

}

