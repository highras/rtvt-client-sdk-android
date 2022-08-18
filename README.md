### android-rtm-sdk 使用文档
- [版本支持](#版本支持)
- [使用说明](#使用说明)
- [使用示例](#使用示例)
- [测试案例](#DEMO)

### 版本支持
- 最低支持Android版本为5.0(api21)

### 使用说明
- rtvt实时语音翻译需要的权限
  ~~~
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET"/>
    ~~~
- 默认支持自动重连(请继承RTMPushProcessor类的reloginWillStart和reloginCompleted方法)
- 服务器push消息:请继承RTMPushProcessor类,重写自己需要的push系列函数


### 使用示例
import com.rtmsdk.RTMClient;<br>
import com.rtmsdk.RTMErrorCode;

 ~~~
    public class RTMExampleQuestProcessor extends RTMPushProcessor {
        ....//重写自己需要处理的业务接口
    }
    
    client = RTMCenter.initRTMClient(endpoint, pid, uid, new demoPush(), this);


    RTMClient client = new RTMClient(String endpoint, long pid, long uid,new RTMExampleQuestProcessor());
    
    client.login(String Token, long tokents, IRTMEmptyCallback  callback)

    client.startTranslate("zh","en", true, IRTMCallback<VoiceStream> callback)
    
    client.sendVoice(int streamId, long seq, byte[] voicedata, UserInterface.IRTMEmptyCallback callback) 
~~~

##  接口说明
~~~
    /**
     *rtm登陆  sync
     * @param token     用户token
     */
    public RTMAnswer login(String token, long ts)

    /**
     *开始实时翻译语音流(需要先login成功)
     * @param srcLanguage 源语言
     * @param destLanguage 目标语言
     * @param asrResult (是否需要语音识别的结果。false: (default) 不需要; true: 需要
     *                  如果asrResult设置为false 那么只会推送翻译语言的文本 如果asrResult设置为true 那么会推送源语言和翻译语言两个结果
     *                  翻译结果通过
     * return VoiceStream
     */
    public RTMStruct.VoiceStream startStream(String srcLanguage, String destLanguage, boolean asrResult)


    /**
     * 发送语音片段
     * @param streamId 翻译流id
     * @param seq   语音片段序号(尽量有序)
     * @param voicedata 语音数据
     * @param voiceDataTs 音频帧对应时间戳
     * @param callback
     */
    public void sendVoice(int streamId, long seq, byte[] voicedata, UserInterface.IRTMEmptyCallback callback) 


    /**
     * 停止本次翻译流 如需下次继续翻译需要再次调用startTranslate
     * @param streamId 翻译的流id
     */
    public void stopTranslate(int streamId)


    /** 释放rtmclient(释放资源,网络广播监听会持有RTMClient对象 如果不调用RTMClient对象会一直持有不释放)
     * 如再次使用 需要重新调用RTMCenter.initRTMClient
     */
    public void closeRTM()
~~~

### DEMO
- 详见app目录