package com.example.realtimeaudiotranslate;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import org.angmarch.views.NiceSpinner;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.fpnn.rtvtsdk.RTVTClient;
import com.fpnn.rtvtsdk.RTVTPushProcessor;
import com.fpnn.rtvtsdk.RTVTStruct;
import com.fpnn.rtvtsdk.RTVTUserInterface;
import com.fpnn.rtvtsdk.RTVTUtils;
import com.fpnn.sdk.FunctionalAnswerCallback;
import com.fpnn.sdk.TCPClient;
import com.fpnn.sdk.proto.Answer;
import com.fpnn.sdk.proto.Quest;
import com.livedata.rtc.RTCEngine;

public class MainActivity extends Activity {
    ArrayList<String> realLog =  new ArrayList<>();

    Context mycontext;
    void addlog(String msg){
        String realmsg = "[" + (new SimpleDateFormat("MM-dd HH:mm:ss.SSS").format(new Date())) + "] " + msg + "\n";
        synchronized (realLog){
            realLog.add(0,realmsg);
        }
    }

    LinkedList<CItem> langcode = new LinkedList<CItem>(){{
    }
    };
    static class CItem {
        public String ID = "";
        public String Value = "";

        public boolean equals(Object obj) {
            if (obj instanceof  CItem) {
                if (this.ID.equals(((CItem) obj).ID) && this.Value.equals(((CItem) obj).Value)) {
                    return true;
                }
                else {
                    return false;
                }
            }
            return false;
        }

        public CItem(String _ID, String _Value) {
            ID = _ID;
            Value = _Value;
        }

        @Override
        public String toString() {
            // TODO Auto-generated method stub
            return ID;
        }

        public String getID() {
            return ID;
        }

        public String getValue() {
            return Value;
        }
    }


    class demoPush extends RTVTPushProcessor {
        @Override
        public boolean reloginWillStart(String uid, int reloginCount) {
            return  true;
        }

        @Override
        public void reloginCompleted(String uid, boolean successful, RTVTStruct.RTVTAnswer answer, int reloginCount) {
            showToast(MainActivity.this, "重连结果 " + answer.getErrInfo());
            if (answer.errorCode == 0){
                addlog("rtvt 重连成功");
                if (streamId == 0)
                    return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startTest();
                    }
                });
            }else{
                addlog("rtvt 重连失败:" + answer.getErrInfo());
            }
        }


        @Override
        public void recognizedResult(int streamId, int startTs, int endTs, int recTs, String srcVoiceText) {
            mylog.log("stream id:" + streamId + " srctext:" + srcVoiceText);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    srcarrayList.add(srcVoiceText);
                    srcadapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void translatedResult(int streamId, int startTs, int endTs, int recTs, String destVoiceText) {
            mylog.log("stream id:" + streamId + " desttext:" + destVoiceText);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    destarrayList.add(destVoiceText);
                    destadapter.notifyDataSetChanged();

                }
            });
        }

        @Override
        public void rtvtConnectClose(String uid) {
            addlog("rtvt closed");
            showToast(MainActivity.this, "rtvt closed");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopAudio();
                }
            });
        }
    }

    final HashMap<String, CItem> allLanguage = new HashMap<String,CItem>(){{
        put("zh",new CItem("简体中文","zh"));
        put("en",new CItem("英语","en"));
        put("ja",new CItem("日语","ja"));
        put("th",new CItem("泰语","th"));
        put("id",new CItem("印尼语","id"));
        put("es",new CItem("西语","es"));
        put("vi",new CItem("越南语","vi"));
        put("hi",new CItem("印地语","hi"));
        put("ar",new CItem("阿语","ar"));
        put("ms",new CItem("马来语","ms"));
    }
    };

    ArrayList<String> srcarrayList = new ArrayList<>();
    ArrayAdapter srcadapter;

    ArrayList<String> destarrayList = new ArrayList<>();
    ArrayAdapter destadapter;
    int REQUEST_CODE_CONTACT = 101;

    int readLen = 640;
    long pid = 90008000;
    String  endpoint = "rtvt.ilivedata.com:14001";
