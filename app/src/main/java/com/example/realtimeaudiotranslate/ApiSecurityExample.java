package com.example.realtimeaudiotranslate;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Formatter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ApiSecurityExample {
    public static String genECDSATokenPKCS8(long pid, long uid, long ts, String secret) {
        String content = pid+":"+uid+":"+ts;
        String token = "";
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("ec");

            BufferedReader reader = new BufferedReader(new StringReader(secret));

            String line = reader.readLine();
            if (line.compareTo("-----BEGIN PRIVATE KEY-----") != 0)
                return token;

            String keyBuffer = "";
            line = reader.readLine();
            while (line.compareTo("-----END PRIVATE KEY-----") != 0)
            {
                keyBuffer += line;
                line = reader.readLine();
            }

            byte[] pkcs8 = Base64.decode(keyBuffer.getBytes(),Base64.NO_WRAP);

            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(pkcs8);

            PrivateKey pk = keyFactory.generatePrivate(pkcs8EncodedKeySpec);

            Signature sigalg = Signature.getInstance("SHA256withECDSA");
            sigalg.initSign(pk);
            sigalg.update(content.getBytes());
            byte[] sigbytes = sigalg.sign();

            token = new String(Base64.encode(sigbytes,Base64.NO_WRAP));

        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IOException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return token;
    }

            public static String genEd25519Token(long pid, long uid, String secret,long ts) {
                String content = pid+":"+uid+":"+ts;
                String token = "";
                try {
                    KeyFactory keyFactory = KeyFactory.getInstance("ed25519");

                    BufferedReader reader = new BufferedReader(new StringReader(secret));

                    String line = reader.readLine();
                    if (line.compareTo("-----BEGIN PRIVATE KEY-----") != 0)
                        return token;

                    String keyBuffer = "";
                    line = reader.readLine();
                    while (line.compareTo("-----END PRIVATE KEY-----") != 0)
                    {
                        keyBuffer += line;
                        line = reader.readLine();
                    }

                    byte[] tmpdata = keyBuffer.getBytes();
                    byte[] pkcs8 = Base64.decode(tmpdata, tmpdata.length);

                    PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(pkcs8);

                    PrivateKey pk = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
                    Signature sigalg = Signature.getInstance("ed25519");
                    sigalg.initSign(pk);
                    sigalg.update(content.getBytes());
                    byte[] sigbytes = sigalg.sign();

                    token = new String(Base64.encode(sigbytes,Base64.DEFAULT));

                } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | IOException | InvalidKeySpecException e) {
                    e.printStackTrace();
                }
                return token;
            }

    public static String genHMACToken(long pid, long ts, String secret){
        String token = pid  + ":" + ts;
//        String token = "11000001:666:1669174320";
        String realKey = "";
        try {
             realKey =new String( Base64.decode(secret, Base64.NO_WRAP), "UTF_8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

//        String md5data = md5string(token);

        String realToken = ApiSecurityExample.hmacSha256(realKey, token);
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
            retVal= Base64.encodeToString(digest, Base64.NO_WRAP);
//            mylog.log("base84tostring is " + retVal);
//            retVal = bytesToHexString(digest,true);
//            mylog.log("bytesToHexString is " + retVal);


//            retVal = new String(Base64.encode(digest,Base64.NO_WRAP));


//            byte[] data  = Base64.encode(digest,Base64.NO_WRAP);
//            retVal = bytesToHexString(data, true);


//            retVal= Base64.encodeToString(digest, Base64.NO_WRAP);
//
//            byte[] digest = mac.doFinal(base_string_base64.getBytes());
//            retVal = Base64.encodeToString(digest, Base64.DEFAULT);

//            mylog.log("value is " + retVal);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return  retVal;
    }

  /*      try {ÅÅ
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