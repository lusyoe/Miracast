package com.winside.miracast.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Author        : lu
 * Data          : 2015/8/11
 * Time          : 15:51
 * Decription    :
 */
public class NetUtils {
    public static boolean isNetAvailiable(Context context) {
        ConnectivityManager connectivity = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo info = connectivity.getActiveNetworkInfo();
            if (info == null || !info.isAvailable()) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }


}