//    String  endpoint = "69.234.232.26:14001";
    String uid = "hello";
    String mykey = "cXdlcnR5";
    Button start;
    Button end;
    Button logbtn;
    Button login;
    Button quit;
    long streamId = 0;
    long seq = 0;
    AudioTrackManager audioTrackManager;
    RTVTClient client;
    Timer timer = null;
    byte [] pcmData = null;
    MediaPlayer mediaPlayer = new MediaPlayer();
    volatile  boolean running = true;
    ListView srcview;
    ListView destview;
    NiceSpinner srcspinner;
    NiceSpinner destspinner;
    NiceSpinner testtype;
    int fdopen = 0;


    private void stopTimer(){
        if (timer != null){
            timer.cancel();
        }
    }

    private void stopPlay(){

        try {
//            audioTrackManager.release();
            mediaPlayer.stop();
            mediaPlayer.release();
        }
        catch (Exception e){
        }
    }

    private String setPCMData(String name){
        InputStream inputStream = null;

        try {
            inputStream = getAssets().open(name);
        } catch (IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
//        pcmData = toByteArray(inputStream);
        byte[] wavdata = toByteArray(inputStream);
        pcmData = new byte[wavdata.length - 44];
        System.arraycopy(wavdata, 44,pcmData,0,wavdata.length - 44);
        return "";
    }



    FileOutputStream outfile;

    {
        try {
            outfile = new FileOutputStream("/sdcard/Download/rtvt.pcm1");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void voiceCallback(byte[] pcmdata){
//        try {
//            outfile.write(pcmdata);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        mylog.log(" data len " + pcmdata.length);
        client.sendVoice(streamId, ++seq, pcmdata, System.currentTimeMillis(), new RTVTUserInterface.IRTVTEmptyCallback(){
            @Override
            public void onResult(RTVTStruct.RTVTAnswer answer) {
                if(answer.errorCode != 0){
                    mylog.log("sendVoice error " + answer.getErrInfo());
                }
            }
        });
    }

    private void stopAudio() {
        stopTimer();
        stopPlay();
        seq = 0;
        running = false;
        srcadapter.clear();
        destadapter.clear();
        timer = new Timer();
        RTCEngine.setVoiceStat(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void startTest(){
        stopAudio();

        String srclang = ((CItem)(srcspinner.getSelectedItem())).getValue();
        String destlang = ((CItem)(destspinner.getSelectedItem())).getValue();
        String type = ((CItem)(testtype.getSelectedItem())).getValue();
        showToast(MainActivity.this, "开始测试");
        String msg = "";
        if (type.equals("0")){
            String playName = srclang + ".wav";
            msg = setPCMData(playName);

            if (!msg.isEmpty()){
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setMessage("读取文件错误:" + msg).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
                return;
            }

            final int[] offset = {0};
            RTVTStruct.VoiceStream voiceStream = client.startTranslate(srclang, destlang, true);
            if (voiceStream.errorCode == 0){
                streamId = voiceStream.streamId;
                byte[] voicedatatmp = new byte[readLen];
                final int[] i = {0};
                addlog("startTranslate ok");

                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (offset[0] + readLen > pcmData.length)
                            return;
                        i[0] +=1;
                        System.arraycopy(pcmData, offset[0],voicedatatmp,0,readLen);

//                              try {
//                                outfile.write(voicedatatmp);
//                            } catch (IOException e) {
//                                e.printStackTrace();
//                            }

                        client.sendVoice(streamId, ++seq, voicedatatmp, System.currentTimeMillis(), new RTVTUserInterface.IRTVTEmptyCallback(){
                            @Override
                            public void onResult(RTVTStruct.RTVTAnswer answer) {
                                if(answer.errorCode != 0){
                                    showToast(MainActivity.this, "sendVoice error " + answer.getErrInfo());
                                }
                            }
                        });

                        offset[0] +=  readLen;
                    }
                },0, 20);

                try {
                    Thread.sleep(1000 *2);
                    AssetFileDescriptor assetFileDescriptor = getApplicationContext().getAssets().openFd(playName);
                    mediaPlayer = new MediaPlayer();
                    mediaPlayer.setDataSource(assetFileDescriptor);
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

            }
            else{
                msg = "startTranslate failed " + voiceStream.getErrInfo();
                mylog.log(msg);
                addlog(msg);
                showToast(MainActivity.this,msg);
            }
        }else if (type.equals("1")){
            RTVTStruct.VoiceStream voiceStream = client.startTranslate(srclang, destlang, true);
            if (voiceStream.errorCode == 0){
                addlog("startTranslate ok");
                streamId = voiceStream.streamId;
                RTCEngine.setVoiceStat(true);
            }
            else{
                msg = "startTranslate failed " + voiceStream.getErrInfo();
                mylog.log(msg);
                addlog(msg);
                showToast(MainActivity.this,msg);
            }
        }

    }


    private void showlog(){
        Dialog dialog = new Dialog(this, R.style.ActionSheetDialogStyle);
        //填充对话框的布局
        View inflate = LayoutInflater.from(this).inflate(R.layout.logview, null);
//        realLogView.append();

        String showmsg = "";
        TextView logview = inflate.findViewById(R.id.sholog);
        logview.setMovementMethod(ScrollingMovementMethod.getInstance());
        logview.setVerticalScrollBarEnabled(true);

        for( String msg: realLog){
            showmsg += msg;
        }
        logview.append(showmsg);
        int offset=logview.getLineCount()*logview.getLineHeight();

        if(offset>logview.getHeight()){
            logview.scrollTo(0,offset-logview.getLineHeight());
        }

        //将布局设置给Dialog
        dialog.setContentView(inflate);
        //获取当前Activity所在的窗体
        Window dialogWindow = dialog.getWindow();
        //设置Dialog从窗体底部弹出
        dialogWindow.setGravity(Gravity.TOP);
        //获得窗体的属性
        WindowManager.LayoutParams lp = dialogWindow.getAttributes();
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = DisplayUtils.getScreenHeight(this)*3 / 4;
        dialogWindow.setAttributes(lp);
        dialog.show();//显示对话框
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        srcadapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, srcarrayList);
        destadapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, destarrayList);


        audioTrackManager = AudioTrackManager.getInstance();
        login = findViewById(R.id.login);


        final LinkedList<CItem> testtypedata = new LinkedList<CItem>(){{
            add(new CItem("使用文件测试","0"));
            add(new CItem("使用录音测试","1"));
            }
        };

        mycontext = this;
        srcview = findViewById(R.id.srctext);
        destview = findViewById(R.id.desttext);
        srcview.setAdapter(srcadapter);
        destview.setAdapter(destadapter);

        srcspinner = findViewById(R.id.srcspinner);
        destspinner = findViewById(R.id.destspinner);
        testtype = findViewById(R.id.testtype);

        srcspinner.setSelectedIndex(0);
        destspinner.setSelectedIndex(0);

        testtype.attachDataSource(testtypedata);
        testtype.setSelectedIndex(1);

        start = findViewById(R.id.starttest);
        end = findViewById(R.id.endtest);
        quit = findViewById(R.id.quit);
        logbtn = findViewById(R.id.logbtn);

//        client = RTVTCenter.initRTVTClient(endpoint, pid, uid, new demoPush(), this.getApplicationContext());
        client = RTVTClient.CreateClient(endpoint, pid, uid, new demoPush(), this.getApplicationContext());

        logbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showlog();
            }
        });
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                stopPlay();
                client.closeRTVT();
                System.exit(0);
