package com.example.realtimeaudiotranslate;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class AudioTrackManager {

    private static final String TAG = "AudioTrackManager";
    private AudioTrack mAudioTrack;
    private volatile static AudioTrackManager mInstance;
    private long bufferCount;

    /**
     * 音频流类型
     */
    private static final int mStreamType = AudioManager.STREAM_MUSIC;
    /**
     * 指定采样率 （MediaRecoder 的采样率通常是8000Hz AAC的通常是44100Hz。
     * 设置采样率为44100，目前为常用的采样率，官方文档表示这个值可以兼容所有的设置）
     */
    private static final int mSampleRateInHz = 16000;
    /**
     * 指定捕获音频的声道数目。在AudioFormat类中指定用于此的常量
     */
    private static final int mChannelConfig = AudioFormat.CHANNEL_OUT_MONO; //单声道


    private static final int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;

    /**
     * 指定缓冲区大小。调用AudioTrack类的getMinBufferSize方法可以获得。
     */
    public int mMinBufferSize;

    /**
     * STREAM的意思是由用户在应用程序通过write方式把数据一次一次得写到audiotrack中。
     * 这个和我们在socket中发送数据一样，
     * 应用层从某个地方获取数据，例如通过编解码得到PCM数据，然后write到audiotrack。
     */
    private static int mMode = AudioTrack.MODE_STREAM;

    private static final int BUFFER_CAPITAL = 10;


    /**
     * 获取单例引用
     *
     * @return
     */
    public static AudioTrackManager getInstance() {
        if (mInstance == null) {
            synchronized (AudioTrackManager.class) {
                if (mInstance == null) {
                    mInstance = new AudioTrackManager();
                }
            }
        }
        return mInstance;
    }


    public AudioTrackManager() {
        initAudioTrack();
    }


    private void initAudioTrack() {
    }



    public int getminbuffer(){
        return mMinBufferSize;
    }

    public void prepareAudioTrack() {

        bufferCount = 0;
        if (null == mAudioTrack) {
            mMinBufferSize = AudioTrack.getMinBufferSize(mSampleRateInHz, mChannelConfig, mAudioFormat);
            mylog.log(" mMinBufferSize is " + mMinBufferSize);
            //注意，按照数字音频的知识，这个算出来的是一秒钟buffer的大小。
            mAudioTrack = new AudioTrack(mStreamType, mSampleRateInHz, mChannelConfig,
                    mAudioFormat, mMinBufferSize, mMode);
        }

        if (mAudioTrack.getState() == mAudioTrack.STATE_UNINITIALIZED) {
            initAudioTrack();
        }
        mAudioTrack.play();
    }


    public synchronized void write(final byte[] bytes, int len, int offset) {
        if (null != mAudioTrack) {
            synchronized (mAudioTrack) {
                int byteSize = len;
                bufferCount += byteSize;
                int write = mAudioTrack.write(bytes, offset, len);
            }
        }
    }

    public synchronized void write(final byte[] bytes, int len) {
        if (null != mAudioTrack) {
            synchronized (mAudioTrack) {
                int byteSize = len;
                bufferCount += byteSize;
                int write = mAudioTrack.write(bytes, 0, len);
            }
        }
    }

    public void release() {
        synchronized (mAudioTrack) {
            if (null == mAudioTrack) {
                return;
            }
            try {
                mAudioTrack.release();
                mAudioTrack = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}