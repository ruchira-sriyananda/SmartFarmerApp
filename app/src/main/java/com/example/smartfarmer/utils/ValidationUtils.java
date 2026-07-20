package com.example.smartfarmer.utils;

import java.util.regex.Pattern;

public class ValidationUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
                    "\\@" +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
                    "(" +
                    "\\." +
                    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
                    ")+"
    );

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        // Regex for Sri Lankan phone numbers:
        // Supports: 0771234567, +94771234567, 94771234567
        String sriLankaRegex = "^(?:\\+94|94|0)?7[0-9]{8}$";
        return phone.matches(sriLankaRegex);
    }

    public static boolean isStrongPassword(String password) {
        return password != null && password.length() >= 6;
    }
}