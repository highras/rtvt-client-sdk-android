### android-rtvt-sdk 使用文档
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

- 默认支持自动重连 (请继承RTVTPushProcessor类的reloginWillStart和reloginCompleted方法，重连完成后需要重新调用 startTranslate方法)
- 服务器push消息:请继承RTVTPushProcessor类,重写自己需要的push系列函数
- 传入的pcm音频需要16000采样率 单声道  固定640字节
- 错误码
  800001-未验证的链接
  800002-登陆失败
  800003-token已过期
  800004-无效的验证时间
  800005-无效token
  800006-音频流不存在

### 使用示例
 ~~~
    public class RTVTExampleQuestProcessor extends RTVTPushProcessor {
        ....//重写自己需要处理的业务接口
    }
    
    client = RTVTClient.CreateClient(endpoint, pid, new RTVTExampleQuestProcessor(), this.getApplicationContext());

    client.login(String secretKey, IRTVTEmptyCallback  callback)

    client.startTranslate("zh", "en", null, true, false, true, "123456",new RTVTUserInterface.IRTVTCallback<RTVTStruct.VoiceStream>() 
    
    client.sendVoice(long streamId, long seq, byte[] voicedata, UserInterface.IRTVTEmptyCallback callback) 
    
    client.stopTranslate(streamId)
~~~

##  接口说明
~~~
    /**
     *rtvt登陆
     * @param token   计算的token
     */
    public RTVTAnswer login(String token, long ts)

    /**
     *rtvt登陆  async
     * @param token   计算的token
     * @param callback  登陆结果回调
     */
    public void login(final String token, final long ts, final RTVTUserInterface.IRTVTEmptyCallback callback)


    /**
     *开始实时翻译语音流(需要先login成功)
     * @param srcLanguage 源语言(必传)
     * @param destLanguage 翻译的目标语言 (如果不需要翻译 可空)
     * @param srcAltLanguage 备选语言列表(可空 如果传了备选语言 会有3秒自动语种识别 第一句返回的识别和翻译时长会变大）
     * @param asrResult 是否需要语音识别的结果。如果设置为true 识别结果通过recognizedResult回调
     * @param asrTempResult 是否需要临时识别结果 如果设置为true 临时识别结果通过recognizedTempResult回调(临时识别结果 用于长句快速的返回)
     * @param transResult 是否需要翻译结果 如果设置为true 翻译结果通过translatedResult回调
     * @param userId  后台显示便于查询 （业务端可以和返回的streamid绑定）
     */
    public void startTranslate(String srcLanguage, String destLanguage, List<String> srcAltLanguage, boolean asrResult, boolean asrTempResult, boolean transResult,String userId, final RTVTUserInterface.IRTVTCallback<VoiceStream> callback)


    /**
     * 发送语音片段
     * @param streamId (startTranslate返回的流id）
     * @param seq   语音片段序号(尽量有序)
     * @param voicedata 语音数据（传入的pcm音频需要16000采样率 单声道 固定640字节）
     * @param voiceDataTs 音频帧对应时间戳
     * @param callback
     */
    public void sendVoice(long streamId, long seq, byte[] voicedata, long voiceDataTs, RTVTUserInterface.IRTVTEmptyCallback callback)


    /**
     * 停止本次翻译流 如需下次继续翻译需要再次调用startTranslate
     * @param streamId 翻译的流id
     */
    public void stopTranslate(long streamId)


    /** 释放rtvtclient(释放资源,网络广播监听会持有RTVTClient对象 如果不调用RTVTClient对象会一直持有不释放)
     * 如再次使用 需要重新调用RTVTCenter.initRTVTClient
     */
    public void closeRTVT()
~~~

### DEMO
- 详见app目录