//                finish();
            }
        });


        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoadingDialog.getInstance(mycontext).show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long ts = System.currentTimeMillis()/1000;
                        String realToken = ApiSecurityExample.genHMACToken(pid, ts, mykey);

                        client.login(realToken, ts, new RTVTUserInterface.IRTVTEmptyCallback() {
                            @Override
                            public void onResult(RTVTStruct.RTVTAnswer answer) {
                                if (answer.errorCode == 0) {
                                    LoadingDialog.getInstance(getApplicationContext()).dismiss();//隐藏
                                    showToast(MainActivity.this, "login ok");
                                    addlog("login ok");
                                }
                                else {
                                    LoadingDialog.getInstance(getApplicationContext()).dismiss();//隐藏
                                    String msg = "login failed " + answer.getErrInfo();
                                    showToast(MainActivity.this, msg);
                                    addlog(msg);
                                }
                            }
                        });
                    }
                }).start();
            }
        });



        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
             startTest();
        }});



        end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                stopPlay();

                showToast(MainActivity.this, "结束测试");
                addlog("结束测试");
                running = false;
                RTCEngine.setVoiceStat(false);
                client.stopTranslate(streamId);
                streamId = 0;
//                try {
//                    outfile.flush();
//                    outfile.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
        });


        RTCEngine.create(this, this.getApplicationContext());


        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE};
        //验证是否许可权限
        for (String str : permissions) {
            if (this.checkSelfPermission(str) != PackageManager.PERMISSION_GRANTED) {
                //申请权限
                this.requestPermissions(permissions, REQUEST_CODE_CONTACT);
            }
        }
        getLangs();
    }


    private void getLangs(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                TCPClient tcpClient = TCPClient.create(endpoint);
                Quest quest = new Quest("getAvailableLanguage");
                try {
                    tcpClient.sendQuest(quest);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                tcpClient.sendQuest(quest, new FunctionalAnswerCallback() {
                    @Override
                    public void onAnswer(Answer answer, int errorCode) {
                        if (errorCode == 0){
                            List<String> obj = (List<String>) answer.want("srcLanguage");
                            for(String value:obj){
                                if (allLanguage.get(value) != null)
                                    langcode.add(allLanguage.get(value));
                            }
                            if (!langcode.isEmpty()){
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        srcspinner.attachDataSource(langcode);
                                        destspinner.attachDataSource(langcode);
                                    }
                                });
                            }
                            mylog.log(obj.toString());
                        }
                        else{
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                                    builder.setMessage("请求可用语言错误:" + errorCode).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    });
                                    builder.show();
                                }
                            });
                        }
                    }
                });
            }
        }).start();
    }
    private  void showToast(Activity activity, String data) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity == null || activity.isDestroyed() || activity.isFinishing())
                    return;
                Toast.makeText(activity, data, Toast.LENGTH_SHORT).show();
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.closeRTVT();
        System.exit(0);
    }

    private  byte[] toByteArray(InputStream input) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n = 0;
        while (true) {
            try {
                if (!(-1 != (n = input.read(buffer)))) break;
            } catch (IOException e) {
                e.printStackTrace();
            }
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }
}