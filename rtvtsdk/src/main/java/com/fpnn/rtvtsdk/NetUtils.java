package com.fpnn.rtvtsdk;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetUtils {
    /**
     * 没有连接网络
     */
    public static final int NETWORK_NONE = -1;
    /**
     * 移动网络
     */
    public static final int NETWORK_MOBILE = 0;
    /**
     * 无线网络
     */
    public static final int NETWORK_WIFI = 1;

    public static final int NETWORK_NOTINIT = -3;
    /**
     * 得到当前网络的状态
     *
     * @param context
     * @return
     */
    static int getNetWorkState(Context context) {
        // 得到连接管理器对象
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = null;
        try {
            if (connectivityManager !=null)
                activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        }
        catch (Exception e)
        {
            ;
        }

        if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {

            if (activeNetworkInfo.getType() == (ConnectivityManager.TYPE_WIFI)) {
                return NETWORK_WIFI;
            } else if (activeNetworkInfo.getType() == (ConnectivityManager.TYPE_MOBILE)) {
                return NETWORK_MOBILE;
            }
        } else {
            return NETWORK_NONE;
        }
        return NETWORK_NONE;
    }
}