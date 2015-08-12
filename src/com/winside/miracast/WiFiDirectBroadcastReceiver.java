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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.util.Log;

import com.winside.miracast.utils.LogUtils;
import com.winside.miracast.utils.PromptManager;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager manager;
    private Channel channel;
    private MainActivity activity;

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     * @param activity activity associated with the receiver
     */
    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel, MainActivity activity) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        LogUtils.e("onReceive action:" + action);
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            // UI update to indicate wifi p2p status.
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
//                activity.setIsWifiP2pEnabled(true);
                PromptManager.showToastTest(context, "WIFI_P2P已激活");
            } else {
//                activity.setIsWifiP2pEnabled(false);
                PromptManager.showToastTest(context, "WIFI_P2P未激活");
            }
            LogUtils.d("P2P state changed - " + state);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // request available peers from the wifi p2p manager. This is an
            // asynchronous call and the calling activity is notified with a
            // callback on PeerListListener.onPeersAvailable()
            if (manager != null) {
                manager.requestPeers(channel, (PeerListListener) activity);
            }
            LogUtils.d("P2P peers changed");
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            LogUtils.d("P2P connection changed isConnected:" + networkInfo.isConnected());
            if (manager != null) {
                manager.requestGroupInfo(channel, (GroupInfoListener) activity);
            }
            if (networkInfo.isConnected()) {
                // we are connected with the other device, request connection
                // info to find group owner IP
                manager.requestConnectionInfo(channel, activity);
            } else {
                // It's a disconnect
//                activity.stopMiracast(false);
                //start a search when we are disconnected
                activity.startSearch();
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {

            LogUtils.e("WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION");
//            activity.resetData();
            activity.setDevice((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            int discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
            if (activity != null && discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
//                activity.discoveryStop();
            }
            LogUtils.e("Discovery state changed: " + discoveryState + " " +
                    "->1:stop, 2:start");
        } else if (MainActivity.DNSMASQ_IP_ADDR_ACTION.equals(action)) {
            String mac = intent.getStringExtra(MainActivity.DNSMASQ_MAC_EXTRA);
            String ip = intent.getStringExtra(MainActivity.DNSMASQ_IP_EXTRA);
            String port = intent.getStringExtra(MainActivity.DNSMASQ_PORT_EXTRA);

            LogUtils.e("mac:" + mac + " IP:" + ip + " port:" + port);
            if (activity != null) {
                LogUtils.e("Activity = " + activity);
                activity.startMiracast(ip, port);
            } else {
                PromptManager.showToastTest(context, "activity为空了");
            }

        }
    }
}
