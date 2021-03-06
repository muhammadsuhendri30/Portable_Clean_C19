/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.examples.detection.Controler.Sensor;
import org.tensorflow.lite.examples.detection.Server.ApiConfig;
import org.tensorflow.lite.examples.detection.Server.AppConfig;
import org.tensorflow.lite.examples.detection.Server.Koneksi_RMQ;
import org.tensorflow.lite.examples.detection.Server.MyRmq;
import org.tensorflow.lite.examples.detection.Session.SharedPrefManager;
import org.tensorflow.lite.examples.detection.View.Mysensor;
import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.examples.detection.utils.Utils;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import pub.devrel.easypermissions.EasyPermissions;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static androidx.core.graphics.TypefaceCompatUtil.getTempFile;
import static org.tensorflow.lite.examples.detection.Session.SharedPrefManager.Sp_gambar;
import static org.tensorflow.lite.examples.detection.Session.SharedPrefManager.Sp_keterangan;
import static org.tensorflow.lite.examples.detection.env.ImageUtils.saveBitmap;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener,MyRmq, Mysensor {
  private static final Logger LOGGER = new Logger();
//  Koneksi_RMQ rmq
  Koneksi_RMQ rmq;
  String mediaPath1;
  String[] mediaColumns = {MediaStore.Video.Media._ID};
//  Sensor sensordata;
//  sensordata= new Sensor(this);
org.tensorflow.lite.examples.detection.Controler.Sensor sensor;
  // Configuration values for the prepackaged SSD model.
  //private static final int TF_OD_API_INPUT_SIZE = 300;
  //private static final boolean TF_OD_API_IS_QUANTIZED = true;
  //private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
  //private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

  //private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  // Face Mask
  private static final int TF_OD_API_INPUT_SIZE = 224;
  private static final boolean TF_OD_API_IS_QUANTIZED = false;
  private static final String TF_OD_API_MODEL_FILE = "model_masker_detector.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labels.txt";

  private static final DetectorMode MODE = DetectorMode.TF_OD_API;
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
  private static final boolean MAINTAIN_ASPECT = false;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(800, 1720);
  //private static final int CROP_SIZE = 320;
  //private static final Size CROP_SIZE = new Size(320, 320);



  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;
  OverlayView trackingOverlay;
  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private BorderedText borderedText;

  // Face detector
  private FaceDetector faceDetector;

  // here the preview image is drawn in portrait way
  private Bitmap portraitBmp = null;
  // here the face is cropped and drawn
  private Bitmap faceBmp = null;
  SharedPrefManager sharedPrefManager;
  TextureView textureView;
  String mFilePath;
  private static int RESULT_LOAD_IMG = 1;
  String imgpath,storedpath;
  ImageView myImage;
  SharedPreferences sp;
  String Keterangan;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);


    // Real-time contour detection of multiple faces
    FaceDetectorOptions options =
            new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                    .build();


    FaceDetector detector = FaceDetection.getClient(options);

    faceDetector = detector;
    sharedPrefManager=new SharedPrefManager(this);
    sensor=new Sensor(this);
    if (ContextCompat.checkSelfPermission(DetectorActivity.this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA},
              50); }



    //checkWritePermission();

  }

  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);


    try {
      detector =
              TFLiteObjectDetectionAPIModel.create(
                      getAssets(),
                      TF_OD_API_MODEL_FILE,
                      TF_OD_API_LABELS_FILE,
                      TF_OD_API_INPUT_SIZE,
                      TF_OD_API_IS_QUANTIZED);
      //cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
              Toast.makeText(
                      getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    int screenOrientation = getScreenOrientation();
    sensorOrientation = rotation - screenOrientation;
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);
    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);




    int targetW, targetH;
    if (sensorOrientation == 90 || sensorOrientation == 270) {
      targetH = previewWidth;
      targetW = previewHeight;
    }
    else {
      targetW = previewWidth;
      targetH = previewHeight;
    }
    int cropW = (int) (targetW / 2.0);
    int cropH = (int) (targetH / 2.0);

    croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

    portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
    faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

    frameToCropTransform =
            ImageUtils.getTransformationMatrix(
                    previewWidth, previewHeight,
                    cropW, cropH,
                    sensorOrientation, MAINTAIN_ASPECT);

