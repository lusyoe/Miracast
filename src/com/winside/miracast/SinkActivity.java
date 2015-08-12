/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.winside.miracast;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SystemWriteManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.UserHandle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.winside.miracast.utils.LogUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SinkActivity extends Activity {

    public static final String TAG = "amlSink";

    public static final String KEY_IP = "ip";
    public static final String KEY_PORT = "port";

    private static final String SO = "winside_miracast_jni";
    private final int OSD_TIMER = 5000;//ms

    private final String FB0_BLANK = "/sys/class/graphics/fb0/blank";
    //private final String CLOSE_GRAPHIC_LAYER      = "echo 1 > /sys/class/graphics/fb0/blank";
    //private final String OPEN_GRAPHIC_LAYER       = "echo 0 > /sys/class/graphics/fb0/blank";
    //private final String WIFI_DISPLAY_CMD         = "wfd -c";
    //private static final int MAX_DELAY_MS         = 3000;

    private final int MSG_CLOSE_OSD = 2;

    private String mIP;
    private String mPort;
    private boolean mMiracastRunning = false;
    private PowerManager.WakeLock mWakeLock;
    private Handler mMiracastThreadHandler = null;
    private boolean isHD = false;
    private SurfaceView mSurfaceView;

    private View mRootView;

    private SystemWriteManager mSystemWrite;

    static {
        LogUtils.e("加载.so库" + SO);
        System.loadLibrary(SO);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                //Log.d(TAG, "P2P connection changed isConnected:" + networkInfo.isConnected());
                if (!networkInfo.isConnected()) {
                    finishView();
                }
            }
        }
    };

    private void finishView() {
        Intent homeIntent = new Intent(SinkActivity.this, MainActivity.class);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        SinkActivity.this.startActivity(homeIntent);
        SinkActivity.this.finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //no title and no status bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.sink);

        mSurfaceView = (SurfaceView) findViewById(R.id.wifiDisplaySurface);

        //full screen test
        mRootView = (View) findViewById(R.id.rootView);

        mSurfaceView.getHolder().addCallback(new SurfaceCallback());
        mSurfaceView.getHolder().setKeepScreenOn(true);
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        mPort = bundle.getString(KEY_PORT);
        mIP = bundle.getString(KEY_IP);
        isHD = bundle.getBoolean(MainActivity.HRESOLUTION_DISPLAY);

        LogUtils.e("mIP = " + mIP + "mPort = " + mIP + "isHD = " + isHD);

        MiracastThread thr = new MiracastThread();
        new Thread(thr).start();
        synchronized (thr) {
            while (null == mMiracastThreadHandler) {
                try {
                    thr.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        SystemWriteManager mSystemWrite = (SystemWriteManager) getSystemService(Context.SYSTEM_WRITE_SERVICE);
    }

    /** register the BroadcastReceiver with the intent values to be matched */
    @Override
    public void onResume() {
        super.onResume();
        changeRole(false);
        /* enable backlight */
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.acquire();

        IntentFilter intentFilter = new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        registerReceiver(mReceiver, intentFilter);

        setSinkParameters(true);
        startMiracast(mIP, mPort);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopMiracast(true);
        unregisterReceiver(mReceiver);
        mWakeLock.release();
        setSinkParameters(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mMiracastRunning) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    openOsd();
                    break;

                case KeyEvent.KEYCODE_BACK:
                    openOsd();
                    return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "onKeyUp BACK KEY miracast running:" + mMiracastRunning);
        }

        if (mMiracastRunning) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                case KeyEvent.KEYCODE_VOLUME_MUTE:
                    break;

                case KeyEvent.KEYCODE_BACK:
                    exitMiracastDialog();
                    return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        //Log.d(TAG, "onConfigurationChanged: " + config);
        /*
    try {
      if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
      } 
      else if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
      }
    } 
    catch (Exception ex) {}
    */
    }

    private void openOsd() {
        switchGraphicLayer(true);
    }

    private void closeOsd() {
        switchGraphicLayer(false);
    }

    /**
     * open or close graphic layer
     */
    public void switchGraphicLayer(boolean open) {
        //Log.d(TAG, (open?"open":"close") + " graphic layer");
        writeSysfs(FB0_BLANK, open ? "0" : "1");
    }

    private int writeSysfs(String path, String val) {
        if (!new File(path).exists()) {
            Log.e(TAG, "File not found: " + path);
            return 1;
        }

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(path), 64);
            try {
                writer.write(val);
            } finally {
                writer.close();
            }
            return 0;

        } catch (IOException e) {
            Log.e(TAG, "IO Exception when write: " + path, e);
            return 1;
        }
    }

    private void exitMiracastDialog() {
        new AlertDialog.Builder(this).setTitle(R.string.exit).setMessage(R.string.exit_miracast).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Intent intent = new Intent(MainActivity.ACTION_REMOVE_GROUP);
                        sendBroadcastAsUser(intent, UserHandle.ALL);
                        finishView();
                    }
                }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                }).show();
    }

    public void startMiracast(String ip, String port) {
        Log.d(TAG, "start miracast isRunning:" + mMiracastRunning + " IP:" + ip + ":" + port);
        mMiracastRunning = true;

        Message msg = Message.obtain();
        msg.what = CMD_MIRACAST_START;
        Bundle data = msg.getData();
        data.putString(KEY_IP, ip);
        data.putString(KEY_PORT, port);
        mMiracastThreadHandler.sendMessage(msg);
    }

    /**
     * client or owner stop miracast
     * client stop miracast, only need open graphic layer
     */
    public void stopMiracast(boolean owner) {
        Log.d(TAG, "stop miracast running:" + mMiracastRunning);

        if (mMiracastRunning) {
            mMiracastRunning = false;
            nativeDisconnectSink();

            //Message msg = Message.obtain();
            //msg.what = CMD_MIRACAST_STOP;
            //mMiracastThreadHandler.sendMessage(msg);
        }
    }

    public void setSinkParameters(boolean start) {
        if (start) {
            if (null != mSystemWrite) {
                mSystemWrite.writeSysfs("/sys/class/vfm/map", "rm default");
                mSystemWrite.writeSysfs("/sys/class/vfm/map", "add default decoder amvideo");
            } else {
                writeSysfs("/sys/class/vfm/map", "rm default");
                writeSysfs("/sys/class/vfm/map", "add default decoder amvideo");
            }
        } else {
            if (null != mSystemWrite) {
                mSystemWrite.writeSysfs("/sys/class/vfm/map", "rm default");
                mSystemWrite.writeSysfs("/sys/class/vfm/map", "add default decoder ppmgr amvideo");
            } else {
                writeSysfs("/sys/class/vfm/map", "rm default");
                writeSysfs("/sys/class/vfm/map", "add default decoder ppmgr amvideo");
            }

        }
    }

    private native void nativeConnectWifiSource(SinkActivity sink, String ip, int port);

    private native void nativeDisconnectSink();

    private native void nativeResolutionSettings(boolean isHD);

    //private native void nativeSourceStart(String ip);
    //private native void nativeSourceStop();
    // Native callback.
    public void notifyRtspError() {
        Log.d(TAG, "notifyRtspError received!!!");
        Intent intent = new Intent(MainActivity.ACTION_FIX_RTSP_FAIL);
        sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    public void notifyRtpNopacket() {
        Log.d(TAG, "notifyRtpNopacket received!!!");
        Intent intent = new Intent(MainActivity.ACTION_REMOVE_GROUP);
        sendBroadcastAsUser(intent, UserHandle.ALL);
        finishView();
    }

    private final int CMD_MIRACAST_START = 10;
    private final int CMD_MIRACAST_STOP = 11;

    class MiracastThread implements Runnable {
        public void run() {
            Looper.prepare();

            Log.v(TAG, "miracast thread run");

            mMiracastThreadHandler = new Handler() {
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case CMD_MIRACAST_START: {
                            Bundle data = msg.getData();
                            String ip = data.getString(KEY_IP);
                            String port = data.getString(KEY_PORT);

                            nativeConnectWifiSource(SinkActivity.this, ip, Integer.parseInt(port));

                        }
                        break;
                        default:
                            break;
                    }
                    sendResultMessage(msg.what, 0, 0, 0);
                }
            };

            synchronized (this) {
                notifyAll();
            }
            Looper.loop();
        }

        private Handler mHandler = new Handler() {
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case CMD_MIRACAST_START: {
                    }
                    break;
                    case CMD_MIRACAST_STOP: {
                    }
                    break;
                    case MSG_CLOSE_OSD: {
                        Log.v(TAG, "handler message CLOSE OSD");
                        closeOsd();
                    }
                    break;

                    default:
                        break;
                }
            }
        };

        private void sendResultMessage(int what, int arg1, int arg2, Object obj) {
            if (null != mHandler) {
                Message message = Message.obtain();
                message.what = what;
                message.arg1 = arg1;
                message.arg2 = arg2;
                message.obj = obj;
                mHandler.sendMessage(message);
            }
        }
    }

    private class SurfaceCallback implements SurfaceHolder.Callback {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            LogUtils.e("surfaceChanged...");
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            LogUtils.e("surfaceCreated...");
            nativeResolutionSettings(isHD);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            LogUtils.e("surfaceDestroyed...");
            if (getAndroidSDKVersion() == 17) {
                writeSysfs("/sys/class/graphics/fb0/free_scale", "0");
                writeSysfs("/sys/class/graphics/fb0/free_scale", "1");
            }
        }
    }

    private int getAndroidSDKVersion() {
        int version = 0;
        try {
            version = Integer.valueOf(Build.VERSION.SDK);
        } catch (NumberFormatException e) {
        }
        return version;
    }

    private void changeRole(boolean isSource) {

        WifiP2pWfdInfo wfdInfo = new WifiP2pWfdInfo();
        wfdInfo.setWfdEnabled(true);
        if (isSource) {
            wfdInfo.setDeviceType(WifiP2pWfdInfo.WFD_SOURCE);
        } else {
            wfdInfo.setDeviceType(WifiP2pWfdInfo.PRIMARY_SINK);
        }
        wfdInfo.setSessionAvailable(true);
        wfdInfo.setControlPort(7236);
        wfdInfo.setMaxThroughput(50);
    }
}
