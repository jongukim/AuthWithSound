package kr.ac.ajou.dv.authwithsound;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {
    public static final byte[] KEY_BYTES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    public static final byte[] IV_BYTES = {17, 18, 19, 20, 21, 22, 23, 24, 0, 0, 0, 0, 0, 0, 0, 0};
    private Cipher cipher;

    public boolean ready() {
        SecretKey key = new SecretKeySpec(KEY_BYTES, "AES");
        IvParameterSpec iv = new IvParameterSpec(IV_BYTES);
        try {
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public byte[] doit(byte[] plaintext) {
        try {
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
