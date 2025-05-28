package com.example.aiproxy.common.conv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for converting between Strings and byte arrays.
 *
 * <p>Note: The original Go functions in `core/common/conv/any.go` used `unsafe`
 * package for performance optimizations by sharing underlying memory between
 * strings and byte slices. Such unsafe memory sharing is not standard practice
 * or directly available in safe Java. The methods in this class provide the
 * standard, safe Java way of performing these conversions, which typically
 * involve copying the data.
 * </p>
 */
public class StringByteConverter {

    /**
     * Converts a byte array to a String using the UTF-8 charset.
     * <p>This method creates a new String instance by copying the bytes.
     * It does not share memory with the input byte array, unlike the
     * original Go `unsafe.BytesToString` function.</p>
     *
     * @param bytes The byte array to convert.
     * @return A new String created from the byte array, or null if the input is null.
     */
    public static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Converts a byte array to a String using the specified charset.
     * <p>This method creates a new String instance by copying the bytes.
     * It does not share memory with the input byte array.</p>
     *
     * @param bytes   The byte array to convert.
     * @param charset The charset to use for decoding the bytes.
     * @return A new String created from the byte array, or null if the input is null.
     */
    public static String bytesToString(byte[] bytes, Charset charset) {
        if (bytes == null) {
            return null;
        }
        if (charset == null) {
            throw new IllegalArgumentException("Charset cannot be null");
        }
        return new String(bytes, charset);
    }

    /**
     * Converts a String to a byte array using the UTF-8 charset.
     * <p>This method creates a new byte array instance by copying the string's characters.
     * It does not share memory with the input string, unlike the
     * original Go `unsafe.StringToBytes` function.</p>
     *
     * @param str The String to convert.
     * @return A new byte array created from the String, or null if the input is null.
     */
    public static byte[] stringToBytes(String str) {
        if (str == null) {
            return null;
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Converts a String to a byte array using the specified charset.
     * <p>This method creates a new byte array instance by copying the string's characters.
     * It does not share memory with the input string.</p>
     *
     * @param str     The String to convert.
     * @param charset The charset to use for encoding the String.
     * @return A new byte array created from the String, or null if the input is null.
     */
    public static byte[] stringToBytes(String str, Charset charset) {
        if (str == null) {
            return null;
        }
        if (charset == null) {
            throw new IllegalArgumentException("Charset cannot be null");
        }
        return str.getBytes(charset);
    }

    // Private constructor to prevent instantiation of this utility class.
    private StringByteConverter() {
    }
}
