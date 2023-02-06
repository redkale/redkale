/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.nio.ByteBuffer;
import org.redkale.util.Utility;

/**
 *
 * writeTo系列的方法输出的字符不能含特殊字符
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 *
 * @deprecated 2.5.0 JDK9以上用byte[]代替char[]会有更好的性能
 */
@Deprecated(since = "2.5.0")
public class JsonCharsWriter extends JsonWriter {

    private static final char[] CHARS_TUREVALUE = "true".toCharArray();

    private static final char[] CHARS_FALSEVALUE = "false".toCharArray();

    private static final int TENTHOUSAND_MAX = 10001;

    private static final char[][] TENTHOUSAND_BYTES = new char[TENTHOUSAND_MAX][];

    static {
        for (int i = 0; i < TENTHOUSAND_BYTES.length; i++) {
            TENTHOUSAND_BYTES[i] = String.valueOf(i).toCharArray();
        }
    }

    private int count;

    private char[] content;

    public JsonCharsWriter() {
        this(defaultSize);
    }

    public JsonCharsWriter(int size) {
        this.content = new char[size > 1024 ? size : 1024];
    }

    //-----------------------------------------------------------------------
    /**
     * 返回指定至少指定长度的缓冲区
     *
     * @param len
     *
     * @return
     */
    private char[] expand(int len) {
        int newcount = count + len;
        if (newcount <= content.length) {
            return content;
        }
        char[] newdata = new char[Math.max(content.length * 3 / 2, newcount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return newdata;
    }

    @Override
    public void writeTo(final char ch) { //只能是 0 - 127 的字符
        expand(1);
        content[count++] = ch;
    }

    @Override
    public void writeTo(final char[] chs, final int start, final int len) { //只能是 0 - 127 的字符
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    @Override
    public void writeTo(final byte ch) { //只能是 0 - 127 的字符
        expand(1);
        content[count++] = (char) ch;
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) { //只能是 0 - 127 的字符
        expand(len);
        for (int i = 0; i < len; i++) {
            content[count + i] = (char) chs[start + i];
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
        int len = value.length();
        expand(len + (quote ? 2 : 0));
        if (quote) {
            content[count++] = '"';
        }
        value.getChars(0, len, content, count);
        count += len;
        if (quote) {
            content[count++] = '"';
        }
    }

    @Override
    public void writeFieldShortValue(final byte[] fieldBytes, final short value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
    }

    @Override
    public void writeFieldIntValue(final byte[] fieldBytes, final int value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
    }

    @Override
    public void writeFieldLongValue(final byte[] fieldBytes, final long value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
    }

    @Override
    public void writeFieldLatin1Value(final byte[] fieldBytes, final String value) {
        int len1 = fieldBytes.length;
        int len2 = value.length();
        expand(len1 + len2 + 2);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        content[count++] = '"';
        value.getChars(0, len2, content, count);
        count += len2;
        content[count++] = '"';
    }

    @Override
    public void writeLastFieldShortValue(final byte[] fieldBytes, final short value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2 + 1);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
        content[count++] = '}';
    }

    @Override
    public void writeLastFieldIntValue(final byte[] fieldBytes, final int value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2 + 1);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
        content[count++] = '}';
    }

    @Override
    public void writeLastFieldLongValue(final byte[] fieldBytes, final long value) {
        String val = String.valueOf(value);
        int len1 = fieldBytes.length;
        int len2 = val.length();
        expand(len1 + len2 + 1);
        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) fieldBytes[i];
        }
        count += len1;
        val.getChars(0, len2, content, count);
        count += len2;
        content[count++] = '}';
    }

    @Override
    public void writeLastFieldLatin1Value(final byte[] fieldBytes, final String value) {
        if (value == null || (tiny && value.isEmpty())) {
            expand(1);
            content[count++] = '}';
        } else {
            int len1 = fieldBytes.length;
            int len2 = value.length();
            expand(len1 + len2 + 3);
            for (int i = 0; i < len1; i++) {
                content[count + i] = (char) fieldBytes[i];
            }
            count += len1;
            content[count++] = '"';
            value.getChars(0, len2, content, count);
            count += len2;
            content[count++] = '"';
            content[count++] = '}';
        }
    }

