package com.example.smartfarmer.utils;

import android.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class EncryptionUtils {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    // In a production app, this key should be derived securely (e.g., using PBKDF2) 
    // and stored in the Android Keystore.
    private static final String SECRET_SEED = "SmartFarmer_Secure_Seed_2024";

    public static String encrypt(String plainText) {
        return encrypt(plainText, SECRET_SEED);
    }

    public static String encrypt(String plainText, String seed) {
        try {
            byte[] key = generateKey(seed);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            IvParameterSpec iv = new IvParameterSpec(new byte[16]); 
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), iv);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception e) {
            return plainText;
        }
    }

    public static String decrypt(String encryptedText) {
        return decrypt(encryptedText, SECRET_SEED);
    }

    public static String decrypt(String encryptedText, String seed) {
        try {
            byte[] key = generateKey(seed);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            IvParameterSpec iv = new IvParameterSpec(new byte[16]);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), iv);
            byte[] decrypted = cipher.doFinal(Base64.decode(encryptedText, Base64.DEFAULT));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If it's old content, try decrypting with global seed as fallback
            if (!seed.equals(SECRET_SEED)) {
                return decrypt(encryptedText, SECRET_SEED);
            }
            return encryptedText;
        }
    }

    private static byte[] generateKey(String seed) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(seed.getBytes(StandardCharsets.UTF_8));
    }

    public static String getConversationSeed(String id1, String id2) {
        if (id1 == null || id2 == null) return SECRET_SEED;
        // Sort IDs to ensure same seed regardless of who is sender/receiver
        if (id1.compareTo(id2) < 0) {
            return id1 + "_" + id2 + "_" + SECRET_SEED;
        } else {
            return id2 + "_" + id1 + "_" + SECRET_SEED;
        }
    }
}