//    frameToCropTransform =
//            ImageUtils.getTransformationMatrix(
//                    previewWidth, previewHeight,
//                    previewWidth, previewHeight,
//                    sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);



    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    textureView=(TextureView)findViewById(R.id.texture);
    myImage=(ImageView)findViewById(R.id.imageviewTest);
    sp=getSharedPreferences("setback", MODE_PRIVATE);
    myImage.setVisibility(View.GONE);
    trackingOverlay.addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                tracker.draw(canvas);
                if (isDebug()) {
                  tracker.drawDebug(canvas);
                }
              }
            });

    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }


  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      saveBitmap(croppedBitmap);
    }

    InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
    faceDetector
            .process(image)
            .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
              @Override
              public void onSuccess(List<Face> faces) {
                if (faces.size() == 0) {
                  updateResults(currTimestamp, new LinkedList<>());
                  return;
                }
                runInBackground(
                        new Runnable() {
                          @Override
                          public void run() {
                            onFacesDetected(currTimestamp, faces);
                          }
                        });
              }

            });


  }

  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void Berhasil(String message) {

  }

  @Override
  public void Gagal() {

  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  private enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> detector.setUseNNAPI(isChecked));
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }


  // Face Mask Processing
  private Matrix createTransform(
          final int srcWidth,
          final int srcHeight,
          final int dstWidth,
          final int dstHeight,
          final int applyRotation) {

    Matrix matrix = new Matrix();
    if (applyRotation != 0) {
      if (applyRotation % 90 != 0) {
        LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
      }

      // Translate so center of image is at origin.
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

      // Rotate around origin.
      matrix.postRotate(applyRotation);
    }

//        // Account for the already applied rotation, if any, and then determine how
//        // much scaling is needed for each axis.
//        final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;
//
//        final int inWidth = transpose ? srcHeight : srcWidth;
//        final int inHeight = transpose ? srcWidth : srcHeight;

    if (applyRotation != 0) {

      // Translate back from origin centered reference to destination frame.
      matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
    }

    return matrix;

  }

  private void updateResults(long currTimestamp, final List<Classifier.Recognition> mappedRecognitions) {

    tracker.trackResults(mappedRecognitions, currTimestamp);
    trackingOverlay.postInvalidate();
    computingDetection = false;


    runOnUiThread(
            new Runnable() {
              @Override
              public void run() {
                showFrameInfo(previewWidth + "x" + previewHeight);
                showCropInfo(croppedBitmap.getWidth() + "x" + croppedBitmap.getHeight());
                showInference(lastProcessingTimeMs + "ms");
              }
            });

  }

  private void onFacesDetected(long currTimestamp, List<Face> faces) {

    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
    final Canvas canvas = new Canvas(cropCopyBitmap);
    final Paint paint = new Paint();
    paint.setColor(Color.RED);
    paint.setStyle(Style.STROKE);
    paint.setStrokeWidth(2.0f);

    float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
    switch (MODE) {
      case TF_OD_API:
        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
        break;
    }

    final List<Classifier.Recognition> mappedRecognitions =
            new LinkedList<Classifier.Recognition>();


    //final List<Classifier.Recognition> results = new ArrayList<>();

    // Note this can be done only once
    int sourceW = rgbFrameBitmap.getWidth();
    int sourceH = rgbFrameBitmap.getHeight();
    int targetW = portraitBmp.getWidth();
    int targetH = portraitBmp.getHeight();
    Matrix transform = createTransform(
            sourceW,
            sourceH,
            targetW,
            targetH,
            sensorOrientation);
    final Canvas cv = new Canvas(portraitBmp);

    // draws the original image in portrait mode.
    cv.drawBitmap(rgbFrameBitmap, transform, null);

    final Canvas cvFace = new Canvas(faceBmp);

    boolean saved = false;

    for (Face face : faces) {

      LOGGER.i("FACE" + face.toString());
//      Toast.makeText(this, ""+face.toString(), Toast.LENGTH_SHORT).show();

      LOGGER.i("Running detection on face " + currTimestamp);
//      Toast.makeText(this, ""+currTimestamp, Toast.LENGTH_SHORT).show();

      //results = detector.recognizeImage(croppedBitmap);


      final RectF boundingBox = new RectF(face.getBoundingBox());

      //final boolean goodConfidence = result.getConfidence() >= minimumConfidence;
      final boolean goodConfidence = true; //face.get;
      if (boundingBox != null && goodConfidence) {

        // maps crop coordinates to original
        cropToFrameTransform.mapRect(boundingBox);

        // maps original coordinates to portrait coordinates
        RectF faceBB = new RectF(boundingBox);
        transform.mapRect(faceBB);

        // translates portrait to origin and scales to fit input inference size
        //cv.drawRect(faceBB, paint);
        float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
        float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
        Matrix matrix = new Matrix();
        matrix.postTranslate(-faceBB.left, -faceBB.top);
        matrix.postScale(sx, sy);

        cvFace.drawBitmap(portraitBmp, matrix, null);


        String label = "";
        float confidence = -1f;
        Integer color = Color.BLUE;

        final long startTime = SystemClock.uptimeMillis();
        final List<Classifier.Recognition> resultsAux = detector.recognizeImage(faceBmp);
        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
        rmq=new Koneksi_RMQ(this);

        if (resultsAux.size() > 0) {

          Classifier.Recognition result = resultsAux.get(0);
          float conf = result.getConfidence();
          if (conf >= 0.6f) {
            confidence = conf;
            label = result.getTitle();
//            Toast.makeText(this, ""+label, Toast.LENGTH_SHORT).show();

            if (label.equals("no mask")){
              String Sn="40:f5:20:2e:4f:3f";
              String Queue="mqtt-subscription-"+Sn+"qos0";
              String pesan="1000#1000";
              Keterangan="Tidak Menggunakan Masker";
              rmq.setupConnectionFactory();
              rmq.publish(pesan,Queue);
              getBitmap(textureView);

            }else if (label.equals("mask")){
              String Sn="40:f5:20:2e:4f:3f";
              String Queue="mqtt-subscription-"+Sn+"qos0";
              String pesan="0000#1000";
              Keterangan="Menggunakan Masker";
              rmq.setupConnectionFactory();
              rmq.publish(pesan,Queue);
              getBitmap(textureView);

            }
            if (result.getId().equals("0")) {
              color = Color.GREEN;
//              String Sn="8c:aa:b5:0e:35:f9";
//              String Queue="mqtt-subscription-"+Sn+"qos0";
//              String pesan="1";
//              rmq.setupConnectionFactory();
//              rmq.publish(pesan,Queue);
            } else {
              color = Color.RED;
//              String Sn="8c:aa:b5:0e:35:f9";
//              String Queue="mqtt-subscription-"+Sn+"qos0";
//              String pesan="0";
//              rmq.setupConnectionFactory();
//              rmq.publish(pesan,Queue);
            }
          }
        }

        if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

          // camera is frontal so the image is flipped horizontally
          // flips horizontally
          Matrix flip = new Matrix();
          if (sensorOrientation == 90 || sensorOrientation == 270) {
            flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
          }
          else {
            flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
          }
          //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
          flip.mapRect(boundingBox);

        }

        final Classifier.Recognition result = new Classifier.Recognition(
                "0", label, confidence, boundingBox);

        result.setColor(color);
        result.setLocation(boundingBox);
        mappedRecognitions.add(result);


      }


    }

    //    if (saved) {
