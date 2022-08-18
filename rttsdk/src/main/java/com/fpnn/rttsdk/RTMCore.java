package com.fpnn.rttsdk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import com.fpnn.sdk.ClientEngine;
import com.fpnn.sdk.ConnectionWillCloseCallback;
import com.fpnn.sdk.ErrorCode;
import com.fpnn.sdk.ErrorRecorder;
import com.fpnn.sdk.FunctionalAnswerCallback;
import com.fpnn.sdk.TCPClient;
import com.fpnn.sdk.proto.Answer;
import com.fpnn.sdk.proto.Quest;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;
import com.fpnn.rttsdk.UserInterface.*;
import com.fpnn.rttsdk.RTMStruct.*;
import static com.fpnn.sdk.ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION;

class RTMCore extends BroadcastReceiver{

    public enum ClientStatus {
        Closed,
        Connecting,
        Connected
    }

    public enum CloseType {
        ByUser,
        ByServer,
        Timeout,
        None
    }


    //for network change
    private int LAST_TYPE = NetUtils.NETWORK_NOTINIT;
    SharedPreferences addressSp;



    @Override
    public void onReceive(Context context, Intent intent) {
        String b= ConnectivityManager.CONNECTIVITY_ACTION;
        String a= intent.getAction();
        if (a == b || (a != null && a.equals(b))) {
            int netWorkState = NetUtils.getNetWorkState(context);
            if (LAST_TYPE != netWorkState) {
                LAST_TYPE = netWorkState;
                onNetChange(netWorkState);
            }
        }
    }
    //for network change


    //-------------[ Fields ]--------------------------//
    private final Object interLocker =  new Object();
    private long pid;
    private String uid;
    protected String lang;
    private String token;
    long ts = 0;
    private String curve;
    private String rtmEndpoint;
    private Context context;
    ErrorRecorder errorRecorder = new ErrorRecorder();

    private Map<String, String>  loginAttrs = new HashMap<>();
    private ClientStatus rttGateStatus = ClientStatus.Closed;
    private CloseType closedCase = CloseType.None;
    private int lastNetType = NetUtils.NETWORK_NOTINIT;
    private AtomicBoolean isRelogin = new AtomicBoolean(false);
    private AtomicBoolean running = new AtomicBoolean(true);
    private AtomicBoolean initCheckThread = new AtomicBoolean(false);
    private Thread checkThread;
    private RTMQuestProcessor processor;
//    private TCPClient dispatch;
    private TCPClient rttGate;
    private Map<String, Map<TCPClient, Long>> fileGates;
    private AtomicLong connectionId = new AtomicLong(0);
    private AtomicBoolean noNetWorkNotify = new AtomicBoolean(false);
    private RTMAnswer lastReloginAnswer = new RTMAnswer();
    private RTMPushProcessor serverPushProcessor;
    RTMUtils rtmUtils = new RTMUtils();

    final int okRet = RTMErrorCode.RTM_EC_OK.value();
    final int videoError = RTMErrorCode.RTM_EC_VIDEO_ERROR.value();
    final int voiceError = RTMErrorCode.RTM_EC_VOICE_ERROR.value();

    //voice
    //video
    public enum RTMModel{
        Normal,
        VOICE,
        VIDEO
    }


    private ArrayList<Integer> finishCodeList = new ArrayList<Integer>(){{
        add(RTMErrorCode.RTM_EC_INVALID_AUTH_TOEKN.value());
        add(RTMErrorCode.RTM_EC_PROJECT_BLACKUSER.value()); }};


    class RTMQuestProcessor{

        void rtmConnectClose() {
            if (serverPushProcessor != null)
                serverPushProcessor.rtmConnectClose(uid);
        }

        Answer recognizedResult(Quest quest, InetSocketAddress peer){
            rttGate.sendAnswer(new Answer(quest));
            String text = rtmUtils.wantString(quest, "asr");
            int streamId = rtmUtils.wantInt(quest, "streamId");

            serverPushProcessor.recognizedResult(streamId, text);
            return null;
        }

        Answer translatedResult(Quest quest, InetSocketAddress peer){
//            Log.i("sdktest","receive translatedResult");
            rttGate.sendAnswer(new Answer(quest));
            String text = rtmUtils.wantString(quest, "trans");
            int streamId = rtmUtils.wantInt(quest, "streamId");

            serverPushProcessor.translatedResult(streamId, text);
            return null;
        }

    }

