package com.fpnn.rtvtsdk;

import android.annotation.SuppressLint;
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

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.HttpsURLConnection;
import com.fpnn.rtvtsdk.RTVTUserInterface.*;
import com.fpnn.rtvtsdk.RTVTStruct.*;
import static com.fpnn.sdk.ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION;

class RTVTCore extends BroadcastReceiver{

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
    int globalQuestTimeoutSeconds = 30;
    int globalMaxThread = 8;
    String SDKVersion = "0.0.1";



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
    private String loginToken;
    private long loginTs = 0;
    private String rtvtEndpoint;
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
    private RTVTQuestProcessor processor;
//    private TCPClient dispatch;
    private TCPClient rttGate;
    private Map<String, Map<TCPClient, Long>> fileGates;
    private AtomicLong connectionId = new AtomicLong(0);
    private AtomicBoolean noNetWorkNotify = new AtomicBoolean(false);
    private RTVTAnswer lastReloginAnswer = new RTVTAnswer();
    private RTVTPushProcessor serverPushProcessor;
    RTVTUtils rtvtUtils = new RTVTUtils();

    final int okRet = ErrorCode.FPNN_EC_OK.value();

    //voice
    //video
    public enum RTVTModel{
        Normal,
        VOICE,
        VIDEO
    }

    class RTVTQuestProcessor{

        void rtvtConnectClose() {
            if (serverPushProcessor != null)
                serverPushProcessor.rtvtConnectClose(uid);
        }

        Answer recognizedResult(Quest quest, InetSocketAddress peer){
            rttGate.sendAnswer(new Answer(quest));
            String text = rtvtUtils.wantString(quest, "asr");
            int streamId = rtvtUtils.wantInt(quest, "streamId");
            int startTs = rtvtUtils.wantInt(quest, "startTs");
            int endTs = rtvtUtils.wantInt(quest, "endTs");
            int recTs = rtvtUtils.wantInt(quest, "recTs");

            serverPushProcessor.recognizedResult(streamId, startTs,endTs , recTs,text);
            return null;
        }

