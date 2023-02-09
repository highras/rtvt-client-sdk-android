package com.example.realtimeaudiotranslate;

import java.security.MessageDigest;
import java.util.Formatter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ApiSecurityExample {

    public static String genToken(long pid, String secret){
        long time = System.currentTimeMillis()/1000;
        String token = pid + ":" + time;

        String md5data = md5string(token);

        String realToken = ApiSecurityExample.hmacSha256(secret, md5data);
        return realToken;
    }

    public static String hmacSha1(String KEY, String VALUE) {
        return hmacSha(KEY, VALUE, "HmacSHA1");
    }

    public static String hmacSha256(String KEY, String VALUE) {
        return hmacSha(KEY, VALUE, "HmacSHA256");
    }

    public static String md5string(String data) {
        String retdata = "";
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(data.getBytes("UTF-8"));
            byte []md5Binary = md5.digest();
            retdata = bytesToHexString(md5Binary, true);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return retdata;
    }

    private static String bytesToHexString(byte[] bytes, boolean isLowerCase) {
        String from = isLowerCase ? "%02x" : "%02X";
        StringBuilder sb = new StringBuilder(bytes.length * 2);

        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format(from, b);
        }
        return sb.toString();
    }



    private static String hmacSha(String KEY, String VALUE, String SHA_TYPE) {
        String retVal = "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret = new SecretKeySpec(KEY.getBytes("UTF-8"), mac.getAlgorithm());
            mac.init(secret);

            byte[] digest = mac.doFinal(VALUE.getBytes());
            retVal = bytesToHexString(digest, true);


//            String base_string_base64 = Base64.encodeToString(VALUE.getBytes(), Base64.NO_WRAP);
//
//            byte[] digest = mac.doFinal(base_string_base64.getBytes());
//            retVal = Base64.encodeToString(digest, Base64.DEFAULT);

//            mylog.log("value is " + retVal);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return  retVal;
    }

  /*      try {
            SecretKeySpec signingKey = new SecretKeySpec(KEY.getBytes("UTF-8"), SHA_TYPE);
            Mac mac = Mac.getInstance(SHA_TYPE);
            mac.init(signingKey);
            byte[] rawHmac = mac.doFinal(VALUE.getBytes("UTF-8"));

            byte[] hexArray = {
                    (byte)'0', (byte)'1', (byte)'2', (byte)'3',
                    (byte)'4', (byte)'5', (byte)'6', (byte)'7',
                    (byte)'8', (byte)'9', (byte)'a', (byte)'b',
                    (byte)'c', (byte)'d', (byte)'e', (byte)'f'
            };
            byte[] hexChars = new byte[rawHmac.length * 2];
            for ( int j = 0; j < rawHmac.length; j++ ) {
                int v = rawHmac[j] & 0xFF;
                hexChars[j * 2] = hexArray[v >>> 4];
                hexChars[j * 2 + 1] = hexArray[v & 0x0F];
            }
            return new String(hexChars);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }*/

}