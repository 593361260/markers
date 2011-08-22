package com.google.android.markersbeta;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationSet;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import android.media.MediaScannerConnection;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.android.slate.Slate;

public class MarkersActivity extends Activity implements MrShaky.Listener
{
    final static int LOAD_IMAGE = 1000;

    static final String TAG = "Markers";

    public static final String IMAGE_SAVE_DIRNAME = "Drawings";
    public static final String IMAGE_TEMP_DIRNAME = IMAGE_SAVE_DIRNAME + "/.temporary";
    public static final String WIP_FILENAME = "temporary.png";

    private static final String PREFS_NAME = "MarkersPrefs";

    private static final int[] BUTTON_COLORS = {
        0xFF000000,
        0xFFFFFFFF,
        0xFFC0C0C0,0xFF808080,
        0xFF404040,0xFFFF0000,
        0xFF00FF00,0xFF0000FF,
        0xFFFF00CC,0xFFFF8000,
        0xFFFFFF00,0xFF6000A0,0xFF804000,
    };

    Slate mSlate;

    MrShaky mShaky;

    boolean mJustLoadedImage = false;

    protected ToolButton mLastTool, mActiveTool;

    public static class ColorList extends LinearLayout {
        public ColorList(Context c, AttributeSet as) {
            super(c, as);
        }
        
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (changed) setOrientation(((right-left) > (bottom-top)) ? HORIZONTAL : VERTICAL);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            return true;
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
    	((ViewGroup)mSlate.getParent()).removeView(mSlate);
        return mSlate;
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.copyFrom(getWindow().getAttributes());
        lp.format = PixelFormat.RGBA_8888;
        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getWindow().setAttributes(lp);

        //Log.d(TAG, "window format: " + getWindow().getAttributes().format);
        
        mShaky = new MrShaky(this, this);
        
        setContentView(R.layout.main);
        mSlate = (Slate) getLastNonConfigurationInstance();
        if (mSlate == null) {
        	mSlate = new Slate(this);
        }
        final ViewGroup root = ((ViewGroup)findViewById(R.id.root));
        root.addView(mSlate, 0);
        
        if (icicle != null) {
            onRestoreInstanceState(icicle);
        }

