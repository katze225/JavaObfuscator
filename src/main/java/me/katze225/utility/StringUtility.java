package me.katze225.utility;

import java.util.Random;

public class StringUtility {
    private static final Random RANDOM = new Random();

    public static String randomString(int length) {
        StringBuilder sb = new StringBuilder();
        String alphabet = "абвгдежзийклмнопрстуфхцчшщъыьэюяАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ" +
                         "абвгдеєжзиіїйклмнопрстуфхцчшщьюяАБВГДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ";
        for (int i = 0; i < length; i++) {
            char c = alphabet.charAt(RANDOM.nextInt(alphabet.length()));
            sb.append(c);
        }
        return sb.toString();
    }

    public static String randomPrefix() {
        String alphabet = "абвгдежзийклмнопрстуфхцчшщъыьэюяАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ" +
                         "абвгдеєжзиіїйклмнопрстуфхцчшщьюяАБВГДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯ";
        int len = 6 + RANDOM.nextInt(8);
        StringBuilder sb = new StringBuilder(len + 1);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        sb.append('_');
        return sb.toString();
    }
}
