package com.fpnn.rttsdk;

import android.app.Activity;
import android.content.Context;

import com.fpnn.sdk.ErrorCode;
import com.fpnn.sdk.FunctionalAnswerCallback;
import com.fpnn.sdk.proto.Answer;
import com.fpnn.sdk.proto.Quest;
import com.fpnn.rttsdk.RTMStruct.*;
public class RTMClient extends RTMCore {

    /**
     * 初始化
     * @param rtmEndpoint
     * @param pid
     * @param uid
     * @param currentActivity 当前Activity
     */
    protected RTMClient(String rtmEndpoint, long pid, String uid, RTMPushProcessor serverPushProcessor, Activity currentActivity) {
        RTMInit(rtmEndpoint,pid, uid, serverPushProcessor,currentActivity);
    }


    /**
     *rtm登陆  sync
     * @param token     用户token
     */
    public RTMAnswer login(String token, long ts) {
        return super.login(token, ts);
    }

    /**
     *rtm登陆  async
     * @param callback  登陆结果回调
     * @param token     用户token
     * @param ts      生成token的时间戳
     */
    public void login( String token, long ts, UserInterface.IRTMEmptyCallback callback) {
        super.login(callback, ts, token);
    }


    /**
     *开始实时翻译语音流(需要先login成功)
     * @param srcLanguage 源语言
     * @param destLanguage 目标语言
     * @param asrResult (是否需要语音识别的结果。false: (default) 不需要; true: 需要
     *                  如果asrResult设置为false 那么只会推送翻译语言的文本 如果asrResult设置为true 那么会推送源语言和翻译语言两个结果
     *                  翻译结果通过
     * return VoiceStream
     */
    public RTMStruct.VoiceStream startStream(String srcLanguage, String destLanguage, boolean asrResult){
        VoiceStream ret = new VoiceStream();
        Quest quest = new Quest("voiceStart");
        quest.param("asrResult", true);
        quest.param("srcLanguage", srcLanguage);
        quest.param("destLanguage", destLanguage);
        Answer answer = sendQuest(quest);
        ret.errorCode = answer.getErrorCode();
        ret.errorMsg = answer.getErrorMessage();
        if (answer.getErrorCode() == okRet){
            ret.streamId = rtmUtils.wantInt("streamId");
        }
        return  ret;
    }


    /**
     *开始实时翻译语音流(需要先login成功)
     * @param srcLanguage 源语言
     * @param destLanguage 目标语言
     * @param asrResult (是否需要语音识别的结果。false: (default) 不需要; true: 需要
     *                  如果asrResult设置为false 那么只会推送翻译语言的文本 如果asrResult设置为true 那么会推送源语言和翻译语言两个结果
     *                  翻译结果通过
     * return VoiceStream
     */
    public void startTranslate(String srcLanguage, String destLanguage, boolean asrResult, final UserInterface.IRTMCallback<VoiceStream> callback){
        VoiceStream ret = new VoiceStream();
        Quest quest = new Quest("voiceStart");
        quest.param("asrResult", asrResult);
        quest.param("srcLanguage", srcLanguage);
        quest.param("destLanguage", destLanguage);

        sendQuest(quest, new FunctionalAnswerCallback() {
            @Override
            public void onAnswer(Answer answer, int errorCode) {
                VoiceStream ret = new VoiceStream();
                ret.errorCode = errorCode;
                if (errorCode == okRet){
                    ret.streamId = rtmUtils.wantInt(answer,"streamId");
                }
                callback.onResult(ret, genRTMAnswer(answer, errorCode));
            }
        });
    }


    /**
     * 发送语音片段
     * @param streamId 翻译流id
     * @param seq   语音片段序号(尽量有序)
     * @param voicedata 语音数据
     * @param callback
     */
    public void sendVoice(int streamId, long seq, byte[] voicedata, UserInterface.IRTMEmptyCallback callback) {
        if (voicedata.length != 640){
            callback.onResult(genRTMAnswer(ErrorCode.FPNN_EC_CORE_UNKNOWN_METHOD.value(), "please send 640 bytes length data"));
            return;
        }

        Quest quest = new Quest("voiceData");
        quest.param("streamId", streamId);
        quest.param("seq", seq);
        quest.param("data", voicedata);
        quest.param("ts", Genid.getCurrentMilliseconds());
        sendQuestEmptyCallback(callback, quest);
    }


    /**
     * 停止本次翻译流 如需下次继续翻译需要再次调用startTranslate
     * @param streamId 翻译的流id
     */
    public void stopTranslate(int streamId){
        Quest quest = new Quest("voiceEnd");
        quest.param("streamId", streamId);
        sendQuestEmptyCallback(new UserInterface.IRTMEmptyCallback() {
            @Override
            public void onResult(RTMAnswer answer) {

            }
        },quest);
    }





    /** 释放rtmclient(释放资源,网络广播监听会持有RTMClient对象 如果不调用RTMClient对象会一直持有不释放)
     * 如再次使用 需要重新调用RTMCenter.initRTMClient
     */
    public void closeRTM(){
        realClose();
        RTMCenter.closeRTM(getPid(), getUid());
    }
}