        final ViewGroup colors = (ViewGroup) findViewById(R.id.colors);
        colors.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //Log.d(TAG, "onTouch: " + event);
                if (event.getAction() == MotionEvent.ACTION_DOWN
                        || event.getAction() == MotionEvent.ACTION_MOVE) {
                   final boolean horizontal = (colors.getWidth() > colors.getHeight());

                   int index = (int) 
                        ((horizontal
                                ? (event.getX() / colors.getWidth())
                                : (event.getY() / colors.getHeight()))
                            * colors.getChildCount());
                    //Log.d(TAG, "touch index: " + index);
                    if (index >= colors.getChildCount()) return false;
                    View button = colors.getChildAt(index);
                    clickColor(button);
                }
                return true;
            }
        });
        
        setActionBarVisibility(false, false);

        clickColor(colors.getChildAt(0));

        Resources res = getResources();
        
        ToolButton.ToolCallback toolCB = new ToolButton.ToolCallback() {
            @Override
            public void setPenMode(ToolButton tool, float min, float max) {
                mSlate.setPenSize(min, max);
                mLastTool = mActiveTool;
                mActiveTool = tool;
                
                if (mLastTool != mActiveTool) {
                    mLastTool.deactivate();
                }
            }
            @Override
            public void restore(ToolButton tool) {
                if (tool != mLastTool) mLastTool.click();
            }
        };
        
        final ToolButton penThinButton = (ToolButton) findViewById(R.id.pen_thin);
        penThinButton.setCallback(toolCB);

        final ToolButton penMediumButton = (ToolButton) findViewById(R.id.pen_medium);
        if (penMediumButton != null) {
            penMediumButton.setCallback(toolCB);
        }
        
        final ToolButton penThickButton = (ToolButton) findViewById(R.id.pen_thick);
        penThickButton.setCallback(toolCB);

        final ToolButton fatMarkerButton = (ToolButton) findViewById(R.id.fat_marker);
        if (fatMarkerButton != null) {
            fatMarkerButton.setCallback(toolCB);
        }
        
        mLastTool = mActiveTool = (penThickButton != null) ? penThickButton : penThinButton;
        mActiveTool.click();
   }

    // MrShaky.Listener
    public void onShake() {
        mSlate.undo();
    }

    public float getAccel() {
        return mShaky.getCurrentMagnitude();
    }

    @Override
    public void onPause() {
        super.onPause();
        mShaky.pause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setRequestedOrientation(
        	(Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD)
        		? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        		: ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mShaky.resume();
    }

    @Override
    public void onConfigurationChanged (Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
//    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
//            setContentView(R.layout.main);
//            findViewById
//    	}
    }

    @Override
    public void onAttachedToWindow() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveDrawing(WIP_FILENAME, true);
        //mSlate.recycle(); -- interferes with newly asynchronous saving code when sharing
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mJustLoadedImage) {
            loadDrawing(WIP_FILENAME, true);
        } else {
            mJustLoadedImage = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            setActionBarVisibility(!getActionBarVisibility(), true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    final static boolean hasAnimations() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB);
    }

    public void clickLogo(View v) {
        setActionBarVisibility(!getActionBarVisibility(), true);
    }

    public boolean getActionBarVisibility() {
        final View bar = findViewById(R.id.actionbar_contents);
        return bar.getVisibility() == View.VISIBLE;
    }

    public void setActionBarVisibility(boolean show, boolean animate) {
        final View bar = findViewById(R.id.actionbar_contents);
        final View logo = findViewById(R.id.logo);
        if (!show) {
            if (hasAnimations() && animate) {
                ObjectAnimator.ofFloat(logo, "alpha", 1f, 0.5f).start();
                ObjectAnimator.ofFloat(bar, "translationY", 0f, -20f).start();
                Animator a = ObjectAnimator.ofFloat(bar, "alpha", 1f, 0f);
                a.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        bar.setVisibility(View.GONE);
                    }
                });
                a.start();
            } else {
                bar.setVisibility(View.GONE);
            }
        } else {
            bar.setVisibility(View.VISIBLE);
            if (hasAnimations() && animate) {
                ObjectAnimator.ofFloat(logo, "alpha", 0.5f, 1f).start();
                ObjectAnimator.ofFloat(bar, "translationY", -20f, 0f).start();
                ObjectAnimator.ofFloat(bar, "alpha", 0f, 1f).start();
            }
        }
    }

    public void clickClear(View v) {
        mSlate.clear();
    }

    public boolean loadDrawing(String filename) {
        return loadDrawing(filename, false);
    }
    public boolean loadDrawing(String filename, boolean temporary) {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        d = new File(d, temporary ? IMAGE_TEMP_DIRNAME : IMAGE_SAVE_DIRNAME);
        final String filePath = new File(d, filename).toString();
        //Log.d(TAG, "loadDrawing: " + filePath);
        
        if (d.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inDither = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            opts.inScaled = false;
            Bitmap bits = BitmapFactory.decodeFile(filePath, opts);
            if (bits != null) {
                mSlate.setBitmap(bits);
                return true;
            }
        }
        return false;
    }

    public void saveDrawing(String filename) {
        saveDrawing(filename, false);
    }

    public void saveDrawing(String filename, boolean temporary) {
        saveDrawing(filename, temporary, /*animate=*/ false, /*share=*/ false, /*clear=*/ false);
    }

    public void saveDrawing(String filename, boolean temporary, boolean animate, boolean share, boolean clear) {
        final Bitmap bits = mSlate.getBitmap();
        if (bits == null) {
            Log.e(TAG, "save: null bitmap");
            return;
        }
        
        final String _filename = filename;
        final boolean _temporary = temporary;
        final boolean _animate = animate;
        final boolean _share = share;
        final boolean _clear = clear;

        new AsyncTask<Void,Void,String>() {
            @Override
            protected String doInBackground(Void... params) {
                String fn = null;
                try {
                    File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    d = new File(d, _temporary ? IMAGE_TEMP_DIRNAME : IMAGE_SAVE_DIRNAME);
                    if (!d.exists()) {
                        if (d.mkdirs()) {
                            new FileOutputStream(new File(d, ".nomedia")).write('\n');
                        } else {
                            throw new IOException("cannot create dirs: " + d);
                        }
                    }
                    File file = new File(d, _filename);
                    Log.d(TAG, "save: saving " + file);
                    OutputStream os = new FileOutputStream(file);
                    bits.compress(Bitmap.CompressFormat.PNG, 0, os);
                    os.close();
                    
                    fn = file.toString();
                } catch (IOException e) {
                    Log.e(TAG, "save: error: " + e);
                }
                return fn;
            }
            
            @Override
            protected void onPostExecute(String fn) {
                if (_share && fn != null) {
                    Uri streamUri = Uri.fromFile(new File(fn));
                    Intent sendIntent = new Intent(Intent.ACTION_SEND);
                    sendIntent.setType("image/jpeg");
                    sendIntent.putExtra(Intent.EXTRA_STREAM, streamUri);
                    startActivity(Intent.createChooser(sendIntent, "Send drawing to:"));
                }
                
                mSlate.clear();
                
                if (!_temporary) {
                    MediaScannerConnection.scanFile(MarkersActivity.this,
                            new String[] { fn }, null, null
                            );
                }
            }
        }.execute();
        
    }

    public void clickSave(View v) {
        if (mSlate.isEmpty()) return;
        
        v.setEnabled(false);
        saveDrawing(System.currentTimeMillis() + ".png");
        Toast.makeText(this, "Drawing saved.", Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    public void clickSaveAndClear(View v) {
        if (mSlate.isEmpty()) return;

        v.setEnabled(false);
        saveDrawing(System.currentTimeMillis() + ".png", 
                /*temporary=*/ false, /*animate=*/ true, /*share=*/ false, /*clear=*/ true);
        Toast.makeText(this, "Drawing saved.", Toast.LENGTH_SHORT).show();
        v.setEnabled(true);
    }

    public void clickShare(View v) {
        v.setEnabled(false);
        saveDrawing("from_markers.png", /*temporary=*/ true, /*animate=*/ false, /*share=*/ true, /*clear=*/ false);
        v.setEnabled(true);
    }

    public void clickLoad(View v) {
        Intent i = new Intent(Intent.ACTION_PICK,
                       android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult(i, LOAD_IMAGE); 
    }
    public void clickDebug(View v) {
        mSlate.setDebugFlags(mSlate.getDebugFlags() == 0 
            ? Slate.FLAG_DEBUG_EVERYTHING
            : 0);
        Toast.makeText(this, "Debug mode " + ((mSlate.getDebugFlags() == 0) ? "off" : "on"),
            Toast.LENGTH_SHORT).show();
    }
    public void clickColor(View v) {
        int color = 0xFF000000;
        switch (v.getId()) {
            case R.id.black:  color = 0xFF000000; break;
            case R.id.white:  color = 0xFFFFFFFF; break;
            case R.id.lgray:  color = 0xFFC0C0C0; break;
            case R.id.gray:   color = 0xFF808080; break;
            case R.id.dgray:  color = 0xFF404040; break;

            case R.id.red:    color = 0xFFFF0000; break;
            case R.id.green:  color = 0xFF00FF00; break;
            case R.id.blue:   color = 0xFF0000FF; break;

            case R.id.pink:   color = 0xFFFF00CC; break;
            case R.id.orange: color = 0xFFFF8000; break;
            case R.id.yellow: color = 0xFFFFFF00; break;
            case R.id.purple: color = 0xFF6000A0; break;

            case R.id.brown:  color = 0xFF804000; break;

            case R.id.erase:  color = 0; break;
        }
        setPenColor(color);

        ViewGroup list = (ViewGroup) findViewById(R.id.colors);
        for (int i=0; i<list.getChildCount(); i++) {
            Button c = (Button) list.getChildAt(i);
            c.setText(c==v
                ? "\u25A0"
                : "");
        }
    }

    public void clickUndo(View v) {
        mSlate.undo();
    }

    public void setPenColor(int color) {
        mSlate.setPenColor(color);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) { 
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

        switch (requestCode) { 
        case LOAD_IMAGE:
            if (resultCode == RESULT_OK) {  
                Uri contentUri = imageReturnedIntent.getData();
                Toast.makeText(this, "Loading from " + contentUri, Toast.LENGTH_SHORT).show();

                loadDrawing(WIP_FILENAME, true);
                mJustLoadedImage = true;

                try {
                    Bitmap b = MediaStore.Images.Media.getBitmap(getContentResolver(), contentUri);
                    if (b != null) {
                        mSlate.paintBitmap(b);
                        Log.d(TAG, "successfully loaded bitmap: " + b);
                    } else {
                        Log.e(TAG, "couldn't get bitmap from " + contentUri);
                    }
                } catch (java.io.FileNotFoundException ex) {
                    Log.e(TAG, "error loading image from " + contentUri + ": " + ex);
                } catch (java.io.IOException ex) {
                    Log.e(TAG, "error loading image from " + contentUri + ": " + ex);
                }
            }
        }
    }

}