    @Override //firstFieldBytes 必须带{开头
    public void writeObjectByOnlyOneLatin1FieldValue(final byte[] firstFieldBytes, final String value) {
        if (value == null || (tiny && value.isEmpty())) {
            expand(2);
            content[count++] = '{';
            content[count++] = '}';
        } else {
            byte[] fieldBytes = firstFieldBytes;
            int len1 = fieldBytes.length;
            int len2 = value.length();
            expand(len1 + len2 + 3);
            for (int i = 0; i < len1; i++) {
                content[count + i] = (char) fieldBytes[i];
            }
            count += len1;
            content[count++] = '"';
            value.getChars(0, len2, content, count);
            count += len2;
            content[count++] = '"';
            content[count++] = '}';
        }
    }

    @Override //firstFieldBytes 必须带{开头, lastFieldBytes必须,开头
    public void writeObjectByOnlyTwoIntFieldValue(final byte[] firstFieldBytes, final int value1, final byte[] lastFieldBytes, final int value2) {
        String val1 = String.valueOf(value1);
        String val2 = String.valueOf(value2);
        int len1 = firstFieldBytes.length;
        int len2 = val1.length();
        int len3 = lastFieldBytes.length;
        int len4 = val2.length();
        expand(len1 + len2 + len3 + len4 + 1);

        for (int i = 0; i < len1; i++) {
            content[count + i] = (char) firstFieldBytes[i];
        }
        count += len1;
        val1.getChars(0, len2, content, count);
        count += len2;

        for (int i = 0; i < len3; i++) {
            content[count + i] = (char) lastFieldBytes[i];
        }
        count += len3;
        val2.getChars(0, len4, content, count);
        count += len4;

        content[count++] = '}';
    }

    @Override
    protected boolean recycle() {
        super.recycle();
        this.count = 0;
        this.specificObjectType = null;
        if (this.content != null && this.content.length > defaultSize) {
            this.content = new char[defaultSize];
        }
        return true;
    }

    public ByteBuffer[] toBuffers() {
        return new ByteBuffer[]{ByteBuffer.wrap(Utility.encodeUTF8(content, 0, count))};
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
        expand(value.length() * 2 + 2);
        if (quote) {
            content[count++] = '"';
        }
        for (char ch : Utility.charArray(value)) {
            switch (ch) {
                case '\n':
                    content[count++] = '\\';
                    content[count++] = 'n';
                    break;
                case '\r':
                    content[count++] = '\\';
                    content[count++] = 'r';
                    break;
                case '\t':
                    content[count++] = '\\';
                    content[count++] = 't';
                    break;
                case '\\':
                    content[count++] = '\\';
                    content[count++] = ch;
                    break;
                case '"':
                    content[count++] = '\\';
                    content[count++] = ch;
                    break;
                default:
                    content[count++] = ch;
                    break;
            }
        }
        if (quote) {
            content[count++] = '"';
        }
    }

    @Override
    public String toString() {
        return new String(content, 0, count);
    }

    //----------------------------------------------------------------------------------------------
    @Override
    public void writeBoolean(boolean value) {
        writeTo(value ? CHARS_TUREVALUE : CHARS_FALSEVALUE);
    }

    @Override
    public void writeInt(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_BYTES[value]);
            return;
        }
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) {
            value = -value;
        }
        int size;
        for (int i = 0;; i++) {
            if (value <= sizeTable[i]) {
                size = i + 1;
                break;
            }
        }
        if (sign != 0) {
            size++; //负数
        }
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
        for (;;) {
            q = (value * 52429) >>> (16 + 3);
            r = value - ((q << 3) + (q << 1));  // r = i-(q*10) ...
            content[--charPos] = digits[r];
            value = q;
            if (value == 0) {
                break;
            }
        }
        if (sign != 0) {
            content[--charPos] = sign;
        }
        count += size;
    }

    @Override
    public void writeLong(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            writeTo(TENTHOUSAND_BYTES[(int) value]);
            return;
        }
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) {
            value = -value;
        }
        int size = 19;
        long p = 10;
        for (int i = 1; i < 19; i++) {
            if (value < p) {
                size = i;
                break;
            }
            p = 10 * p;
        }
        if (sign != 0) {
            size++; //负数
        }
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
        for (;;) {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            content[--charPos] = digits[r];
            i2 = q2;
            if (i2 == 0) {
                break;
            }
        }
        if (sign != 0) {
            content[--charPos] = sign;
        }
        count += size;
    }

}
