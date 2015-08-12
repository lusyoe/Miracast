package com.winside.miracast;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.AnimationDrawable;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import com.winside.miracast.utils.LogUtils;
import com.winside.miracast.utils.PromptManager;

public class MainActivity extends Activity implements  WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener, WifiP2pManager.ChannelListener {

    private ImageView mConnectState;
    private String TAG = "Main";
    private int mNetId = -1;

    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    public static final String DNSMASQ_IP_ADDR_ACTION = "android.net.dnsmasq.IP_ADDR";
    public static final String DNSMASQ_MAC_EXTRA = "MAC_EXTRA";
    public static final String DNSMASQ_IP_EXTRA = "IP_EXTRA";
    public static final String DNSMASQ_PORT_EXTRA = "PORT_EXTRA";

    // 连接的设备信息
    private WifiP2pDevice mDevice = null;
    private String mPort;
    private String mIP;

    private static final int MAX_DELAY_MS = 0;

    // 清晰度
    public static final String HRESOLUTION_DISPLAY = "display_resolution_hd";

    // WifiP2p是否激活
    private boolean isWifiP2pEnabled = false;
    private boolean retryChannel = false;

    // 连接失败广播
    private BroadcastReceiver mConnectFailReceiver;

    public static final String ACTION_FIX_RTSP_FAIL = "com.amlogic.miracast.RTSP_FAIL";
    public static final String ACTION_REMOVE_GROUP = "com.amlogic.miracast.REMOVE_GROUP";
    private SharedPreferences mPref;
    private SharedPreferences.Editor mEditor;
    private WiFiDirectBroadcastReceiver mReceiver;
    private PowerManager.WakeLock mWakeLock;

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mConnectState = ((ImageView) findViewById(R.id.dot));

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        initFilter();

        initBroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_FIX_RTSP_FAIL);
        filter.addAction(ACTION_REMOVE_GROUP);
        registerReceiver(mConnectFailReceiver, filter);
        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        mEditor = mPref.edit();


    }

    @Override
    protected void onResume() {
        super.onResume();
        changeRole(false);
        /* enable backlight */
        mReceiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
        mWakeLock.acquire();
        registerReceiver(mReceiver, intentFilter);
    }

    public void onQuery(MenuItem item) {
        // 设置清晰度为标清
        mEditor.putBoolean(HRESOLUTION_DISPLAY, false);
        mEditor.commit();

        // 设置清晰度为高清
//                mEditor.putBoolean(HRESOLUTION_DISPLAY, true);
//                mEditor.commit();
    }


    private void initBroadcastReceiver() {
        mConnectFailReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(ACTION_FIX_RTSP_FAIL)) {
                    Log.d(TAG, "ACTION_FIX_RTSP_FAIL : mNetId=" + mNetId);
                    fixRtspFail();
                } else if (action.equals(ACTION_REMOVE_GROUP)) {
                    Log.d(TAG, "ACTION_REMOVE_GROUP");
                    manager.removeGroup(channel, null);
                }
            }
        };

    }

    private void initFilter() {
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(DNSMASQ_IP_ADDR_ACTION);


    }

    /**
     * 开始搜索附近的设备，需要开启无线显示
     */
    public void startSearch() {
        LogUtils.d("startSearch wifiP2pEnabled:" + isWifiP2pEnabled);
        /*if (!isWifiP2pEnabled) {
            if (manager != null && channel != null) {
                mConnectWarn.setVisibility(View.VISIBLE);
                mConnectWarn.setText(getResources().getString(R.string.p2p_off_warning));
            }
            return;
        }*/
//        onInitiateDiscovery();
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                PromptManager.showToast(MainActivity.this, MainActivity.this.getResources().getString(R.string.discover_init));
            }

            @Override
            public void onFailure(int reasonCode) {
//                PromptManager.showToast(MainActivity.this, MainActivity.this.getResources().getString(R.string.discover_fail));
                PromptManager.showToastTestLong(MainActivity.this, "错误响应码: " + reasonCode);
            }
        });
    }

    /**
     *  搜索进度条
     */
   /* public void onInitiateDiscovery() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(this, getResources().getString(R.string.find_title), getResources().getString(R.string.find_progress), true, true, new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                    public void onSuccess() {
                        if (mDeviceNameText != null) {
                            mSavedDeviceName = mDeviceNameText.getText().toString();
                            mDeviceNameShow.setText(mSavedDeviceName);
                        }
                        if (DEBUG) Log.d(TAG, " device rename success");
                    }

                    public void onFailure(int reason) {
                        Toast.makeText(WiFiDirectMainActivity.this, R.string.wifi_p2p_failed_rename_message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }*/

    /**
     * 停止搜索隐藏进度条
     */
    /*public void discoveryStop(){
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }*/

    /**
     * 连接失败操作
     */
    private void fixRtspFail() {
        if (manager != null && mNetId != -1) {
            manager.removeGroup(channel, null);
            manager.deletePersistentGroup(channel, mNetId, null);

            new AlertDialog.Builder(this).setTitle(R.string.rtsp_fail).setMessage(R.string.rtsp_suggestion).setIconAttribute(android.R.attr.alertDialogIcon).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            }).show();
        }
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
        manager.setWFDInfo(channel, wfdInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Successfully set WFD info.");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to set WFD info with reason " + reason + ".");
            }
        });
        ;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
//        Log.e("Main", "onWindowFocusChanged.....");
        mConnectState.setBackgroundResource(R.drawable.wifi_connect);
        AnimationDrawable anim = (AnimationDrawable) mConnectState.getBackground();
        anim.start();
    }

    @Override
    public void onChannelDisconnected() {
        if (manager != null && !retryChannel) {
            PromptManager.showToast(this, getResources().getString(R.string.channel_try));
            retryChannel = true;
            manager.initialize(this, getMainLooper(), this);
        } else {
            //retryChannel = false;
            PromptManager.showToast(this, getResources().getString(R.string.channel_close));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
        mWakeLock.release();
        changeRole(true);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mConnectFailReceiver);
        super.onDestroy();
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {

    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        if (group != null) {
            LogUtils.e("onGroupInfoAvailable true : " + group);
            mNetId = group.getNetworkId();
        } else {
            LogUtils.d("onGroupInfoAvailable false");
            mNetId = -1;
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {

    }

    public void setDevice(WifiP2pDevice device) {
        mDevice = device;
       /* if (mDevice != null) {
            if(mDeviceTitle != null)
                mDeviceTitle.setVisibility(View.VISIBLE);
            mSavedDeviceName = mDevice.deviceName;
            if(mDeviceNameShow != null)
                mDeviceNameShow.setText(mSavedDeviceName);
        }
        if (DEBUG)*/
        if (mDevice != null) {
            LogUtils.e("mDevice.status" + mDevice.status);
        }
    }

    public void startMiracast(String ip, String port) {
        mPort = port;
        mIP = ip;
        // 设置连接的显示信息
//        setConnect();
        LogUtils.d("start miracast delay " + MAX_DELAY_MS + " ms");
        mHandler.postDelayed(new Runnable() {
            public void run() {
                Intent intent = new Intent(MainActivity.this, SinkActivity.class);
                Bundle bundle = new Bundle();
                bundle.putString(SinkActivity.KEY_PORT, mPort);
                bundle.putString(SinkActivity.KEY_IP, mIP);
                bundle.putBoolean(HRESOLUTION_DISPLAY, mPref.getBoolean(HRESOLUTION_DISPLAY, false));
                intent.putExtras(bundle);
                startActivity(intent);
            }
        }, MAX_DELAY_MS);
    }

}
