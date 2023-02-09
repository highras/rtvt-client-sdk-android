package com.example.realtimeaudiotranslate;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.fpnn.rtvtsdk.RTVTCenter;
import com.fpnn.rtvtsdk.RTVTClient;
import com.fpnn.rtvtsdk.RTVTPushProcessor;
import com.fpnn.rtvtsdk.RTVTStruct;
import com.fpnn.rtvtsdk.RTVTUserInterface;
import com.livedata.rtc.RTCEngine;

import org.angmarch.views.NiceSpinner;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

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
        public String toString() {           //为什么要重写toString()呢？因为适配器在显示数据的时候，如果传入适配器的对象不是字符串的情况下，直接就使用对象.toString()
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startTest();
                    }
                });
            }
        }


        @Override
        public void recognizedResult(int streamId, int startTs, int endTs, int recTs, String srcVoiceText) {
            mylog.log("stream id:" + streamId + "srctext:" + srcVoiceText);
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
            mylog.log("stream id:" + streamId + "desttext:" + destVoiceText);
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
            showToast(MainActivity.this, "rtvt closed");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopAudio();
                }
            });
        }
    }


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

    private void setPCMData(String name){
        InputStream inputStream = null;

        try {
            inputStream = getAssets().open(name);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        pcmData = toByteArray(inputStream);
        byte[] wavdata = toByteArray(inputStream);
        pcmData = new byte[wavdata.length - 44];
        System.arraycopy(wavdata, 44,pcmData,0,wavdata.length - 44);
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

    private void startTest(){
        stopAudio();

        String srclang = ((CItem)(srcspinner.getSelectedItem())).getValue();
        String destlang = ((CItem)(destspinner.getSelectedItem())).getValue();
        String type = ((CItem)(testtype.getSelectedItem())).getValue();
        showToast(MainActivity.this, "开始测试");
        if (type.equals("0")){
            String playName = srclang + ".wav";
            setPCMData(playName);

            final int[] offset = {0};
            RTVTStruct.VoiceStream voiceStream = client.startTranslate(srclang, destlang, true);
            if (voiceStream.errorCode == 0){
                streamId = voiceStream.streamId;
                byte[] voicedatatmp = new byte[readLen];
                final int[] i = {0};

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
                mylog.log("startTranslate failed " + voiceStream.getErrInfo());
                showToast(MainActivity.this,"startTranslate failed " + voiceStream.getErrInfo());
            }
        }else if (type.equals("1")){
            RTVTStruct.VoiceStream voiceStream = client.startTranslate(srclang, destlang, true);
            if (voiceStream.errorCode == 0){
                streamId = voiceStream.streamId;
                RTCEngine.setVoiceStat(true);
            }
            else{
                showToast(MainActivity.this,"startTranslate failed " + voiceStream.getErrInfo());
            }
        }

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        srcadapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, srcarrayList);
        destadapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, destarrayList);


        audioTrackManager = AudioTrackManager.getInstance();
        login = findViewById(R.id.login);

        final LinkedList<CItem> langcode = new LinkedList<CItem>(){{
            add(new CItem("简体中文","zh"));
            add(new CItem("英语","en"));
//            add(new CItem("日语","ja"));
//            add(new CItem("泰语","th"));
            add(new CItem("印尼语","id"));
//            add(new CItem("西语","es"));
//            add(new CItem("越南语","vi"));
//            add(new CItem("印地语","hi"));
            add(new CItem("阿语","ar"));
//            add(new CItem("马来语","ms"));
            }
        };

        final LinkedList<CItem> testtypedata = new LinkedList<CItem>(){{
            add(new CItem("使用文件测试","0"));
            add(new CItem("使用录音测试","1"));
            }
        };

        srcview = findViewById(R.id.srctext);
        destview = findViewById(R.id.desttext);
        srcview.setAdapter(srcadapter);
        destview.setAdapter(destadapter);

        srcspinner = findViewById(R.id.srcspinner);
        destspinner = findViewById(R.id.destspinner);
        testtype = findViewById(R.id.testtype);

        srcspinner.attachDataSource(langcode);
        destspinner.attachDataSource(langcode);

        srcspinner.setSelectedIndex(0);
        destspinner.setSelectedIndex(0);

        testtype.attachDataSource(testtypedata);
        testtype.setSelectedIndex(0);

        start = findViewById(R.id.starttest);
        end = findViewById(R.id.endtest);
        quit = findViewById(R.id.quit);

//        client = RTVTCenter.initRTVTClient(endpoint, pid, uid, new demoPush(), this.getApplicationContext());
        client = RTVTClient.CreateClient(endpoint, pid, uid, new demoPush(), this.getApplicationContext());

        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopTimer();
                stopPlay();
                client.closeRTVT();
                finish();
            }
        });


        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        long ts = System.currentTimeMillis()/1000;
                        String realToken = ApiSecurityExample.genHMACToken(pid, ts, mykey);

                        client.login(realToken, ts, new RTVTUserInterface.IRTVTEmptyCallback() {
                            @Override
                            public void onResult(RTVTStruct.RTVTAnswer answer) {
                                mylog.log(" login " + answer.getErrInfo());
                                if (answer.errorCode == 0)
                                    showToast(MainActivity.this, "login ok" );
                                else
                                    showToast(MainActivity.this, "login failed " + answer.getErrInfo() );
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
                running = false;
                RTCEngine.setVoiceStat(false);
                client.stopTranslate(streamId);
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