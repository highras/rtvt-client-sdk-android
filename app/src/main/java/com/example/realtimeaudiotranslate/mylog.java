package com.example.realtimeaudiotranslate;

import android.util.Log;

import com.fpnn.sdk.ErrorRecorder;

public class mylog extends ErrorRecorder {
    public static void log(String msg) {
        Log.i("sdktest", msg);
    }
}
