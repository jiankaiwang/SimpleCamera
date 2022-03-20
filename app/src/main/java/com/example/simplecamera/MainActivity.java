package com.example.simplecamera;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener,
        ImageAnalysis.Analyzer {

    Button btnTakePicture;
    TextView useCamera, useVideo;
    ImageView btnSwitchCamera, viewData;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private int LENS_SELECTOR = CameraSelector.LENS_FACING_BACK;
    private LinearLayout linearLayout;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private ImageAnalysis imageAnalysis;

    private static final int CAMERA_REQUEST_CODE = 10;
    private static final int AUDIO_REQUEST_CODE = 10;

    private String[] functions_name = {"PICTURE", "VIDEO"};
    private int functions_id = 0;
    private ArrayList<TextView> functions_text = new ArrayList<TextView>();

    private File imgDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SimpleCamera");
    private File videoDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SimpleCamera");

    // 0 for stop, 1 for start
    private int video_state = 0;

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_REQUEST_CODE);
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            int REQUEST_CODE_PERMISSION_STORAGE = 100;
            String[] permissions = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            for (String str : permissions) {
                if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                    this.requestPermissions(permissions, REQUEST_CODE_PERMISSION_STORAGE);
                    return;
                }
            }
        }
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                AUDIO_REQUEST_CODE
                );
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        btnTakePicture = findViewById(R.id.btnTakePicture);
        viewData = findViewById(R.id.viewData);
        useCamera = findViewById(R.id.useCamera);
        useVideo = findViewById(R.id.useVideo);
        previewView = findViewById(R.id.previewView);
        linearLayout = findViewById(R.id.linearLayout);

        if (!hasCameraPermission()) {
            requestPermission();
        }

        if (!hasStoragePermission()) {
            requestStoragePermission();
        }

        if (!hasAudioPermission()) {
            requestAudioPermission();
        }

        functions_text.add(useCamera);
        functions_text.add(useVideo);

        useCamera.setOnClickListener(this);
        useVideo.setOnClickListener(this);
        btnSwitchCamera.setOnClickListener(this);
        btnTakePicture.setOnClickListener(this);
        linearLayout.setOnTouchListener(new OnSwipeTouchListener(this) {
            @Override
            public void onSwipeRight() {
                if(video_state == 1) { return ; }
                if (functions_id > 0) {
                    swipeRight();
                }
            }

            @Override
            public void onSwipeLeft() {
                if(video_state == 1) { return ; }
                if (functions_id < functions_name.length) {
                    swipeLeft();
                }
            }

            @Override
            public void onSwipeTop() { }

            @Override
            public void onSwipeBottom() { }
        });

        // include startProcessCamera()
        toSelectFunction();

        // show the image gallery
        searchAndSetData();
    }

    private void toSelectFunction() {
        btnTakePicture.setText(functions_name[functions_id]);
        startProcessCamera();
        ColorStateList csl = null;
        for(int i = 0; i < functions_text.size(); i++) {
            if(i == functions_id) {
                csl = getResources().getColorStateList(R.color.white);
                functions_text.get(i).setTextColor(csl);
            } else {
                csl = getResources().getColorStateList(R.color.gray);
                functions_text.get(i).setTextColor(csl);
            }
        }
    }

    private void swipeRight() {
        functions_id -= 1;
        toSelectFunction();
    }

    private void swipeLeft() {
        functions_id += 1;
        toSelectFunction();
    }

    private void startUsingCamera() {
        functions_id = 0;
        toSelectFunction();
    }

    private void startUsingVideo() {
        functions_id = 1;
        toSelectFunction();
    }

    protected void startProcessCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, getExecutor());
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {

        // camera selector use case
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(LENS_SELECTOR)
                .build();

        // preview use case
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // image capture use case
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        // video capture use case
        videoCapture = new VideoCapture.Builder().setVideoFrameRate(30).build();

        // image analysis use case
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // make sure all occupying are unbinding
        cameraProvider.unbindAll();

        // bind all above to the life cycle
        switch(functions_id) {
            default:
            case 1:
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, videoCapture);
                break;
            case 0:
                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageCapture, imageAnalysis);
                break;
        }
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btnSwitchCamera:
                switchCamera();
                break;
            case R.id.btnTakePicture:
                switch(functions_id) {
                    case 0:
                        createPicture();
                        break;
                    case 1:
                        if (video_state == 0) {
                            btnTakePicture.setText(R.string.video_activity_stop);
                            video_state = 1;
                            recordVideo();
                        } else {
                            btnTakePicture.setText(R.string.video_activity);
                            video_state = 0;
                            videoCapture.stopRecording();
                        }
                        break;
                }
                break;
            case R.id.useCamera:
                startUsingCamera();
                break;
            case R.id.useVideo:
                startUsingVideo();
                break;
        }
    }

    private void switchCamera() {
        if(LENS_SELECTOR == CameraSelector.LENS_FACING_BACK) {
            LENS_SELECTOR = CameraSelector.LENS_FACING_FRONT;
        } else {
            LENS_SELECTOR = CameraSelector.LENS_FACING_BACK;
        }

        startProcessCamera();
    }

    private void searchAndSetData() {
        String[] picNames;
        String[] videoNames;
        File picFile = new File(imgDir.getAbsolutePath());
        picNames = picFile.list();
        File videoFile = new File(videoDir.getAbsolutePath());
        videoNames = videoFile.list();

        Arrays.sort(picNames);
        Arrays.sort(videoNames);

        ArrayList<String> fileNames = new ArrayList<String>();
        if(picNames.length > 0) {
            fileNames.add(picNames[picNames.length-1]);
        }
        if(videoNames.length > 0) {
            fileNames.add(videoNames[videoNames.length-1]);
        }
        Collections.sort(fileNames, Collections.reverseOrder());

        File filePath = null;
        if(fileNames.size() > 0) {
            String ext_name = FilenameUtils.getExtension(fileNames.get(0));
            switch(ext_name) {
                case "jpeg":
                case "jpg":
                    filePath = new File(imgDir, fileNames.get(0));
                    setImageDataView(filePath.getAbsolutePath());
                    break;
                case "mp4":
                    filePath = new File(videoDir, fileNames.get(0));
                    setVideoDataView(filePath.getAbsolutePath());
                    break;
            }
        } else {
            // the default png if no data available
            String uri = "@drawable/gallery";
            int imageResource = getResources().getIdentifier(uri, null, getPackageName());
            Drawable res = getResources().getDrawable(imageResource);
            viewData.setImageDrawable(res);
        }
    }

    @SuppressLint("NewApi")
    private Bitmap createImageThumbNail(String path) {
        return ThumbnailUtils.createImageThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
    }

    private void setImageDataView(String abs_file_path) {
        File file = new File(abs_file_path);
        if(!file.exists()) { return; }
        Bitmap bitmap = createImageThumbNail(abs_file_path);
        viewData.setImageBitmap(bitmap);
    }

    private Bitmap createVideoThumbNail(String path){
        return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);
    }

    private void setVideoDataView(String abs_file_path) {
        File file = new File(abs_file_path);
        if(!file.exists()) { return; }
        Bitmap bitmap = createVideoThumbNail(abs_file_path);
        viewData.setImageBitmap(bitmap);
    }

    private void createPicture() {
        if(! imgDir.exists()) {
            imgDir.mkdir();
        }
        String timestamp = String.valueOf(new Date().getTime());
        File file = new File(imgDir, timestamp + ".jpg");

        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(file).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
//                        Toast.makeText(MainActivity.this, "The image has been saved successfully.", Toast.LENGTH_SHORT).show();
//                        Log.d("OnImageSaved", file.getAbsolutePath());
                        setImageDataView(file.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Error on saving the image: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        if (videoCapture != null) {

            if(! videoDir.exists()) {
                videoDir.mkdir();
            }
            String timestamp = String.valueOf(new Date().getTime());
            File file = new File(videoDir, timestamp + ".mp4");

            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
//                    requestAudioPermission();
                    return;
                }

                videoCapture.startRecording(
                        new VideoCapture.OutputFileOptions.Builder(file).build(),
                        getExecutor(),
                        new VideoCapture.OnVideoSavedCallback() {
                            @Override
                            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
//                                Toast.makeText(MainActivity.this, "The video has been saved successfully.", Toast.LENGTH_SHORT).show();
//                                Log.d("onVideoSaved", file.getAbsolutePath());
                                setVideoDataView(file.getAbsolutePath());
                            }

                            @Override
                            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                                Toast.makeText(MainActivity.this, "The video can't be saved. " + message, Toast.LENGTH_LONG).show();
                            }
                        }
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        // preprocessing the image
        image.close();
    }

}
