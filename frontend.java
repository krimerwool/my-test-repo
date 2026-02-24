// cropclassification.java:

package com.example.cropdemo;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cropanalysissdk.CropSDK;
import com.example.cropanalysissdk.AnalysisResult;
import com.example.cropanalysissdk.CropDetection;
import java.util.Locale;

public class CropClassificationActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView resultTextView;
    private CropSDK cropSdk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_classification);

        imageView = findViewById(R.id.cropImageView);
        resultTextView = findViewById(R.id.cropTextView);

        try {
            cropSdk = new CropSDK(this);
        } catch (Exception e) {
            resultTextView.setText("Error: " + e.getMessage());
            return;
        }

        String imageUriString = getIntent().getStringExtra("imageUri");
        if (imageUriString != null) {
            Bitmap bitmap = loadBitmapFromUri(Uri.parse(imageUriString));
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                AnalysisResult result = cropSdk.analyze(bitmap);
                resultTextView.setText(formatProfessionalReport(result));
            }
        }
    }

    private String formatProfessionalReport(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        // 1. TERRAIN ANALYSIS

        sb.append("         TERRAIN ANALYSIS\n");


        // 🔴 FIXED: Simplified confidence display logic
        float barrenConf = Float.isNaN(result.getBarrenConfidence()) ? 0.0f : result.getBarrenConfidence();

        if (result.isBarren()) {
            sb.append("Status:       🟤 FALLOW LAND\n");
        } else {
            sb.append("Status:       🟢 CULTIVATED FIELD\n");
        }

        // barrenConfidence already represents the confidence in the detected state
        sb.append(String.format(Locale.US, "Confidence:   %.1f%%\n", barrenConf * 100));
        sb.append("\n");

        // 2. PRIMARY DETECTION RESULTS

        sb.append("      PRIMARY DETECTION RESULTS\n");


        if (result.getGridDetections().isEmpty()) {
            if (result.isBarren()) {
                sb.append(" No crops detected (Fallow land)\n");
            } else {
                sb.append(" No conclusive crop identified\n");
                sb.append("\nNote: This may indicate:\n");
                sb.append("  • Mixed vegetation\n");
                sb.append("  • Low confidence detections\n");
                sb.append("  • Image quality issues\n");
            }
        } else {
            for (CropDetection detection : result.getGridDetections()) {
                float conf = Float.isNaN(detection.getConfidence()) ? 0.0f : detection.getConfidence();

                // Add crop emoji for visual appeal
                String cropEmoji = getCropEmoji(detection.getCropName());

                sb.append(String.format(Locale.US, "%s CROP:         %s\n",
                        cropEmoji, detection.getCropName().toUpperCase()));
                sb.append(String.format(Locale.US, "   Confidence:   %.1f%%\n", conf * 100));
                sb.append(String.format(Locale.US, "   Votes:        %d\n", detection.getVotes()));
                sb.append(String.format(Locale.US, "   Location:     %s\n", detection.getLocation()));
                sb.append(String.format(Locale.US, "   Method:       %s\n", detection.getSource()));
                sb.append("\n");
            }
        }

        // 3. FULL IMAGE ANALYSIS (for debugging)

        sb.append("Full Image Detection:\n");

        CropDetection fullImage = result.getFullImageAnalysis();
        float fullConf = Float.isNaN(fullImage.getConfidence()) ? 0.0f : fullImage.getConfidence();

        sb.append(String.format(Locale.US, "  %s (%.1f%% confidence)\n",
                fullImage.getCropName(), fullConf * 100));

        sb.append(String.format(Locale.US, "⏱ Process Time: %d ms\n", result.getExecutionTimeMs()));


        return sb.toString();
    }

    /**
     * Returns an emoji for each crop type for better visual representation
     */

    private String getCropEmoji(String cropName) {
        switch (cropName.toLowerCase()) {
            case "maize":
                return "🌽";
            case "rice":
                return "🌾";
            case "soybean":
                return "🫘";
            case "sugarcane":
                return "🎋";
            default:
                return "🌱";
        }
    }

    private Bitmap loadBitmapFromUri(Uri uri) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(
                        ImageDecoder.createSource(getContentResolver(), uri)
                );
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }

            // Convert hardware bitmap to software bitmap if needed
            if (bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}

