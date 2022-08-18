package com.fpnn.rttsdk;

import org.json.JSONObject;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RTMStruct {

    //errorCode==0为成功 非0错误 错误信息详见errorMsg字段
    public static class RTMAnswer
    {
        public int errorCode = -1;
        public String errorMsg = "";
        public RTMAnswer(){}
        public RTMAnswer(int _code, String msg){
            errorCode = _code;
            errorMsg = msg;
        }
        public String getErrInfo(){
            return  " " + errorCode + "-" + errorMsg;
        }
    }

    public static class VoiceStream extends RTMAnswer{
        public int streamId = 0;
    }
}
