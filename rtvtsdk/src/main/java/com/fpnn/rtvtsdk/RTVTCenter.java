package com.fpnn.rtvtsdk;

import android.app.Activity;

import java.util.HashMap;

public class RTVTCenter {
    static HashMap<String, RTVTClient> clients = new HashMap<>();
    public  static RTVTClient initRTMClient(String rtmEndpoint, long pid, String uid, RTVTPushProcessor pushProcessor, Activity currentActivity){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            if (clients.containsKey(findkey)){
                return clients.get(findkey);
            }
        }
        RTVTClient client = new RTVTClient( rtmEndpoint,pid, uid, pushProcessor,currentActivity);
        return client;
    }


    static void closeRTM(long pid, String uid){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            clients.remove(findkey);
        }
    }
}
