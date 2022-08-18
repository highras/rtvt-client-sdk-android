package com.fpnn.rttsdk;

import android.app.Activity;

import java.util.HashMap;

public class RTMCenter {
    static HashMap<String, RTMClient> clients = new HashMap<>();
    public  static RTMClient initRTMClient(String rtmEndpoint, long pid, String uid, RTMPushProcessor pushProcessor, Activity currentActivity){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            if (clients.containsKey(findkey)){
                return clients.get(findkey);
            }
        }
        RTMClient client = new RTMClient( rtmEndpoint,pid, uid, pushProcessor,currentActivity);
        return client;
    }


    static void closeRTM(long pid, String uid){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            clients.remove(findkey);
        }
    }
}
