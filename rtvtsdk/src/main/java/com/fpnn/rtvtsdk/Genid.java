package com.fpnn.rtvtsdk;

import java.util.concurrent.atomic.AtomicLong;

public class Genid {
    static private AtomicLong orderId = new AtomicLong();

    static public long genMid() {
        long id = getCurrentMilliseconds()<<16;
        return id + orderId.incrementAndGet();
    }

    static long getCurrentSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    static long getCurrentMilliseconds() {
        return System.currentTimeMillis();
    }

}