//      lastSaved = System.currentTimeMillis();
//    }

    updateResults(currTimestamp, mappedRecognitions);


  }
  private void SimpanGambar() {
//
//    ProgressDialog progressDialog=new ProgressDialog(DetectorActivity.this);
//    progressDialog.setMessage("Loading...");
//    progressDialog.show();
//    Toast.makeText(this, "Jadian yok", Toast.LENGTH_SHORT).show();
    File file = new File(String.valueOf(mediaPath1));
//    Toast.makeText(this, storedpath, Toast.LENGTH_SHORT).show();
    // Parsing any Media type file
    RequestBody requestBody = RequestBody.create(MediaType.parse("*/*"), file);
    MultipartBody.Part fileToUpload = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
    RequestBody filename = RequestBody.create(MediaType.parse("text/plain"), file.getName());
    ApiConfig getResponse = AppConfig.getRetrofit().create(ApiConfig.class);
    Call<ResponseBody> call = getResponse.uploadFile(fileToUpload, filename);
    call.enqueue(new Callback<ResponseBody>() {
      @Override
      public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
//                Toast.makeText(Input_Datamenu.this, ""+response, Toast.LENGTH_SHORT).show();
        ResponseBody responseBody=response.body();
        Log.v("Response",responseBody.toString());
//        Toast.makeText(DetectorActivity.this, response.body().toString(), Toast.LENGTH_SHORT).show();
        if (response.isSuccessful()){
          try {
            JSONObject jsonRESULTS = new JSONObject(response.body().string());
//            Toast.makeText(DetectorActivity.this, jsonRESULTS.getString, Toast.LENGTH_SHORT).show();
            Log.d("Json", String.valueOf(response.body().toString()));
            if (jsonRESULTS.getString("success").equals("true")){
              String pesan_login=jsonRESULTS.getString("message");
              String Nama_Gambar=jsonRESULTS.getString("tmp_name");
//              Toast.makeText(getApplication(), ""+pesan_login, Toast.LENGTH_SHORT).show();
              sharedPrefManager.saveSPString(Sp_gambar, Nama_Gambar);
              sharedPrefManager.saveSPString(Sp_keterangan,Keterangan);
//              Simpan(mac,suhu,keterangan,gambar);
//              progressDialog.dismiss();
              Log.d("response api", jsonRESULTS.toString());
            } else if (jsonRESULTS.getString("success").equals("true")){
              String pesan_login=jsonRESULTS.getString("message");
//              Toast.makeText(getApplication(), ""+pesan_login, Toast.LENGTH_SHORT).show();
              Log.v("ini",pesan_login);
//              progressDialog.dismiss();
            }
          } catch (JSONException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else {
        }
      }
      @Override
      public void onFailure(Call<ResponseBody> call, Throwable t) {
        try {
          Log.v("response:","gagal");
//                    InputGagal();
          Toast.makeText(getApplication(), "Server Tidak Merespon", Toast.LENGTH_SHORT).show();
//          progressDialog.dismiss();
        } catch (Exception e) {
          e.printStackTrace();
        }

      }
    });

  }

  private void Simpan(String mac, String suhu, String suhuruangan, String keterangan, String gambar, String Hasil) {
    sensor.Simpan(mac,suhu,suhuruangan,Hasil,keterangan,gambar);


//    sensordata.(mac,suhu,keterangan,gambar)gambar

  }

  @Override
  public void Berhasil_kirimdata(String Message){


}
  @Override
  public void Gagal_kirimdata(String Message){

  }
  @Override
  public void No_Internet(String Message){

  }

  public void getBitmap(TextureView vv) {
    Date now = new Date();
    android.text.format.DateFormat.format("yyyy-MM-dd_hh:mm:ss", now);
    Random rand = new Random();
    double rand_dub1 = rand.nextDouble();
//    Toast.makeText(getApplication(), "Testt", Toast.LENGTH_SHORT).show();
    String mPath = Environment.getExternalStorageDirectory().toString() + "/DCIM/Screenshots/"+rand_dub1+ ".png";
//    Toast.makeText(getApplication(), "Capturing Screenshot: " + mPath, Toast.LENGTH_SHORT).show();
    Log.d("FOTO SAVE",mPath);
    mediaPath1=mPath;
    Bitmap bm = vv.getBitmap();
    if(bm == null)
      Log.e("test","bitmap is null");
    OutputStream fout = null;
    File imageFile = new File(mPath);
    try {
      fout = new FileOutputStream(imageFile);
      bm.compress(Bitmap.CompressFormat.PNG, 90, fout);
      storedpath=mPath;
      if(sp.contains("imagepath")) {
        storedpath=sp.getString("imagepath", "");
        myImage.setImageBitmap(BitmapFactory.decodeFile(storedpath));
      }
      SimpanGambar();
      fout.flush();
      fout.close();
    } catch (FileNotFoundException e) {
      Log.e("test", "FileNotFoundException");
      e.printStackTrace();
    } catch (IOException e) {
      Log.e("test", "IOException");
      e.printStackTrace();
    }
  }
}

