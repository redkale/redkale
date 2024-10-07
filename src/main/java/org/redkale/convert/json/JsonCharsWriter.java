/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.convert.json;

import static org.redkale.convert.json.JsonWriter.BYTE_DQUOTE;
import static org.redkale.convert.json.JsonWriter.BYTE_LBRACE;
import static org.redkale.convert.json.JsonWriter.BYTE_RBRACE;
import static org.redkale.convert.json.JsonWriter.DEFAULT_SIZE;
import static org.redkale.convert.json.JsonWriter.DigitOnes;
import static org.redkale.convert.json.JsonWriter.DigitTens;
import static org.redkale.convert.json.JsonWriter.digits;
import static org.redkale.convert.json.JsonWriter.sizeTable;
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
    public void writeFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[(int) value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[(int) -value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, final String value) {
        char[] bs1 = fieldChars;
        int len1 = bs1.length;
        int len2 = value.length();
        char[] src = expand(len1 + len2 + 2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        src[count++] = BYTE_DQUOTE;
        value.getChars(0, len2, content, count);
        count += len2;
        src[count++] = BYTE_DQUOTE;
    }

    @Override
    public void writeLastFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value) {
        char[] bs1 = fieldChars;
        char[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[(int) value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[(int) -value]
                        : String.valueOf(value).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        char[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, final String value) {
        if (value == null && nullable()) {
            writeTo(fieldChars);
            writeNull();
            writeTo(BYTE_RBRACE);
            return;
        }
        if (value == null || (tiny() && value.isEmpty())) {
            expand(1);
            content[count++] = BYTE_RBRACE;
        } else {
            int len1 = fieldChars.length;
            int len2 = value.length();
            char[] src = expand(len1 + len2 + 3);
            System.arraycopy(fieldChars, 0, src, count, len1);
            count += len1;
            content[count++] = BYTE_DQUOTE;
            value.getChars(0, len2, content, count);
            count += len2;
            content[count++] = BYTE_DQUOTE;
            content[count++] = BYTE_RBRACE;
        }
    }

    @Override // firstFieldBytes 必须带{开头
    public void writeObjectByOnlyOneLatin1FieldValue(
            byte[] firstFieldBytes, char[] firstFieldChars, final String value) {
        if (value == null && nullable()) {
            writeTo(BYTE_LBRACE);
            writeTo(firstFieldChars);
            writeNull();
            writeTo(BYTE_RBRACE);
            return;
        }
        if (value == null || (tiny() && value.isEmpty())) {
            expand(2);
            content[count++] = BYTE_LBRACE;
            content[count++] = BYTE_RBRACE;
        } else {
            int len1 = firstFieldChars.length;
            int len2 = value.length();
            char[] src = expand(len1 + len2 + 3);
            System.arraycopy(firstFieldChars, 0, src, count, len1);
            count += len1;
            content[count++] = BYTE_DQUOTE;
            value.getChars(0, len2, content, count);
            count += len2;
            content[count++] = BYTE_DQUOTE;
            content[count++] = BYTE_RBRACE;
        }
    }

    @Override // firstFieldBytes 必须带{开头, lastFieldBytes必须,开头
    public void writeObjectByOnlyTwoIntFieldValue(
            final byte[] firstFieldBytes,
            final char[] firstFieldChars,
            final int value1,
            final byte[] lastFieldBytes,
            final char[] lastFieldChars,
            final int value2) {
        char[] bs1 = firstFieldChars;
        char[] bs2 = (value1 >= 0 && value1 < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value1]
                : ((value1 < 0 && value1 > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value1]
                        : String.valueOf(value1).toCharArray());
        char[] bs3 = lastFieldChars;
        char[] bs4 = (value2 >= 0 && value2 < TENTHOUSAND_MAX)
                ? TENTHOUSAND_CHARS[value2]
                : ((value2 < 0 && value2 > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_CHARS2[-value2]
                        : String.valueOf(value2).toCharArray());
        int len1 = bs1.length;
        int len2 = bs2.length;
        int len3 = bs3.length;
        int len4 = bs4.length;
        char[] src = expand(len1 + len2 + len3 + len4 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        System.arraycopy(bs3, 0, src, count, len3);
        count += len3;
        System.arraycopy(bs4, 0, src, count, len4);
        count += len4;
        src[count++] = BYTE_RBRACE;
    }

    public byte[] toBytes() {
        return Utility.encodeUTF8(content, 0, count);
    }

    public int count() {
        return this.count;
    }

    @Override
    public void writeString(String value) {
        writeString(true, value);
    }

    @Override
    public void writeString(final boolean quote, String value) {
        if (value == null) {
            writeNull();
            return;
        }
        final String str = value;
        if (Utility.isLatin1(str)) {
            writeEscapeLatinString(quote, Utility.latin1ByteArray(str));
            return;
        }
        if (false) {
            byte[] utf16s = Utility.utf16ByteArray(value);
            if (utf16s != null) { // JDK9+
                writeEscapeUTF16String(quote, utf16s);
                return;
            }
        }
        final int len = str.length();
        char[] chars = expand(len * 2 + 2);
        int curr = count;
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
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
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
        count = curr;
    }

    private void writeEscapeUTF16String(final boolean quote, byte[] value) {
        byte[] bytes = value;
        int len = bytes.length;
        char[] chars = expand(len + 2);
        int curr = count;
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
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
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
        count = curr;
    }

    private void writeEscapeLatinString(final boolean quote, byte[] value) {
        char[] chars = expand(value.length * 2 + 2);
        int curr = count;
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
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
        if (quote) {
            chars[curr++] = BYTE_DQUOTE;
        }
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
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) value = -value;
        int size;
        for (int i = 0; ; i++) {
            if (value <= sizeTable[i]) {
                size = i + 1;
                break;
            }
        }
        if (sign != 0) size++; // 负数
        expand(size);

        int q, r;
        int charPos = count + size;

        // Generate two digits per iteration
        while (value >= 65536) {
            q = value / 100;
            // really: r = i - (q * 100);
            r = value - ((q << 6) + (q << 5) + (q << 2));
            value = q;
            content[--charPos] = DigitOnes[r];
            content[--charPos] = DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (; ; ) {
            q = (value * 52429) >>> (16 + 3);
            r = value - ((q << 3) + (q << 1)); // r = i-(q*10) ...
            content[--charPos] = digits[r];
            value = q;
            if (value == 0) break;
        }
        if (sign != 0) content[--charPos] = sign;
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
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) value = -value;
        int size = 19;
        long p = 10;
        for (int i = 1; i < 19; i++) {
            if (value < p) {
                size = i;
                break;
            }
            p = 10 * p;
        }
        if (sign != 0) size++; // 负数
        expand(size);

        long q;
        int r;
        int charPos = count + size;

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (value > Integer.MAX_VALUE) {
            q = value / 100;
            // really: r = i - (q * 100);
            r = (int) (value - ((q << 6) + (q << 5) + (q << 2)));
            value = q;
            content[--charPos] = DigitOnes[r];
            content[--charPos] = DigitTens[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) value;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            content[--charPos] = DigitOnes[r];
            content[--charPos] = DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        for (; ; ) {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1)); // r = i2-(q2*10) ...
            content[--charPos] = digits[r];
            i2 = q2;
            if (i2 == 0) break;
        }
        if (sign != 0) content[--charPos] = sign;
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
