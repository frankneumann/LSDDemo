package com.tc.tar;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.util.ArrayUtils;
import org.rajawali3d.view.ISurface;
import org.rajawali3d.view.SurfaceView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements LSDRenderer.RenderListener {

    public static final String TAG = MainActivity.class.getSimpleName();

    private static VideoSource sVideoSource;
    private String mfileDir;
    private RelativeLayout mLayout;
    private SurfaceView mRajawaliSurface;
    private Renderer mRenderer;
    private ImageView mImageView;
    private int[] mResolution;
    private boolean mStarted = false;
    private boolean isFirst = true;
    private MenuItem mItemSavePointCloud;

    static {
        System.loadLibrary("g2o_core");
        System.loadLibrary("g2o_csparse_extension");
        System.loadLibrary("g2o_ext_csparse");
        System.loadLibrary("g2o_solver_csparse");
        System.loadLibrary("g2o_stuff");
        System.loadLibrary("g2o_types_sba");
        System.loadLibrary("g2o_types_sim3");
        System.loadLibrary("g2o_types_slam3d");
        System.loadLibrary("LSD");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mfileDir = getExternalFilesDir(null).getAbsolutePath();
        copyAssets(this, mfileDir);
        TARNativeInterface.nativeInit(mfileDir + File.separator + "cameraCalibration.cfg");
        mRajawaliSurface = createSurfaceView();
        mRenderer = createRenderer();
        applyRenderer();

        mLayout = new RelativeLayout(this);
        FrameLayout.LayoutParams childParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams
                .MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mLayout.addView(mRajawaliSurface, childParams);

        mImageView = new ImageView(this);
        RelativeLayout.LayoutParams imageParams = new RelativeLayout.LayoutParams(480, 320);
        imageParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        imageParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mLayout.addView(mImageView, imageParams);
        mResolution = TARNativeInterface.nativeGetResolution();

        sVideoSource = new VideoSource(this, mResolution[0], mResolution[1]);
        sVideoSource.start();

        setContentView(mLayout);

        Toolbar toolbar = new Toolbar(this);
        final TypedArray styledAttributes = this.getTheme().obtainStyledAttributes(
                new int[] { android.R.attr.actionBarSize });
        int actionBarSize = (int) styledAttributes.getDimension(0, 0);
        styledAttributes.recycle();
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, actionBarSize);
        toolbar.setLayoutParams(layoutParams);
        toolbar.setElevation(4f);
        toolbar.setPopupTheme(R.style.ThemeOverlay_AppCompat_Light);
        toolbar.setVisibility(View.VISIBLE);

        mLayout.addView(toolbar, 0);

        setSupportActionBar(toolbar);

        Toast.makeText(this, "Press Volume(+) to Start", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mItemSavePointCloud = menu.add("Save Point Cloud");
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == mItemSavePointCloud) {
            String toastMessage = new String();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String currentDateandTime = sdf.format(new Date());
            String filePath = mfileDir + "/PointCloud_" + currentDateandTime + ".ply";

            try {
                LSDKeyFrame[] keyFrames = TARNativeInterface.nativeGetAllKeyFrames();
                float[] vertices = null;
                int[] colors = null;
                int pointNum = 0;
                for (LSDKeyFrame keyFrame : keyFrames) {
                    if (vertices == null) {
                        vertices = keyFrame.worldPoints;
                        colors = keyFrame.colors;
                    } else {
                        vertices = ArrayUtils.concatAllFloat(vertices, keyFrame.worldPoints);
                        colors = ArrayUtils.concatAllInt(colors, keyFrame.colors);
                    }
                    pointNum += keyFrame.pointCount;
                }

                PrintWriter pw = new PrintWriter(filePath);
                pw.println(String.format("ply\n" +
                        "format ascii 1.0\n" +
                        "element vertex %d\n" +
                        "property float x\n" +
                        "property float y\n" +
                        "property float z\n" +
                        "property uchar red\n" +
                        "property uchar green\n" +
                        "property uchar blue\n" +
                        "end_header", pointNum));
                for (int i = 0; i < pointNum; i++) {
                    pw.println(String.format(Locale.ROOT, "%f %f %f %d %d %d", vertices[3 * i], vertices[3 * i + 1], vertices[3 * i + 2],
                            Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])));
                }
                pw.flush();
                pw.close();

                toastMessage = String.format("Saved point cloud to file: %s", filePath);
            } catch (Exception e) {
                toastMessage = String.format("No point cloud could be saved due to exception: %s", e);
            }

            Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        }

        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            TARNativeInterface.nativeDestroy();
            finish();
            System.exit(0);
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            Toast.makeText(this, "Start", Toast.LENGTH_SHORT).show();
            if (mStarted) {
            } else {
                mStarted = true;
                if (isFirst) {
                    TARNativeInterface.nativeStart();
                    isFirst = false;
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Toast.makeText(this, "Stop!", Toast.LENGTH_SHORT).show();
            if (mStarted) {
                mStarted = false;
            }
            return true;
        }
        return true;
    }

    protected SurfaceView createSurfaceView() {
        SurfaceView view = new SurfaceView(this);
        view.setFrameRate(60);
        view.setRenderMode(ISurface.RENDERMODE_WHEN_DIRTY);
        return view;
    }

    protected Renderer createRenderer() {
        LSDRenderer renderer = new LSDRenderer(this);
        renderer.setRenderListener(this);
        return renderer;
    }

    protected void applyRenderer() {
        mRajawaliSurface.setSurfaceRenderer(mRenderer);
    }

    public View getView() {
        return mLayout;
    }

    @Override
    public void onRender() {
        if (mImageView == null)
            return;

        byte[] imgData;
        if (!mStarted) {
            byte[] frameData = sVideoSource.getFrame();     // YUV data
            if (frameData == null)
                return;
            imgData = new byte[mResolution[0] * mResolution[1] * 4];
            for (int i = 0; i < imgData.length / 4; ++i) {
                imgData[i * 4] = frameData[i];
                imgData[i * 4 + 1] = frameData[i];
                imgData[i * 4 + 2] = frameData[i];
                imgData[i * 4 + 3] = (byte) 0xff;
            }
        } else {
            imgData = TARNativeInterface.nativeGetCurrentImage(0);
        }

        if (imgData == null)
            return;

        final Bitmap bm = Bitmap.createBitmap(mResolution[0], mResolution[1], Bitmap.Config.ARGB_8888);
        bm.copyPixelsFromBuffer(ByteBuffer.wrap(imgData));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImageView.setImageBitmap(bm);
            }
        });
    }

    // Call from JNI
    public static VideoSource getVideoSource() {
        return sVideoSource;
    }

    public static void copyAssets(Context context, String dir) {
        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
            Log.e(TAG, "copyAssets: Failed to get asset file list.", e);
        }
        for (String filename : files) {
            if (!filename.endsWith(".cfg"))//hack to skip non cfg files
                continue;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File(dir, filename);
                if (outFile.exists()) {
                    Log.d(TAG, "copyAssets: File exists: " + filename);
                } else {
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                    Log.d(TAG, "copyAssets: File copied: " + filename);
                }
            } catch (IOException e) {
                Log.e(TAG, "copyAssets: Failed to copy asset file: " + filename, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, e.toString());
                    }
                }
            }
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