    void  internalReloginCompleted(final String uid, final boolean successful, final int reloginCount){
        if (!successful){
        }
        serverPushProcessor.reloginCompleted(uid, successful, lastReloginAnswer, reloginCount);
    }

    void reloginEvent(int count){
        if (noNetWorkNotify.get()) {
            isRelogin.set(false);
            internalReloginCompleted(uid, false, count);
            return;
        }
//        isRelogin.set(true);
        int num = count;
        Map<String, String> kk = loginAttrs;
        if (serverPushProcessor.reloginWillStart(uid, num)) {
            lastReloginAnswer = login(token, ts);
            if(lastReloginAnswer.errorCode == okRet || lastReloginAnswer.errorCode == RTMErrorCode.RTM_EC_DUPLCATED_AUTH.value()) {
                isRelogin.set(false);
                internalReloginCompleted(uid, true, num);
                return;
            }
            else {
                if (finishCodeList.contains(lastReloginAnswer.errorCode)){
                    isRelogin.set(false);
                    internalReloginCompleted(uid, false, num);
                    return;
                }
                else {
                    if (num >= serverPushProcessor.internalReloginMaxTimes){
                        isRelogin.set(false);
                        internalReloginCompleted(uid, false, num);
                        return;
                    }
                    if (!isRelogin.get()) {
                        internalReloginCompleted(uid, false, num);
                        return;
                    }
                    try {
                        Thread.sleep(2 * 1000);
                    } catch (InterruptedException e) {
                        isRelogin.set(false);
                        internalReloginCompleted(uid, false, num);
                        return;
                    }
                    reloginEvent(++num);
                }
            }
        }
        else {
            isRelogin.set(false);
            internalReloginCompleted(uid, false, --num);
        }
    }

