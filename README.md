### android-rtvt-sdk 使用文档
- [版本支持](#版本支持)
- [使用说明](#使用说明)
- [使用示例](#使用示例)
- [测试案例](#DEMO)

### 版本支持
- 最低支持Android版本为5.0(api21)

### 使用说明
  dependencies {
    implementation 'com.github.highras:rtvt-android:2.0.0'
  }


- rtvt实时语音翻译需要的权限
  ~~~
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.INTERNET"/>
    ~~~

- 默认支持自动重连 (请继承RTVTPushProcessor类的reloginWillStart和reloginCompleted方法，重连完成后需要重新调用 startTranslate方法)
- 服务器push消息:请继承RTVTPushProcessor类,重写自己需要的push系列函数
- 传入的pcm音频需要16000采样率 单声道  固定640字节
- 收到链接断开事件后 之前开始的streamid就会失效 需要链接成功后重新调用startTranslate获取新的streamid
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

    client.login(String token, IRTVTEmptyCallback  callback)

    client.startTranslate(srclang, destlang, null, true, true, true, false,"xxxx",new RTVTUserInterface.IRTVTCallback<RTVTStruct.VoiceStream>() {})
    
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
     * @param tempResult 是否需要临时结果 如果设置为true 临时识别结果和翻译结果通过recognizedTempResult和translatedTempResult回调(用于长句快速的返回)
     * @param transResult 是否需要翻译结果 如果设置为true 翻译结果通过translatedResult回调
     * @param ttsResult 是否需要TTS结果，TTS仅合成最终翻译结果
     * @param userId  后台显示便于查询 （业务端可以和返回的streamid绑定）
     */
 public void startTranslate(String srcLanguage, String destLanguage, List<String> srcAltLanguage, boolean asrResult, boolean tempResult, boolean transResult, boolean ttsResult,String userId, final RTVTUserInterface.IRTVTCallback<VoiceStream> callback)

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

    /**
     * 发送语音片段(多语种翻译)
     * @param streamId (multi_starTranslate返回的streamid）
     * @param seq  语音片段序号(尽量有序)
     * @param voicedata 语音数据（传入的pcm音频需要16000采样率 单声道 固定640字节）
     * @param voiceDataTs 音频帧对应时间戳
     * @param dstLanguageList 需要翻译的语言列表
     * @param callback
     */
     public void multi_sendVoice(long streamId, long seq, byte[] voicedata, long voiceDataTs, List<String> dstLanguageList, RTVTUserInterface.IRTVTEmptyCallback callback)


    /**
     *开始实时翻译语音流(多语种翻译)
     * @param srcLanguage 源语言(必传)
     * @param srcAltLanguage 备选语言列表(可空 如果传了备选语言 会有3秒自动语种识别 第一句返回的识别和翻译时长会变大）
     * @param asrResult 是否需要语音识别的结果。如果设置为true 识别结果通过recognizedResult回调
     * @param tempResult 是否需要临时识别结果和临时翻译结果 如果设置为true 临时识别结果通过recognizedTempResult回调 翻译临时结果通过translatedTempResult回调(用于长句快速返回)
     * @param ttsResult 是否需要TTS结果，TTS仅合成最终翻译结果
     * @param userId  后台显示便于查询 （业务端可以和返回的streamid绑定）
     */
    public void multi_startTranslate(String srcLanguage, List<String> srcAltLanguage, boolean asrResult,boolean tempResult, boolean ttsResult, String userId, final RTVTUserInterface.IRTVTCallback<VoiceStream> callback)
     

    /** 释放rtvtclient(释放资源,网络广播监听会持有RTVTClient对象 如果不调用RTVTClient对象会一直持有不释放)
     * 如再次使用 需要重新调用RTVTCenter.initRTVTClient
     */
    public void closeRTVT()
    
    
    RTVTPushProcessor 回调类方法
     /**
     * RTVT链接断开 (默认会自动连接)
     */
    public void rtvtConnectClose(){}

    /**
     * RTVT重连开始接口 每次重连都会判断reloginWillStart返回值 若返回false则中断重连
     * 参数说明 uid-用户id  answer-上次重连的结果  reloginCount-将要重连的次数
     * 备注:需要用户设定一些条件 比如重连间隔 最大重连次数
     */
    public boolean reloginWillStart(int reloginCount){return true;}

    /**
     * RTVT重连完成(如果 successful 为false表示最终重连失败,answer会有详细的错误码和错和错误信息 为true表示重连成功)
     */
    public void reloginCompleted(boolean successful, RTVTStruct.RTVTAnswer answer, int reloginCount){}


    /**
     *源语言识别结果
     * @param streamId 翻译流id
     * @param startTs  音频开始的毫秒时间戳
     * @param endTs    音频结束的毫秒时间戳
     * @param recTs    识别时间戳，毫秒
     * @param language 识别的语言
     * @param srcVoiceText 识别文本
     * @param taskId     单句对应的序号
     */
    public void recognizedResult(long streamId, long startTs, long endTs, long recTs, String language, String srcVoiceText, long taskId){}


    /**
     *源语言临时识别结果
     * @param streamId 翻译流id
     * @param startTs  音频开始的毫秒时间戳
     * @param endTs    音频结束的毫秒时间戳
     * @param recTs    识别时间戳，毫秒
     * @param language 识别的语言
     * @param srcVoiceText 识别文本
     * @param taskId     单句对应的序号
     */
    public void recognizedTempResult(long streamId, long startTs, long endTs, long recTs, String language, String srcVoiceText,long taskId){}


    /**
     *目标言翻译结果
     * @param streamId 翻译流id
     * @param startTs  结果开始的毫秒时间戳
     * @param endTs    结果结束的毫秒时间戳
     * @param recTs    识别时间戳，毫秒
     * @param language 识别的语言
     * @param destVoiceText 翻译文本
     * @param taskId     单句对应的序号
     */
    public void translatedResult(long streamId, long startTs, long endTs, long recTs, String language, String destVoiceText, long taskId){}

    /**
     *临时翻译结果
     * @param streamId 翻译流id
     * @param startTs  音频开始的毫秒时间戳
     * @param endTs    音频结束的毫秒时间戳
     * @param recTs    识别时间戳，毫秒
     * @param language 识别的语言
     * @param destVoiceText 临时翻译文本
     * @param taskId     单句对应的序号
     */
    public void translatedTempResult(long streamId, long startTs, long endTs, long recTs, String language, String destVoiceText, long taskId){}
    
~~~

### DEMO
- 详见app目录