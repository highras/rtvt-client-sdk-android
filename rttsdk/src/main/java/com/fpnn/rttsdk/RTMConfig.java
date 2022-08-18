package com.fpnn.rttsdk;

import com.fpnn.sdk.ErrorRecorder;

public class RTMConfig {
    final static int lostConnectionAfterLastPingInSeconds = 60;
    final static int globalMaxThread = 8;

    public final static String SDKVersion = "2.7.0";
//    public final static String InterfaceVersion = "2.7.0";
    public static ErrorRecorder defaultErrorRecorder = new ErrorRecorder();
    public static int globalQuestTimeoutSeconds = 30;   //请求超时时间
    public static int globalFileQuestTimeoutSeconds = 120;  //传输文件/音频/翻译/语音识别/文本检测 最大超时时间
//    public boolean autoConnect = true;
}