    public void onNetChange(int netWorkState){
        if (lastNetType != NetUtils.NETWORK_NOTINIT) {
            switch (netWorkState) {
                case NetUtils.NETWORK_NONE:
                    noNetWorkNotify.set(true);
//                    Log.e("sdktest","no network");
//                    if (isRelogin.get()){
//                        isRelogin.set(false);
//                    }
                    break;
                case NetUtils.NETWORK_MOBILE:
                case NetUtils.NETWORK_WIFI:
//                    Log.e("sdktest","have network");

                    if (rttGate == null)
                        return;
                    noNetWorkNotify.set(false);
                    if (lastNetType != netWorkState) {
                        if (isRelogin.get())
                            return;
                        close();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
//                        try {
//                            Thread.sleep(100);
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
                        isRelogin.set(true);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                reloginEvent(1);
//                                if (getClientStatus() == ClientStatus.Connected){
//                                    Quest quest = new Quest("bye");
//                                    sendQuest(quest, new FunctionalAnswerCallback() {
//                                        @Override
//                                        public void onAnswer(Answer answer, int errorCode) {
//                                            close();
//                                            try {
//                                                Thread.sleep(200);
//                                            } catch (InterruptedException e) {
//                                                e.printStackTrace();
//                                            }
//                                            reloginEvent(1);
//                                        }
//                                    }, 5);
//                                }
//                                else {
////                                    voiceClose();
//                                    reloginEvent(1);
//                                }
                            }
                        }).start();
                    }
                    break;
            }
        }
        lastNetType = netWorkState;
    }

    public  void setServerPushProcessor(RTMPushProcessor processor){
        this.serverPushProcessor = processor;
    }


    void RTMInit(String rtmendpoint, long pid, String uid, RTMPushProcessor serverPushProcessor, Context appcontext) {

        rtmUtils.errorRecorder = errorRecorder;
        this.rtmEndpoint = rtmendpoint;

        this.pid = pid;
        this.uid = uid;
        isRelogin.set(false);
        fileGates = new HashMap<>();
        processor = new RTMQuestProcessor();

        this.serverPushProcessor = serverPushProcessor;

        context = appcontext;
        ClientEngine.setMaxThreadInTaskPool(RTMConfig.globalMaxThread);

        try {
            //网络监听
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
            context.registerReceiver(this, intentFilter);

            addressSp = context.getSharedPreferences("Logindb",context.MODE_PRIVATE);
        }
        catch (Exception ex){
            ex.printStackTrace();
            errorRecorder.recordError("registerReceiver exception:" + ex.getMessage());
        }
    }

    long getPid() {
        return pid;
    }

    String getUid() {
        return uid;
    }


    synchronized protected ClientStatus getClientStatus() {
        synchronized (interLocker) {
            return rttGateStatus;
        }
    }

    RTMAnswer genRTMAnswer(int errCode){
        return genRTMAnswer(errCode,"");
    }

    RTMAnswer genRTMAnswer(int errCode,String msg)
    {
        RTMAnswer tt = new RTMAnswer();
        tt.errorCode = errCode;
        if (msg == null || msg.isEmpty())
            tt.errorMsg = RTMErrorCode.getMsg(errCode);
        else
            tt.errorMsg = msg;
        return tt;
    }

    private TCPClient getCoreClient() {
        synchronized (interLocker) {
            if (rttGateStatus == ClientStatus.Connected)
                return rttGate;
            else
                return null;
        }
    }


    RTMAnswer genRTMAnswer(Answer answer) {
        if (answer == null)
            return new RTMAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(), "invalid connection");
        return new RTMAnswer(answer.getErrorCode(),answer.getErrorMessage());
    }



    RTMAnswer genRTMAnswer(Answer answer, String msg) {
        if (answer == null)
            return new RTMAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(), "invalid connection");
        return new RTMAnswer(answer.getErrorCode(),answer.getErrorMessage() + " " + msg);
    }


    RTMAnswer genRTMAnswer(Answer answer,int errcode) {
        if (answer == null && errcode !=0) {
            if (errcode == ErrorCode.FPNN_EC_CORE_TIMEOUT.value())
                return new RTMAnswer(errcode, "FPNN_EC_CORE_TIMEOUT");
            else
                return new RTMAnswer(errcode,"fpnn  error");
        }
        else
            return new RTMAnswer(answer.getErrorCode(),answer.getErrorMessage());
    }

    void setCloseType(CloseType type)
    {
        closedCase = type;
    }

    void sayBye(final IRTMEmptyCallback callback) {
        closedCase = CloseType.ByUser;
        final TCPClient client = getCoreClient();
        if (client == null) {
            close();
            return;
        }
        Quest quest = new Quest("bye");
        sendQuest(quest, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                close();
                callback.onResult(genRTMAnswer(answer,errorCode));
            }
        }, 5);
    }

    void realClose(){
        closedCase = CloseType.ByUser;
        try {
            if (context != null)
                context.unregisterReceiver(this);
        } catch (IllegalArgumentException e){
        }
        close();
    }


    void sendQuest(Quest quest, final FunctionalAnswerCallback callback) {
        sendQuest(quest, callback, RTMConfig.globalQuestTimeoutSeconds);
    }

    Answer sendQuest(Quest quest) {
        return sendQuest(quest,RTMConfig.globalQuestTimeoutSeconds);
    }

    Answer sendQuest(Quest quest, int timeout) {
        Answer answer = new Answer(new Quest(""));
        TCPClient client = getCoreClient();
        if (client == null) {
            answer.fillErrorInfo(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(), "invalid connection");
        }else {
            try {
                answer = client.sendQuest(quest, timeout);
            } catch (Exception e) {
                if (errorRecorder != null)
                    errorRecorder.recordError(e);
                answer = new Answer(quest);
                answer.fillErrorInfo(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(), e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        return answer;
    }

    void sendQuest(Quest quest, final FunctionalAnswerCallback callback, int timeout) {
        TCPClient client = getCoreClient();
        final Answer answer = new Answer(quest);
        if (client == null) {
            answer.fillErrorInfo(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(),"invalid connection");
            callback.onAnswer(answer,answer.getErrorCode());//当前线程
            return;
        }
        if (timeout <= 0)
            timeout = RTMConfig.globalQuestTimeoutSeconds;
        try {
            client.sendQuest(quest, callback, timeout);
        }
        catch (Exception e){
            answer.fillErrorInfo(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),e.getMessage());
            callback.onAnswer(answer, answer.getErrorCode());
        }
    }

     byte[] shortArr2byteArr(short[] shortArr, int shortArrLen){
        byte[] byteArr = new byte[shortArrLen * 2];
        ByteBuffer.wrap(byteArr).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortArr);
        return byteArr;
    }


    void sendQuestEmptyCallback(final IRTMEmptyCallback callback, Quest quest) {
        sendQuest(quest, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                callback.onResult(genRTMAnswer(answer,errorCode));
            }
        }, RTMConfig.globalQuestTimeoutSeconds);
    }

    RTMAnswer sendQuestEmptyResult(Quest quest){
        Answer ret =  sendQuest(quest);
        if (ret == null)
            return genRTMAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(),"invalid connection");
        return genRTMAnswer(ret);
    }

    public void printLog(String msg){
//        Log.e("sdktest",msg);
        errorRecorder.recordError(msg);
    }

    boolean isAirplaneModeOn() {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,0) != 0;
    }

    boolean isNetWorkConnected() {
        boolean isConnected = false;
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeInfo = cm.getActiveNetworkInfo();
            if (activeInfo != null && activeInfo.isAvailable() && activeInfo.isConnected())
                isConnected = true;
        }
        return isConnected;
    }


    //-------------[ Auth(Login) utilies functions ]--------------------------//
    private void ConfigRtmGateClient(final TCPClient client) {
        client.setQuestTimeout(RTMConfig.globalQuestTimeoutSeconds);

        if (errorRecorder != null)
            client.setErrorRecorder(errorRecorder);

        client.setQuestProcessor(processor, "com.fpnn.rttsdk.RTMCore$RTMQuestProcessor");

        client.setWillCloseCallback(new ConnectionWillCloseCallback() {
            @Override
            public void connectionWillClose(InetSocketAddress peerAddress, int _connectionId,boolean causedByError) {
//                printLog("closedCase " + closedCase + " getClientStatus() " + getClientStatus());
                if (connectionId.get() != 0 && connectionId.get() == _connectionId && closedCase != CloseType.ByUser && closedCase != CloseType.ByServer && getClientStatus() != ClientStatus.Connecting) {
                    close();

                    processor.rtmConnectClose();

                    if (closedCase == CloseType.ByServer || isRelogin.get()) {
                        return;
                    }

                    if (isAirplaneModeOn()) {
                        return;
                    }

                    if(getClientStatus() == ClientStatus.Closed){
                        try {
                            Thread.sleep(2* 1000);//处理一些特殊情况
                            if (noNetWorkNotify.get()) {
                                return;
                            }
                            if (isRelogin.get() || getClientStatus() == ClientStatus.Connected) {
                                return;
                            }
                            isRelogin.set(true);
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    reloginEvent(1);
                                }
                            }).start();
                        }
                        catch (Exception e){
                            printLog(" relogin error " + e.getMessage());
                        }
                    }
                }
            }
        });
    }

    public void httpRequest(final String url){
        new Thread(new Runnable() {
            @Override
            public void run() {
                int resultCode = -1;
                try {
                    URL sendurl = new URL(url);
                    HttpsURLConnection conn = (HttpsURLConnection) sendurl.openConnection();
                    conn.setConnectTimeout(15 * 1000);//超时时间
                    conn.setReadTimeout(15 * 1000);
                    conn.setDoInput(true);
                    conn.setUseCaches(false);
                    conn.connect();
                    resultCode = conn.getResponseCode();
                }catch (Exception ex){
                    Log.i("rtmsdk","httprequest error " + resultCode);
                }
            }
        }).start();
    }


    private void test80(String ipaddres, final IRTMEmptyCallback callback){
        String realhost = ipaddres;
        if (ipaddres.isEmpty()) {
            realhost = rtmEndpoint.split(":")[0];
            if (realhost == null || realhost.isEmpty()) {
                callback.onResult(genRTMAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value()));
                return;
            }
        }

        rttGate = new TCPClient(realhost, 80);
        ConfigRtmGateClient(rttGate);
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        qt.param("version", "Android-" + RTMConfig.SDKVersion);
        qt.param("device", deviceid);

        if (loginAttrs != null)
            qt.param("attrs", loginAttrs);

        Answer answer = null;
        try {
            answer = rttGate.sendQuest(qt, RTMConfig.globalQuestTimeoutSeconds);
//            answer = new Answer(qt);
//            answer.fillErrorCode(FPNN_EC_CORE_INVALID_CONNECTION.value());
            if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()){
                String url = "https://" + rtmEndpoint.split(":")[0] + "/service/tcp-13321-fail-tcp-80-fail" + pid + "-" + uid;
                httpRequest(url);
                callback.onResult(genRTMAnswer(answer));
            }
            else{
                Quest quest = new Quest("adddebuglog");
                String msg = "pid:" + pid + " uid:"+uid +  " link 80 port ok";
                quest.param("msg",msg);
                quest.param("attrs","");
                rttGate.sendQuest(quest, new FunctionalAnswerCallback() {
                    @Override
                    public void onAnswer(Answer answer, int errorCode) {
                        Log.i("sdktest","hehehehe " + errorCode);
                    }
                });
                synchronized (interLocker) {
                    rttGateStatus = ClientStatus.Connected;
                }

                synchronized (addressSp){
                    SharedPreferences.Editor editor = addressSp.edit();
                    editor.putString("addressip",rttGate.peerAddress.getAddress().getHostAddress());
                    editor.commit();
                }

//                checkRoutineInit();
                connectionId.set(rttGate.getConnectionId());
                callback.onResult(genRTMAnswer(ErrorCode.FPNN_EC_OK.value()));
            }

        } catch (Exception e) {
            e.printStackTrace();
            callback.onResult(genRTMAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_METHOD.value()));
        }
    }


    private RTMAnswer test80(String ipaddres){
        String realhost = ipaddres;
        if (ipaddres.isEmpty()) {
            String linkEndpoint = rttGate.endpoint();
            realhost = linkEndpoint.split(":")[0];
            if (realhost == null || realhost.isEmpty())
                return genRTMAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value());
        }

        rttGate = new TCPClient(realhost, 80);
        ConfigRtmGateClient(rttGate);
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        qt.param("version", "Android-" + RTMConfig.SDKVersion);
        qt.param("device", deviceid);

        if (loginAttrs != null)
            qt.param("attrs", loginAttrs);

        Answer answer = null;
        try {
            answer = rttGate.sendQuest(qt, RTMConfig.globalQuestTimeoutSeconds);
//            answer = new Answer(qt);
//            answer.fillErrorCode(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value());
            if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()){
                String url = "https://" + rtmEndpoint.split(":")[0] + "/service/tcp-13321-fail-tcp-80-fail" + pid + "-" + uid;
                httpRequest(url);
                return genRTMAnswer(answer);
            }
            else {
                Quest quest = new Quest("adddebuglog");
                String msg = "pid:" + pid + " uid:"+uid +  "link 80 port ok";
                quest.param("msg",msg);
                quest.param("attrs","");
                rttGate.sendQuest(quest, new FunctionalAnswerCallback() {
                    @Override
                    public void onAnswer(Answer answer, int errorCode) {

                    }
                });

                synchronized (interLocker) {
                    rttGateStatus = ClientStatus.Connected;
                }
//                checkRoutineInit();
                connectionId.set(rttGate.getConnectionId());
                synchronized (addressSp){
                    SharedPreferences.Editor editor = addressSp.edit();
                    editor.putString("addressip",rttGate.peerAddress.getAddress().getHostAddress());
                    editor.commit();
                }
                return genRTMAnswer(ErrorCode.FPNN_EC_OK.value());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return genRTMAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_METHOD.value());
        }
    }

    //------------voice add---------------//
    private RTMAnswer auth(String token, long ts, Map<String, String> attr, boolean retry) {
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        String sharedip = "";

        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        qt.param("tokenType", 1);
        qt.param("ts", ts);
        qt.param("version", "Android-RTV" + RTMConfig.SDKVersion);

        if (attr != null)
            qt.param("attrs", attr);
        try {
            Answer answer = rttGate.sendQuest(qt, RTMConfig.globalQuestTimeoutSeconds);
//            Answer answer = new Answer(qt);
//            answer.fillErrorCode(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value());
            if (answer  == null || answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()) {
                closeStatus();
                if (retry)
                    return test80(sharedip);
                if (answer != null && answer.getErrorMessage().indexOf("Connection open channel failed") != -1){
                    InetSocketAddress peeraddres = rttGate.peerAddress;
                    if (peeraddres != null){
                        boolean isnetwork = isNetWorkConnected();
                        String hostname = rtmEndpoint.split(":")[0];
                        if (peeraddres.getHostString().equals(hostname) && isnetwork && addressSp != null){
                            synchronized (addressSp){
                                sharedip = addressSp.getString("addressip", "");
                            }
                            if (!sharedip.isEmpty()) {
                                rttGate = new TCPClient(sharedip, peeraddres.getPort());
                                ConfigRtmGateClient(rttGate);
                                return auth(token, ts, attr,true);
                            }
                        }
                        if (!isnetwork)
                            return genRTMAnswer(answer,"when send sync auth  failed:no network ");
                        else {
                            return genRTMAnswer(answer, "when send sync auth  rttGate parse endpoint " + peeraddres.getHostString());
                        }
                    }
                    else
                        return genRTMAnswer(answer,"when send sync auth  parse address is null");
                }
                else if (answer != null && answer.getErrorCode() == FPNN_EC_CORE_INVALID_CONNECTION.value())
                {
                    return test80(sharedip);
//                    return genRTMAnswer(answer,"when send sync auth ");
                }
                else
                    return genRTMAnswer(answer,"when send sync auth ");

            }
            synchronized (interLocker) {
                rttGateStatus = ClientStatus.Connected;
            }
//            checkRoutineInit();
            connectionId.set(rttGate.getConnectionId());
            synchronized (addressSp){
                SharedPreferences.Editor editor = addressSp.edit();
                editor.putString("addressip",rttGate.peerAddress.getAddress().getHostAddress());
                editor.commit();
            }

            return genRTMAnswer(answer);
        }
        catch (Exception  ex){
            closeStatus();
            return genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),ex.getMessage());
        }
    }

    private void auth(final IRTMEmptyCallback callback, final String token, final long ts, final boolean retry) {
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        final Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("lang", lang);
        qt.param("tokenType", 1);
        qt.param("ts", ts);
        qt.param("version", "Android-" + RTMConfig.SDKVersion);

        rttGate.sendQuest(qt, new FunctionalAnswerCallback() {
            @SuppressLint("NewApi")
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                try {
                    String sharedip = "";
//                    rttGate.close();
//                    answer = new Answer(qt);
//                    answer.fillErrorCode(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value());
//                    errorCode = ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value();

                    if (answer == null || errorCode != ErrorCode.FPNN_EC_OK.value()) {
                        closeStatus();
                        if (retry) {
                            test80(sharedip, callback);
//                            callback.onResult(genRTMAnswer(answer, "retry failed when send async auth "));
                            return;
                        }
                        if (answer!= null && answer.getErrorMessage().indexOf("Connection open channel failed") != -1){
                            InetSocketAddress peeraddres = rttGate.peerAddress;
                            if (peeraddres != null){
                                boolean isnetwork = isNetWorkConnected();
                                String hostname = rtmEndpoint.split(":")[0];
                                if (peeraddres.getHostString().equals(hostname) && isnetwork && addressSp != null){
                                    synchronized (addressSp){
                                        sharedip = addressSp.getString("addressip", "");
                                    }
                                    rttGate.peerAddress = new InetSocketAddress(sharedip, peeraddres.getPort());
                                    auth(callback, token, ts,true);
                                    return;
                                }
                                if (!isnetwork)
                                    callback.onResult(genRTMAnswer( errorCode, "when send async auth   failed:no network "  + answer.getErrorMessage()));
                                else
                                    callback.onResult(genRTMAnswer( errorCode, "when send async auth " + answer.getErrorMessage() + " parse address:" + peeraddres.getHostString()));
                            }
                            else
                                callback.onResult(genRTMAnswer( errorCode, "when send async auth " + answer.getErrorMessage() + "peeraddres is null"));
                            return;
                        }
                        else
                        {
//                            test80(sharedip, callback);
                            callback.onResult(genRTMAnswer( answer, "when send async auth " + answer.getErrorMessage()));
                            return;
                        }
                    } else {
                        synchronized (interLocker) {
                            rttGateStatus = ClientStatus.Connected;
                        }

                        synchronized (addressSp){
                            SharedPreferences.Editor editor = addressSp.edit();
                            editor.putString("addressip",rttGate.peerAddress.getAddress().getHostAddress());
                            editor.commit();
                        }

//                        checkRoutineInit();
                        connectionId.set(rttGate.getConnectionId());
                        callback.onResult(genRTMAnswer(errorCode));
                    }
                }
                catch (Exception e){
                    callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),"when async auth " + e.getMessage()));
                }
            }
        }, RTMConfig.globalQuestTimeoutSeconds);
    }


    void login(final IRTMEmptyCallback callback, long ts, final String token) {
        if (token ==null || token.isEmpty()){
            callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value()," token  is null or empty"));
            return;
        }

        String errDesc = "";
        if (rtmEndpoint == null || rtmEndpoint.isEmpty() || rtmEndpoint.lastIndexOf(':') == -1)
            errDesc = "invalid rtmEndpoint:" + rtmEndpoint;
        if (pid <= 0)
            errDesc += " pid is invalid:" + pid;
        if (serverPushProcessor == null)
            errDesc += " RTMMPushProcessor is null";

        if (!errDesc.equals("")) {
            callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(), errDesc));
            return;
        }

            if (rttGateStatus == ClientStatus.Connected || rttGateStatus == ClientStatus.Connecting) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_OK.value()));
                    }
                }).start();
                return;
            }
        synchronized (interLocker) {
            rttGateStatus = ClientStatus.Connecting;
        }

        if (rttGate != null) {
            rttGate.close();
            auth(callback, token,ts, false);
        } else {
            try {
                rttGate = TCPClient.create(rtmEndpoint);
            }
            catch (IllegalArgumentException ex){
                callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),"create rtmgate error endpoint Illegal:" +ex.getMessage() + " :" +  rtmEndpoint ));
                return;
            }
            catch (Exception e){
                String msg = "create rtmgate error orginal error:" + e.getMessage() + " endpoint: " + rtmEndpoint;
                if (rttGate != null)
                    msg = msg + " parse endpoint " + rttGate.endpoint();
                callback.onResult(genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),msg ));
                return;
            }
            this.token = token;
            this.ts = ts;
            if (lang == null)
                this.lang = "";
            closedCase = CloseType.None;
            ConfigRtmGateClient(rttGate);
            auth(callback, token, ts, false);
        }
    }

    private  void closeStatus()
    {
        synchronized (interLocker) {
            rttGateStatus = ClientStatus.Closed;
        }
    }

    RTMAnswer login(String token, long ts) {
        if (token == null || token.isEmpty())
            return genRTMAnswer(RTMErrorCode.RTM_EC_INVALID_AUTH_TOEKN.value(), "login failed token  is null or empty");

        String errDesc = "";
        if (rtmEndpoint == null || rtmEndpoint.isEmpty() || rtmEndpoint.lastIndexOf(':') == -1)
            errDesc = "invalid rtmEndpoint:" + rtmEndpoint;
        if (pid <= 0)
            errDesc += " pid is invalid:" + pid;
        if (serverPushProcessor == null)
            errDesc += " RTMMPushProcessor is null";

        if (!errDesc.equals("")) {
            return genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(), errDesc);
        }

        synchronized (interLocker) {
            if (rttGateStatus == ClientStatus.Connected || rttGateStatus == ClientStatus.Connecting)
                return genRTMAnswer(ErrorCode.FPNN_EC_OK.value());

            rttGateStatus = ClientStatus.Connecting;
        }

        if (rttGate != null) {
            rttGate.close();
            return auth(token, ts,null,false);
        } else {
            try {
                rttGate = TCPClient.create(rtmEndpoint);
            }
            catch (IllegalArgumentException ex){
                return genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),"create rtmgate error endpoint Illegal:" +ex.getMessage() + " :" +  rtmEndpoint );
            }
            catch (Exception e){
                String msg = "create rtmgate error orginal error:" + e.getMessage() + " endpoint: " + rtmEndpoint;
                if (rttGate != null)
                    msg = msg + " parse endpoint " + rttGate.endpoint();
                return genRTMAnswer(RTMErrorCode.RTM_EC_UNKNOWN_ERROR.value(),msg );
            }
            this.token = token;
            this.ts = ts;
            if (lang == null)
                this.lang = "";
            this.token =  token;
            closedCase = CloseType.None;
            ConfigRtmGateClient(rttGate);
            return auth(token, ts, null, false);
        }
    }



    public void close() {
        if (isRelogin.get()) {
            return;
        }
        synchronized (interLocker) {
            initCheckThread.set(false);
            running.set(false);
            fileGates.clear();
            if (rttGateStatus == ClientStatus.Closed) {
                return;
            }
            rttGateStatus = ClientStatus.Closed;
        }
        if (rttGate !=null)
            rttGate.close();
    }
}