//MainActivity.java
package com.example.cropdemo;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

//import com.example.cropdemo.ml.ModelManager;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Uri> takePictureLauncher;
    private Uri cameraImageUri;

    // Launcher for picking an image from the gallery
    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImageSelected);

    // Launcher for requesting camera permissions
    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchCamera(); // Permission granted, launch the camera
                } else {
                    Toast.makeText(this, "Camera permission is required to use the camera.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Only for Testing, to remove at production
//        ModelManager.resetToAssetModel(this);
//        Till here
//        try {
//            ModelManager.installAssetModelIfNeeded(this);
//        } catch (Exception e) {
//            e.printStackTrace();
//            Toast.makeText(
//                    this,
//                    "Failed to initialize ML model",
//                    Toast.LENGTH_LONG
//            ).show();
//            finish();   // fail fast – app cannot function without model
//            return;
//        }

        // Find both buttons
        Button openCameraBtn = findViewById(R.id.open_camera_btn);
        Button uploadGalleryBtn = findViewById(R.id.upload_gallery_btn);

        // Initialize the launcher that will handle the camera result
        setupTakePictureLauncher();

        // --- Assign Correct Click Listeners ---

        // Listener for the "Open Camera" button
        openCameraBtn.setOnClickListener(v -> {
            // Check for camera permission before launching
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                // If permission is not granted, request it
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        // Listener for the "Upload from Gallery" button
        uploadGalleryBtn.setOnClickListener(v -> {
            // This correctly launches the gallery picker
            pickImageLauncher.launch("image/*");
        });
    }

    private void setupTakePictureLauncher() {
        // This launcher is called when the camera app returns a result
        takePictureLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
            if (success && cameraImageUri != null) {
                // Photo was taken successfully, now process the image
                onImageSelected(cameraImageUri);
            } else {
                Toast.makeText(this, "Failed to capture image", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchCamera() {
        try {
            // Create a temporary file to store the image
            File photoFile = File.createTempFile("JPEG_", ".jpg", getExternalFilesDir(null));
            // Get a content URI for the file using a FileProvider
            cameraImageUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".provider",
                    photoFile
            );
            // Launch the camera app and tell it to save the photo to our URI
            takePictureLauncher.launch(cameraImageUri);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to create image file for camera", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * This single method is called after an image is selected from EITHER the camera or the gallery.
     */
    private void onImageSelected(Uri imageUri) {
        if (imageUri != null) {
            // Create an intent to start the classification activity
            Intent intent = new Intent(this, CropClassificationActivity.class);
            // Pass the URI of the selected image as a string
            intent.putExtra("imageUri", imageUri.toString());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Failed to retrieve image", Toast.LENGTH_SHORT).show();
        }
    }

    // NOTE: The old onActivityResult method is no longer needed with the new launchers.
}

//MyApplication.java
// File: /app/src/main/java/com/example/cropdemo/MyApplication.java

package com.example.cropdemo;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

public class MyApplication extends Application {

   @Override
   public void onCreate() {
       super.onCreate();
       // Start the network monitoring as soon as the app is created
       startNetworkCallback();
   }

   private void startNetworkCallback() {
       ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
       NetworkRequest networkRequest = new NetworkRequest.Builder()
               .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
               .build();

       ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
           @Override
           public void onAvailable(@NonNull Network network) {
               super.onAvailable(network);
               // Use a Handler to post the Toast to the main thread
               new Handler(Looper.getMainLooper()).post(() ->
                       Toast.makeText(getApplicationContext(), "Internet connection is active.", Toast.LENGTH_SHORT).show()
               );
           }

           @Override
           public void onLost(@NonNull Network network) {
               super.onLost(network);
               new Handler(Looper.getMainLooper()).post(() ->
                       Toast.makeText(getApplicationContext(), "Internet connection lost.", Toast.LENGTH_SHORT).show()
               );
           }
       };

       cm.registerNetworkCallback(networkRequest, networkCallback);
   }
}
package com.example.cropdemo;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Looper;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;

//import com.example.cropdemo.ml.ModelUpdateScheduler;

public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        startNetworkCallback();
    }

    private void startNetworkCallback() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                // Use a Handler to post the Toast to the main thread
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(getApplicationContext(), "Internet connection is active.", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(getApplicationContext(), "Internet connection lost.", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }
}