        Answer translatedResult(Quest quest, InetSocketAddress peer){
//            Log.i("sdktest","receive translatedResult");
            rttGate.sendAnswer(new Answer(quest));
            String text = rtvtUtils.wantString(quest, "trans");
            int streamId = rtvtUtils.wantInt(quest, "streamId");
            int startTs = rtvtUtils.wantInt(quest, "startTs");
            int endTs = rtvtUtils.wantInt(quest, "endTs");
            int recTs = rtvtUtils.wantInt(quest, "recTs");

            serverPushProcessor.translatedResult(streamId, startTs,endTs , recTs, text);
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
            lastReloginAnswer = auth(loginToken, loginTs, false);
            if(lastReloginAnswer.errorCode == okRet) {
                isRelogin.set(false);
                internalReloginCompleted(uid, true, num);
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

    public  void setServerPushProcessor(RTVTPushProcessor processor){
        this.serverPushProcessor = processor;
    }


    void RTVTInit(String rtvtendpoint, long pid, String uid, RTVTPushProcessor serverPushProcessor, Context appcontext) {

        rtvtUtils.errorRecorder = errorRecorder;
        this.rtvtEndpoint = rtvtendpoint;

        this.pid = pid;
        this.uid = uid;
        isRelogin.set(false);
        fileGates = new HashMap<>();
        processor = new RTVTQuestProcessor();

        this.serverPushProcessor = serverPushProcessor;

        context = appcontext.getApplicationContext();
        ClientEngine.setMaxThreadInTaskPool(globalMaxThread);

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

    RTVTAnswer genRTVTAnswer(int errCode){
        return genRTVTAnswer(errCode,"");
    }

    RTVTAnswer genRTVTAnswer(int errCode,String msg)
    {
        RTVTAnswer tt = new RTVTAnswer();
        tt.errorCode = errCode;
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


    RTVTAnswer genRTVTAnswer(Answer answer) {
        if (answer == null)
            return new RTVTAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(), "invalid connection");
        return new RTVTAnswer(answer.getErrorCode(),answer.getErrorMessage());
    }



    RTVTAnswer genRTVTAnswer(Answer answer, String msg) {
        if (answer == null)
            return new RTVTAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(), "invalid connection");
        return new RTVTAnswer(answer.getErrorCode(),answer.getErrorMessage() + " " + msg);
    }


    RTVTAnswer genRTVTAnswer(Answer answer,int errcode) {
        if (answer == null && errcode !=0) {
            if (errcode == ErrorCode.FPNN_EC_CORE_TIMEOUT.value())
                return new RTVTAnswer(errcode, "FPNN_EC_CORE_TIMEOUT");
            else
                return new RTVTAnswer(errcode,"fpnn  error");
        }
        else
            return new RTVTAnswer(answer.getErrorCode(),answer.getErrorMessage());
    }

    void setCloseType(CloseType type)
    {
        closedCase = type;
    }

    void sayBye(final IRTVTEmptyCallback callback) {
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
                callback.onResult(genRTVTAnswer(answer,errorCode));
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
        sendQuest(quest, callback, globalQuestTimeoutSeconds);
    }

    Answer sendQuest(Quest quest) {
        return sendQuest(quest,globalQuestTimeoutSeconds);
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
                answer.fillErrorInfo(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(), e.getMessage());
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
            timeout = globalQuestTimeoutSeconds;
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


    void sendQuestEmptyCallback(final IRTVTEmptyCallback callback, Quest quest) {
        sendQuest(quest, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                callback.onResult(genRTVTAnswer(answer,errorCode));
            }
        }, globalQuestTimeoutSeconds);
    }

    RTVTAnswer sendQuestEmptyResult(Quest quest){
        Answer ret =  sendQuest(quest);
        if (ret == null)
            return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value(),"invalid connection");
        return genRTVTAnswer(ret);
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
        client.setQuestTimeout(globalQuestTimeoutSeconds);

        if (errorRecorder != null)
            client.setErrorRecorder(errorRecorder);

        client.setQuestProcessor(processor, "com.fpnn.rtvtsdk.RTVTCore$RTVTQuestProcessor");

        client.setWillCloseCallback(new ConnectionWillCloseCallback() {
            @Override
            public void connectionWillClose(InetSocketAddress peerAddress, int _connectionId,boolean causedByError) {
//                printLog("closedCase " + closedCase + " getClientStatus() " + getClientStatus());
                if (connectionId.get() != 0 && connectionId.get() == _connectionId && closedCase != CloseType.ByUser && closedCase != CloseType.ByServer && getClientStatus() != ClientStatus.Connecting) {
                    close();

                    processor.rtvtConnectClose();

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
                    Log.i("rtvtsdk","httprequest error " + resultCode);
                }
            }
        }).start();
    }


    private void test80(String ipaddres, final IRTVTEmptyCallback callback){
        String realhost = ipaddres;
        if (ipaddres.isEmpty()) {
            realhost = rtvtEndpoint.split(":")[0];
            if (realhost == null || realhost.isEmpty()) {
                callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value()));
                return;
            }
        }

        rttGate = new TCPClient(realhost, 80);
        ConfigRtmGateClient(rttGate);
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", loginToken);
        qt.param("version", "Android-" + SDKVersion);
        qt.param("device", deviceid);

        if (loginAttrs != null)
            qt.param("attrs", loginAttrs);

