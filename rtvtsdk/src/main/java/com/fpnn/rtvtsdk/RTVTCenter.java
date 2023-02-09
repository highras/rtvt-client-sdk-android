package com.fpnn.rtvtsdk;

import android.content.Context;

import java.util.HashMap;

public class RTVTCenter {
    static HashMap<String, RTVTClient> clients = new HashMap<>();
    public  static RTVTClient initRTVTClient(String rtvtEndpoint, long pid, String uid, RTVTPushProcessor pushProcessor, Context applicationContext){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            if (clients.containsKey(findkey)){
                return clients.get(findkey);
            }
        }
        RTVTClient client = new RTVTClient( rtvtEndpoint,pid, uid, pushProcessor,applicationContext);
        return client;
    }


    static void closeRTVT(long pid, String uid){
        synchronized (clients){
            String findkey = pid + ":" + uid;
            clients.remove(findkey);
        }
    }
}
