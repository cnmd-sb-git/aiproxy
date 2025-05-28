package com.example.aiproxy.common;

import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.Locale;

public class TruncateHelper {

    /**
     * Truncates a string to a specified maximum byte length, ensuring truncation
     * occurs at a valid UTF-8 character boundary.
     *
     * @param s           The string to truncate.
     * @param maxByteLength The maximum desired byte length of the truncated string (in UTF-8).
     * @return The truncated string.
     */
    public static String truncateStringByUtf8Bytes(String s, int maxByteLength) {
        if (s == null || s.isEmpty() || maxByteLength <= 0) {
            return "";
        }

        byte[] utf8Bytes = s.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length <= maxByteLength) {
            return s;
        }

        // Iterate through the original string's characters (code points)
        // to find the correct truncation point in the UTF-8 byte array.
        int currentByteLength = 0;
        int characterCount = 0; // Number of characters in the original string to keep

        // Using BreakIterator to iterate over grapheme clusters (user-perceived characters)
        // is more robust than char or code point iteration for truncation.
        // However, the Go code iterates by rune (code point) and calculates UTF-8 byte length.
        // To closely match, we iterate code points.
        for (int i = 0; i < s.length(); ) {
            int codePoint = s.codePointAt(i);
            String charAsString = new String(Character.toChars(codePoint));
            int charUtf8ByteLength = charAsString.getBytes(StandardCharsets.UTF_8).length;

            if (currentByteLength + charUtf8ByteLength > maxByteLength) {
                break; // Next character would exceed the byte limit
            }
            currentByteLength += charUtf8ByteLength;
            characterCount += Character.charCount(codePoint); // Advance by 1 or 2 for supplementary characters
            i += Character.charCount(codePoint);
        }
        
        // Now, characterCount is the number of characters from the original string
        // whose UTF-8 representation fits within maxByteLength.
        // We need to construct the result from the UTF-8 bytes.
        // A simpler way might be to directly work with the byte array.

        // Re-evaluate: The Go code s[:total] slices the string based on byte count.
        // If we have the UTF-8 bytes, and we determine total byte count for valid runes:
        int validByteEndIndex = 0;
        int tempByteCount = 0;
        for (int i = 0; i < s.length(); ) {
            int codePoint = s.codePointAt(i);
            // Get UTF-8 length of this code point
            // A single char can be > 1 byte in UTF-8. Max 4 bytes for a Unicode code point.
            // (A Java char is 2 bytes, a code point can be 1 or 2 Java chars)
            int runeLen;
            if (codePoint < 0x80) {
                runeLen = 1;
            } else if (codePoint < 0x800) {
                runeLen = 2;
            } else if (codePoint < 0x10000) { // Basic Multilingual Plane
                runeLen = 3;
            } else { // Supplementary characters
                runeLen = 4;
            }

            if (tempByteCount + runeLen > maxByteLength) {
                break;
            }
            tempByteCount += runeLen;
            validByteEndIndex = tempByteCount;
            i += Character.charCount(codePoint);
        }

        if (validByteEndIndex == 0 && maxByteLength > 0 && !s.isEmpty()) {
             // This case means the first character itself is larger than maxByteLength.
             // The Go code `s[:total]` with total=0 would return empty.
            return "";
        }
        
        // Construct string from the valid portion of UTF-8 bytes
        return new String(utf8Bytes, 0, validByteEndIndex, StandardCharsets.UTF_8);
    }


    /**
     * Truncates a UTF-8 byte array to a specified maximum byte length, ensuring truncation
     * occurs at a valid UTF-8 character boundary.
     *
     * @param b          The UTF-8 byte array to truncate.
     * @param maxByteLength The maximum desired byte length of the truncated array.
     * @return The truncated byte array.
     */
    public static byte[] truncateBytesByUtf8Characters(byte[] b, int maxByteLength) {
        if (b == null || b.length == 0 || maxByteLength <= 0) {
            return new byte[0];
        }
        if (b.length <= maxByteLength) {
            return b;
        }

        // Iterate through the bytes, respecting UTF-8 character boundaries.
        // A simple way is to convert to string and use the logic from truncateStringByUtf8Bytes,
        // but that involves extra conversions. Let's try to do it directly on bytes.
        int currentLength = 0;
        int prevValidEnd = 0;

        // This is a simplified way to find character boundaries. A full UTF-8 decoder would be more robust.
        // For each byte, check if it's the start of a new UTF-8 character.
        // UTF-8 start bytes: 0xxxxxxx, 110xxxxx, 1110xxxx, 11110xxx
        // Continuation bytes: 10xxxxxx
        int i = 0;
        while (i < b.length) {
            int byteVal = b[i] & 0xFF;
            int charByteLength;

            if (byteVal < 0x80) { // 0xxxxxxx (ASCII)
                charByteLength = 1;
            } else if ((byteVal & 0xE0) == 0xC0) { // 110xxxxx
                charByteLength = 2;
            } else if ((byteVal & 0xF0) == 0xE0) { // 1110xxxx
                charByteLength = 3;
            } else if ((byteVal & 0xF8) == 0xF0) { // 11110xxx
                charByteLength = 4;
            } else {
                // This indicates an invalid UTF-8 start byte or a continuation byte where a start byte was expected.
                // For truncation, we should probably stop here to avoid including part of an invalid sequence.
                break; 
            }

            if (currentLength + charByteLength > maxByteLength) {
                break; // Next character would exceed the limit
            }

            // Check if all bytes of this character are present
            if (i + charByteLength > b.length) {
                // The byte array ends mid-character. This shouldn't happen with valid UTF-8
                // unless the input array itself is truncated. We stop before this partial char.
                break;
            }
            
            // Validate continuation bytes if charByteLength > 1
            boolean validChar = true;
            for (int j = 1; j < charByteLength; j++) {
                if ((b[i+j] & 0xC0) != 0x80) { // Must be 10xxxxxx
                    validChar = false;
                    break;
                }
            }
            if (!validChar) {
                // Invalid UTF-8 sequence found. Stop before it.
                break;
            }

            currentLength += charByteLength;
            prevValidEnd = currentLength;
            i += charByteLength;
        }
        
        if (prevValidEnd == 0 && maxByteLength > 0 && b.length > 0) {
            // First character itself is larger than maxByteLength or invalid
            return new byte[0];
        }

        byte[] truncatedBytes = new byte[prevValidEnd];
        System.arraycopy(b, 0, truncatedBytes, 0, prevValidEnd);
        return truncatedBytes;
    }

    private TruncateHelper() {
    }
}
