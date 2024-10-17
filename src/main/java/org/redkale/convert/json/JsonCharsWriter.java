/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.json;

import org.redkale.convert.Encodeable;
import static org.redkale.convert.json.JsonWriter.BYTE_COMMA;
import static org.redkale.convert.json.JsonWriter.BYTE_DQUOTE;
import static org.redkale.convert.json.JsonWriter.DEFAULT_SIZE;
import static org.redkale.convert.json.JsonWriter.DigitOnes;
import static org.redkale.convert.json.JsonWriter.DigitTens;
import org.redkale.util.StringWrapper;
import org.redkale.util.Utility;

/**
 * writeTo系列的方法输出的字符不能含特殊字符
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public class JsonCharsWriter extends JsonWriter {

    private static final char[] CHARS_TUREVALUE = "true".toCharArray();

    private static final char[] CHARS_FALSEVALUE = "false".toCharArray();

    private static final int TENTHOUSAND_MAX = 10001;

    private static final char[][] TENTHOUSAND_CHARS = new char[TENTHOUSAND_MAX][];
    private static final char[][] TENTHOUSAND_CHARS2 = new char[TENTHOUSAND_MAX][];

    static {
        for (int i = 0; i < TENTHOUSAND_CHARS.length; i++) {
            TENTHOUSAND_CHARS[i] = String.valueOf(i).toCharArray();
            TENTHOUSAND_CHARS2[i] = String.valueOf(-i).toCharArray();
        }
    }

    private int count;

    private char[] content;

    public JsonCharsWriter() {
        this(DEFAULT_SIZE);
    }

    public JsonCharsWriter(int size) {
        this.content = new char[size > DEFAULT_SIZE ? size : DEFAULT_SIZE];
    }

    // -----------------------------------------------------------------------
    /**
     * 返回指定至少指定长度的缓冲区
     *
     * @param len
     *
     * @return
     */
    private char[] expand(int len) {
        int ncount = count + len;
        if (ncount <= content.length) {
            return content;
        }
        char[] newdata = new char[Math.max(content.length * 2, ncount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return newdata;
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.count = 0;
        if (this.content != null && this.content.length > DEFAULT_SIZE * 100) {
            this.content = new char[DEFAULT_SIZE];
        }
        return true;
    }

    @Override
    public final boolean charsMode() {
        return true;
    }

    @Override
    public void writeTo(final char ch) { // 只能是 0 - 127 的字符
        expand(1);
        content[count++] = ch;
    }

    @Override
    public void writeTo(final char[] chs, final int start, final int len) { // 只能是 0 - 127 的字符
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    @Override
    public void writeTo(final byte b) { // 只能是 0 - 127 的字符
        expand(1);
        content[count++] = (char) (b & 0xff);
    }

    @Override
    public void writeTo(final byte[] bs, final int start, final int len) { // 只能是 0 - 127 的字符
        char[] chars = expand(len);
        for (int i = 0; i < len; i++) {
            chars[count + i] = (char) (bs[start + i] & 0xff);
        }
        count += len;
    }

    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger、BigDecimal转换的String
     *
     * @param quote 是否加双引号
     * @param value 非null且不含需要转义的字符的String值
     */
    @Override
    public void writeLatin1To(final boolean quote, final String value) {
        if (value == null) {
            writeNull();
            return;
        }
        int len = value.length();
        if (quote) {
            expand(len + 2);
            content[count++] = BYTE_DQUOTE;
            value.getChars(0, len, content, count);
            count += len;
            content[count++] = BYTE_DQUOTE;
        } else {
            expand(len);
            value.getChars(0, len, content, count);
            count += len;
        }
    }

    @Override
    public boolean writeFieldBooleanValue(Object fieldArray, boolean comma, boolean value) {
        char[] bs1 = (char[]) fieldArray;
        char[] bs2 = value ? CHARS_TUREVALUE : CHARS_FALSEVALUE;
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(1 + len1 + len2);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        return true;
    }

    @Override
    public boolean writeFieldShortValue(Object fieldArray, boolean comma, short value) {
        return writeFieldIntValue(fieldArray, comma, value);
    }

    @Override
    public boolean writeFieldByteValue(Object fieldArray, boolean comma, byte value) {
        char[] bs1 = (char[]) fieldArray;
        char[] bs2 = value >= 0 ? TENTHOUSAND_CHARS[value] : TENTHOUSAND_CHARS2[-value];
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(1 + len1 + len2);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        return true;
    }

    @Override
    public boolean writeFieldIntValue(Object fieldArray, boolean comma, int value) {
        char[] bs1 = (char[]) fieldArray;
        int len1 = bs1.length;
        char[] src = expand(bs1.length + 12);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += bs1.length;
        writeInt(value);
        return true;
    }

    @Override
    public boolean writeFieldLongValue(Object fieldArray, boolean comma, long value) {
        char[] bs1 = (char[]) fieldArray;
        int len1 = bs1.length;
        char[] src = expand(len1 + 21);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        writeLong(value);
        return true;
    }

    @Override
    public boolean writeFieldObjectValue(Object fieldArray, boolean comma, Encodeable encodeable, Object value) {
        if (value == null && !nullable()) {
            return comma;
        }
        char[] bs1 = (char[]) fieldArray;
        int len1 = bs1.length;
        char[] src = expand(1 + len1);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        encodeable.convertTo(this, value);
        return true;
    }

    @Override
    public boolean writeFieldStringValue(Object fieldArray, boolean comma, String value) {
        if (value == null || (tiny() && value.isEmpty())) {
            return comma;
        }
        char[] bs1 = (char[]) fieldArray;
        int len1 = bs1.length;
        char[] src = expand(1 + len1);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        writeString(value);
        return true;
    }

    @Override
    protected boolean writeFieldLatin1Value(Object fieldArray, boolean comma, boolean quote, String value) {
        if (value == null || (tiny() && value.isEmpty())) {
            return comma;
        }
        char[] bs1 = (char[]) fieldArray;
        int len1 = bs1.length;
        int len2 = value.length();

        if (quote) {
            char[] src = expand(len1 + len2 + 3);
            if (comma) src[count++] = BYTE_COMMA;
            System.arraycopy(bs1, 0, src, count, len1);
            count += len1;
            src[count++] = BYTE_DQUOTE;
            value.getChars(0, len2, content, count);
            count += len2;
            src[count++] = BYTE_DQUOTE;
        } else {
            char[] src = expand(1 + len1 + len2);
            if (comma) src[count++] = BYTE_COMMA;
            System.arraycopy(bs1, 0, src, count, len1);
            count += len1;
            value.getChars(0, len2, content, count);
            count += len2;
        }
        return true;
    }

    public byte[] toBytes() {
        return Utility.encodeUTF8(content, 0, count);
    }

    public int count() {
        return this.count;
    }

    @Override
    public void writeString(String value) {
        if (value == null) {
            writeNull();
            return;
        }
        final String str = value;
        if (Utility.isLatin1(str)) {
            writeEscapeLatinString(Utility.latin1ByteArray(str));
            return;
        }
        if (false) {
            byte[] utf16s = Utility.utf16ByteArray(value);
            if (utf16s != null) { // JDK9+
                writeEscapeUTF16String(utf16s);
                return;
            }
        }
        final int len = str.length();
        char[] chars = expand(len * 2 + 2);
        int curr = count;
        chars[curr++] = BYTE_DQUOTE;
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch < 14) {
                switch (ch) {
                    case '\n': // 10
                        chars[curr++] = '\\';
                        chars[curr++] = 'n';
                        break;
                    case '\r': // 13
                        chars[curr++] = '\\';
                        chars[curr++] = 'r';
                        break;
                    case '\t': // 8
                        chars[curr++] = '\\';
                        chars[curr++] = 't';
                        break;
                    case '\f': // 12
                        chars[curr++] = '\\';
                        chars[curr++] = 'f';
                        break;
                    case '\b': // 9
                        chars[curr++] = '\\';
                        chars[curr++] = 'b';
                        break;
                    default:
                        chars[curr++] = ch;
                        break;
                }
            } else if (ch == '"' || ch == '\\') {
                chars[curr++] = '\\';
                chars[curr++] = ch;
            } else {
                chars[curr++] = ch;
            }
        }
        chars[curr++] = BYTE_DQUOTE;
        count = curr;
    }

    private void writeEscapeUTF16String(byte[] value) {
        byte[] bytes = value;
        int len = bytes.length;
        char[] chars = expand(len + 2);
        int curr = count;
        chars[curr++] = BYTE_DQUOTE;
        byte b1, b2;
        for (int i = 0; i < len; i += 2) {
            b1 = bytes[i];
            b2 = bytes[i + 1];
            char ch = (char) ((b2 == 0 && b1 >= 0) ? b1 : ((b1 & 0xff) | ((b2 & 0xff) << 8)));
            if (ch < 14) {
                switch (ch) {
                    case '\n': // 10
                        chars[curr++] = '\\';
                        chars[curr++] = 'n';
                        break;
                    case '\r': // 13
                        chars[curr++] = '\\';
                        chars[curr++] = 'r';
                        break;
                    case '\t': // 8
                        chars[curr++] = '\\';
                        chars[curr++] = 't';
                        break;
                    case '\f': // 12
                        chars[curr++] = '\\';
                        chars[curr++] = 'f';
                        break;
                    case '\b': // 9
                        chars[curr++] = '\\';
                        chars[curr++] = 'b';
                        break;
                    default:
                        chars[curr++] = ch;
                        break;
                }
            } else if (ch == '"' || ch == '\\') {
                chars[curr++] = '\\';
                chars[curr++] = ch;
            } else {
                chars[curr++] = ch;
            }
        }
        chars[curr++] = BYTE_DQUOTE;
        count = curr;
    }

    private void writeEscapeLatinString(byte[] value) {
        char[] chars = expand(value.length * 2 + 2);
        int curr = count;
        chars[curr++] = BYTE_DQUOTE;
        for (byte b : value) {
            if (b == BYTE_DQUOTE) {
                chars[curr++] = '\\';
                chars[curr++] = BYTE_DQUOTE;
            } else if (b == '\\') {
                chars[curr++] = '\\';
                chars[curr++] = '\\';
            } else if (b < 14) {
                if (b == '\n') { // 10
                    chars[curr++] = '\\';
                    chars[curr++] = 'n';
                } else if (b == '\r') { // 13
                    chars[curr++] = '\\';
                    chars[curr++] = 'r';
                } else if (b == '\t') { // 8
                    chars[curr++] = '\\';
                    chars[curr++] = 't';
                } else if (b == '\f') { // 12
                    chars[curr++] = '\\';
                    chars[curr++] = 'f';
                } else if (b == '\b') { // 9
                    chars[curr++] = '\\';
                    chars[curr++] = 'b';
                } else {
                    chars[curr++] = (char) b;
                }
            } else {
                chars[curr++] = (char) b;
            }
        }
        chars[curr++] = BYTE_DQUOTE;
        count = curr;
    }

    @Override
    public String toString() {
        return new String(content, 0, count);
    }

    // ----------------------------------------------------------------------------------------------
    @Override
    public void writeBoolean(boolean value) {
        writeTo(value ? CHARS_TUREVALUE : CHARS_FALSEVALUE);
    }

    @Override
    public void writeInt(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_CHARS[value]);
            return;
        }
        if (value < 0 && value > -TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_CHARS2[-value]);
            return;
        }
        final boolean negative = value < 0;
        int size = stringSize(value);
        char[] chars = expand(size);
        int charPos = count + size;
        int i = negative ? value : -value;
        int q, r;
        // Generate two digits per iteration
        while (i <= -100) {
            q = i / 100;
            r = (q * 100) - i;
            i = q;
            chars[--charPos] = DigitOnes[r];
            chars[--charPos] = DigitTens[r];
        }
        // We know there are at most two digits left at this point.
        chars[--charPos] = DigitOnes[-i];
        if (i < -9) {
            chars[--charPos] = DigitTens[-i];
        }

        if (negative) {
            chars[--charPos] = '-';
        }
        count += size;
    }

    @Override
    public void writeLong(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_CHARS[(int) value]);
            return;
        }
        if (value < 0 && value > -TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_CHARS2[(int) -value]);
            return;
        }
        final boolean negative = value < 0;
        int size = stringSize(value);
        char[] chars = expand(size);
        int charPos = count + size;
        long i = negative ? value : -value;
        long q;
        int r;
        while (i <= Integer.MIN_VALUE) {
            q = i / 100;
            r = (int) ((q * 100) - i);
            i = q;
            chars[--charPos] = DigitOnes[r];
            chars[--charPos] = DigitTens[r];
        }
        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            r = (q2 * 100) - i2;
            i2 = q2;
            chars[--charPos] = DigitOnes[r];
            chars[--charPos] = DigitTens[r];
        }
        // We know there are at most two digits left at this point.
        chars[--charPos] = DigitOnes[-i2];
        if (i2 < -9) {
            chars[--charPos] = DigitTens[-i2];
        }

        if (negative) {
            chars[--charPos] = '-';
        }
        count += size;
    }

    @Override
    public void writeWrapper(StringWrapper wrapper) {
        if (wrapper == null || wrapper.getValue() == null) {
            writeNull();
            return;
        }
        String value = wrapper.getValue();
        if (Utility.isLatin1(value)) {
            writeTo(Utility.latin1ByteArray(value));
            return;
        }
        int len2 = value.length();
        char[] chars = expand(len2);
        value.getChars(0, len2, chars, count);
        count += len2;
    }
}
