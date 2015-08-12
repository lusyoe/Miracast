package com.winside.miracast.utils;

import android.app.ProgressDialog;
import android.content.Context;
import android.widget.Toast;

import com.winside.miracast.R;


/**
 * 提示信息的管理
 */

public class PromptManager {
    private static ProgressDialog dialog;

    public static void showProgressDialog(Context context) {
        dialog = new ProgressDialog(context);
//		dialog.setIcon(R.mipmap.ic_launcher);
        dialog.setTitle(R.string.app_name);

        dialog.setMessage("请等候，数据加载中……");
        dialog.show();
    }

    public static void closeProgressDialog() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void showToast(Context context, int msgResId) {
        Toast.makeText(context, msgResId, Toast.LENGTH_SHORT).show();
    }

    // 当测试阶段时true
    private static final boolean isShow = true;

    /**
     * 测试用 在正式投入市场：删
     * @param context
     * @param msg
     */
    public static void showToastTest(Context context, String msg) {
        if (isShow) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
        }
    }

    public static void showToastTestLong(Context context, String msg) {
        if (isShow) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
        }
    }

}