        Answer answer = null;
        try {
            answer = rttGate.sendQuest(qt, globalQuestTimeoutSeconds);
//            answer = new Answer(qt);
//            answer.fillErrorCode(FPNN_EC_CORE_INVALID_CONNECTION.value());
            if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()){
                String url = "https://" + rtvtEndpoint.split(":")[0] + "/service/tcp-13321-fail-tcp-80-fail" + pid + "-" + uid;
                httpRequest(url);
                callback.onResult(genRTVTAnswer(answer));
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
                callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_OK.value()));
            }

        } catch (Exception e) {
            e.printStackTrace();
            callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_METHOD.value()));
        }
    }


    private RTVTAnswer test80(String ipaddres){
        String realhost = ipaddres;
        if (ipaddres.isEmpty()) {
            String linkEndpoint = rttGate.endpoint();
            realhost = linkEndpoint.split(":")[0];
            if (realhost == null || realhost.isEmpty())
                return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value());
        }

        rttGate = new TCPClient(realhost, 80);
        ConfigRtmGateClient(rttGate);
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", loginToken);
        qt.param("version", "AndroidRTVT-" + SDKVersion);
        qt.param("device", deviceid);

        if (loginAttrs != null)
            qt.param("attrs", loginAttrs);

        Answer answer = null;
        try {
            answer = rttGate.sendQuest(qt, globalQuestTimeoutSeconds);
//            answer = new Answer(qt);
//            answer.fillErrorCode(ErrorCode.FPNN_EC_CORE_INVALID_CONNECTION.value());
            if (answer.getErrorCode() != ErrorCode.FPNN_EC_OK.value()){
                String url = "https://" + rtvtEndpoint.split(":")[0] + "/service/tcp-13321-fail-tcp-80-fail" + pid + "-" + uid;
                httpRequest(url);
                return genRTVTAnswer(answer);
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
                return genRTVTAnswer(ErrorCode.FPNN_EC_OK.value());
            }

        } catch (Exception e) {
            e.printStackTrace();
            return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_METHOD.value());
        }
    }

    //------------voice add---------------//
    private RTVTAnswer auth(String token , long ts, boolean retry) {
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        String sharedip = "";

        Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("ts", ts);
        qt.param("version", "AndroidRTVT-" + SDKVersion);

        try {
            Answer answer = rttGate.sendQuest(qt, globalQuestTimeoutSeconds);
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
                        String hostname = rtvtEndpoint.split(":")[0];
                        if (peeraddres.getHostString().equals(hostname) && isnetwork && addressSp != null){
                            synchronized (addressSp){
                                sharedip = addressSp.getString("addressip", "");
                            }
                            if (!sharedip.isEmpty()) {
                                rttGate = new TCPClient(sharedip, peeraddres.getPort());
                                ConfigRtmGateClient(rttGate);
                                return auth(token, ts,true);
                            }
                        }
                        if (!isnetwork)
                            return genRTVTAnswer(answer,"when send sync auth  failed:no network ");
                        else {
                            return genRTVTAnswer(answer, "when send sync auth  rttGate parse endpoint " + peeraddres.getHostString());
                        }
                    }
                    else
                        return genRTVTAnswer(answer,"when send sync auth  parse address is null");
                }
                else if (answer != null && answer.getErrorCode() == FPNN_EC_CORE_INVALID_CONNECTION.value())
                {
                    return test80(sharedip);
//                    return genRTVTAnswer(answer,"when send sync auth ");
                }
                else
                    return genRTVTAnswer(answer,"when send sync auth ");

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

            return genRTVTAnswer(answer);
        }
        catch (Exception  ex){
            closeStatus();
            return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),ex.getMessage());
        }
    }

    private void auth(final IRTVTEmptyCallback callback, final String token, final long ts, final boolean retry) {
        String deviceid = Build.BRAND + "-" + Build.MODEL;
        final Quest qt = new Quest("login");
        qt.param("pid", pid);
        qt.param("uid", uid);
        qt.param("token", token);
        qt.param("ts", ts);
        qt.param("version", "AndroidRTVT-" + SDKVersion);

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
//                            callback.onResult(genRTVTAnswer(answer, "retry failed when send async auth "));
                            return;
                        }
                        if (answer!= null && answer.getErrorMessage().indexOf("Connection open channel failed") != -1){
                            InetSocketAddress peeraddres = rttGate.peerAddress;
                            if (peeraddres != null){
                                boolean isnetwork = isNetWorkConnected();
                                String hostname = rtvtEndpoint.split(":")[0];
                                if (peeraddres.getHostString().equals(hostname) && isnetwork && addressSp != null){
                                    synchronized (addressSp){
                                        sharedip = addressSp.getString("addressip", "");
                                    }
                                    rttGate.peerAddress = new InetSocketAddress(sharedip, peeraddres.getPort());
                                    auth(callback, token, ts,true);
                                    return;
                                }
                                if (!isnetwork)
                                    callback.onResult(genRTVTAnswer( errorCode, "when send async auth   failed:no network "  + answer.getErrorMessage()));
                                else
                                    callback.onResult(genRTVTAnswer( errorCode, "when send async auth " + answer.getErrorMessage() + " parse address:" + peeraddres.getHostString()));
                            }
                            else
                                callback.onResult(genRTVTAnswer( errorCode, "when send async auth " + answer.getErrorMessage() + "peeraddres is null"));
                            return;
                        }
                        else
                        {
//                            test80(sharedip, callback);
                            callback.onResult(genRTVTAnswer( answer, "when send async auth " + answer.getErrorMessage()));
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
                        callback.onResult(genRTVTAnswer(errorCode));
                    }
                }
                catch (Exception e){
                    callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),"when async auth " + e.getMessage()));
                }
            }
        }, globalQuestTimeoutSeconds);
    }


    void login(final IRTVTEmptyCallback callback, final String secretKey) {
        if (secretKey ==null || secretKey.isEmpty()){
            callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),"login failed secretKey  is null or empty"));
            return;
        }

        String errDesc = "";
        if (rtvtEndpoint == null || rtvtEndpoint.isEmpty() || rtvtEndpoint.lastIndexOf(':') == -1)
            errDesc = "login failed invalid rtvtEndpoint:" + rtvtEndpoint;
        if (pid <= 0)
            errDesc += "login failed pid is invalid:" + pid;
        if (serverPushProcessor == null)
            errDesc += "login failed RTVTMPushProcessor is null";

        if (!errDesc.equals("")) {
            callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(), errDesc));
            return;
        }

            if (rttGateStatus == ClientStatus.Connected || rttGateStatus == ClientStatus.Connecting) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_OK.value()));
                    }
                }).start();
                return;
            }
        synchronized (interLocker) {
            rttGateStatus = ClientStatus.Connecting;
        }

        long ts = System.currentTimeMillis()/1000;
        String realToken = ApiSecurityExample.genToken(pid, secretKey);
        this.loginToken = realToken;
        this.loginTs = ts;

        if (rttGate != null) {
            rttGate.close();
            auth(callback, realToken,ts, false);
        } else {
            try {
                rttGate = TCPClient.create(rtvtEndpoint);
            }
            catch (IllegalArgumentException ex){
                callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),"create rtvtgate error endpoint Illegal:" +ex.getMessage() + " :" +  rtvtEndpoint ));
                return;
            }
            catch (Exception e){
                String msg = "create rtvtgate error orginal error:" + e.getMessage() + " endpoint: " + rtvtEndpoint;
                if (rttGate != null)
                    msg = msg + " parse endpoint " + rttGate.endpoint();
                callback.onResult(genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),msg ));
                return;
            }

            closedCase = CloseType.None;
            ConfigRtmGateClient(rttGate);
            auth(callback, realToken, ts, false);
        }
    }

    private  void closeStatus()
    {
        synchronized (interLocker) {
            rttGateStatus = ClientStatus.Closed;
        }
    }

    RTVTAnswer login(String secretKey) {

        if (secretKey == null || secretKey.isEmpty())
            return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(), "login failed secretKey  is null or empty");

        String errDesc = "";
        if (rtvtEndpoint == null || rtvtEndpoint.isEmpty() || rtvtEndpoint.lastIndexOf(':') == -1)
            errDesc = " login failed invalid rtvtEndpoint:" + rtvtEndpoint;
        if (pid <= 0)
            errDesc += " login failed pid is invalid:" + pid;
        if (serverPushProcessor == null)
            errDesc += " login failed RTVTMPushProcessor is null";

        if (!errDesc.equals("")) {
            return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(), errDesc);
        }

        synchronized (interLocker) {
            if (rttGateStatus == ClientStatus.Connected || rttGateStatus == ClientStatus.Connecting)
                return genRTVTAnswer(ErrorCode.FPNN_EC_OK.value());

            rttGateStatus = ClientStatus.Connecting;
        }

        long ts = System.currentTimeMillis()/1000;
        String realToken = ApiSecurityExample.genToken(pid, secretKey);
        this.loginToken = realToken;
        this.loginTs = ts;

        if (rttGate != null) {
            rttGate.close();
            return auth(realToken, ts,false);
        } else {
            try {
                rttGate = TCPClient.create(rtvtEndpoint);
            }
            catch (IllegalArgumentException ex){
                return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),"create rtvtgate error endpoint Illegal:" +ex.getMessage() + " :" +  rtvtEndpoint );
            }
            catch (Exception e){
                String msg = "create rtvtgate error orginal error:" + e.getMessage() + " endpoint: " + rtvtEndpoint;
                if (rttGate != null)
                    msg = msg + " parse endpoint " + rttGate.endpoint();
                return genRTVTAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_ERROR.value(),msg );
            }

            closedCase = CloseType.None;
            ConfigRtmGateClient(rttGate);
            return auth(realToken, ts, false);
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
