package com.fpnn.rtvtsdk;

public class RTVTStruct {

    //errorCode==0为成功 非0错误 错误信息详见errorMsg字段
    public static class RTVTAnswer
    {
        public int errorCode = -1;
        public String errorMsg = "";
        public RTVTAnswer(){}
        public RTVTAnswer(int _code, String msg){
            errorCode = _code;
            errorMsg = msg;
        }
        public String getErrInfo(){
            return  " " + errorCode + "-" + errorMsg;
        }
    }

    public static class VoiceStream extends RTVTAnswer{
        public long streamId = 0;
    }
}
