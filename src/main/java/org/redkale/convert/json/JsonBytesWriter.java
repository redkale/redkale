/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.redkale.convert.*;
import static org.redkale.convert.json.JsonWriter.*;
import org.redkale.util.*;

/**
 * writeTo系列的方法输出的字符不能含特殊字符
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public class JsonBytesWriter extends JsonWriter implements ByteTuple {

    private static final byte[] BYTES_TUREVALUE = "true".getBytes();

    private static final byte[] BYTES_FALSEVALUE = "false".getBytes();

    private static final byte[] BYTES_NULL = new byte[] {'n', 'u', 'l', 'l'};

    private static final int TENTHOUSAND_MAX = 10001;

    private static final byte[][] TENTHOUSAND_BYTES = new byte[TENTHOUSAND_MAX][];
    private static final byte[][] TENTHOUSAND_BYTES2 = new byte[TENTHOUSAND_MAX][];

    static {
        for (int i = 0; i < TENTHOUSAND_BYTES.length; i++) {
            TENTHOUSAND_BYTES[i] = String.valueOf(i).getBytes();
            TENTHOUSAND_BYTES2[i] = String.valueOf(-i).getBytes();
        }
    }

    private int count;

    private byte[] content;

    public JsonBytesWriter() {
        this(DEFAULT_SIZE);
    }

    public JsonBytesWriter(int size) {
        this.content = new byte[size > DEFAULT_SIZE ? size : DEFAULT_SIZE];
    }

    public JsonBytesWriter(ByteArray array) {
        this.content = array.content();
        this.count = array.length();
    }

    public JsonBytesWriter(int features, ByteArray array) {
        this.features = features;
        this.content = array.content();
        this.count = array.length();
    }

    protected byte[] expand(int len) {
        int newcount = count + len;
        if (newcount <= content.length) {
            return content;
        }
        byte[] newdata = new byte[Math.max(content.length * 3 / 2, newcount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return newdata;
    }

    @Override
    public boolean recycle() {
        super.recycle();
        this.count = 0;
        if (this.content != null && this.content.length > DEFAULT_SIZE * 100) {
            this.content = new byte[DEFAULT_SIZE];
        }
        return true;
    }

    @Override
    public byte[] content() {
        return content;
    }

    @Override
    public int offset() {
        return 0;
    }

    @Override
    public int length() {
        return count;
    }

    /**
     * 将本对象的内容引用复制给array
     *
     * @param array ByteArray
     */
    public void directTo(ByteArray array) {
        array.directFrom(content, count);
    }

    @Override
    public final void writeNull() {
        writeTo(BYTES_NULL);
    }

    @Override
    public final void writeField(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        if (this.comma) {
            writeTo(BYTE_COMMA);
        }
        if (member != null) {
            byte[] bs = member.getJsonFieldNameBytes();
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
        } else {
            writeLatin1To(true, fieldName);
            writeTo(BYTE_COLON);
        }
    }

    @Override
    public void writeTo(final byte ch) { // 只能是 0 - 127 的字符
        expand(1);
        content[count++] = ch;
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) { // 只能是 0 - 127 的字符
        expand(len);
        System.arraycopy(chs, start, content, count, len);
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
        byte[] bs = Utility.latin1ByteArray(value);
        int len = bs.length;
        if (quote) {
            byte[] src = expand(len + 2);
            src[count++] = BYTE_DQUOTE;
            System.arraycopy(bs, 0, src, count, bs.length);
            count += len;
            src[count++] = BYTE_DQUOTE;
        } else {
            byte[] src = expand(len);
            System.arraycopy(bs, 0, src, count, bs.length);
            count += len;
        }
    }

    @Override
    public boolean writeFieldBooleanValue(Object fieldArray, boolean comma, boolean value) {
        if (!value && tiny()) {
            return comma;
        }
        byte[] bs1 = (byte[]) fieldArray;
        byte[] bs2 = value ? BYTES_TUREVALUE : BYTES_FALSEVALUE;
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(1 + len1 + len2);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        return true;
    }

    @Override
    public boolean writeFieldByteValue(Object fieldArray, boolean comma, byte value) {
        byte[] bs1 = (byte[]) fieldArray;
        byte[] bs2 = value >= 0 ? TENTHOUSAND_BYTES[value] : TENTHOUSAND_BYTES2[-value];
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(1 + len1 + len2);
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
    public boolean writeFieldIntValue(Object fieldArray, boolean comma, int value) {
        byte[] bs1 = (byte[]) fieldArray;
        int len1 = bs1.length;
        byte[] src = expand(len1 + 12);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        writeInt(value);
        return true;
    }

    @Override
    public boolean writeFieldLongValue(Object fieldArray, boolean comma, long value) {
        byte[] bs1 = (byte[]) fieldArray;
        int len1 = bs1.length;
        byte[] src = expand(len1 + 21);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        writeLong(value);
        return true;
    }

    @Override
    public boolean writeFieldObjectValue(Object fieldArray, boolean comma, Encodeable encodeable, Object value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        byte[] bs1 = (byte[]) fieldArray;
        int len1 = bs1.length;
        byte[] src = expand(1 + len1);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        encodeable.convertTo(this, value);
        return true;
    }

    @Override
    public boolean writeFieldStringValue(Object fieldArray, boolean comma, String value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        if (tiny() && value.isEmpty()) {
            return comma;
        }
        byte[] bs1 = (byte[]) fieldArray;
        int len1 = bs1.length;
        byte[] src = expand(1 + len1);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        writeString(value);
        return true;
    }

    @Override
    protected void writeFieldNull(Object fieldArray, boolean comma) {
        byte[] bs1 = (byte[]) fieldArray;
        byte[] bs2 = BYTES_NULL;
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(1 + len1 + len2);
        if (comma) src[count++] = BYTE_COMMA;
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    protected boolean writeFieldLatin1Value(Object fieldArray, boolean comma, boolean quote, String value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        if (tiny() && value.isEmpty()) {
            return comma;
        }
        byte[] bs1 = (byte[]) fieldArray;
        byte[] bs2 = Utility.latin1ByteArray(value);
        int len1 = bs1.length;
        int len2 = bs2.length;
        if (quote) {
            byte[] src = expand(len1 + len2 + 3);
            if (comma) src[count++] = BYTE_COMMA;
            System.arraycopy(bs1, 0, src, count, len1);
            count += len1;
            src[count++] = BYTE_DQUOTE;
            System.arraycopy(bs2, 0, src, count, len2);
            count += len2;
            src[count++] = BYTE_DQUOTE;
        } else {
            byte[] src = expand(1 + len1 + len2);
            if (comma) src[count++] = BYTE_COMMA;
            System.arraycopy(bs1, 0, src, count, len1);
            count += len1;
            System.arraycopy(bs2, 0, src, count, len2);
            count += len2;
        }
        return true;
    }

    public JsonBytesWriter clear() {
        this.count = 0;
        return this;
    }

    /**
     * 直接获取全部数据, 实际数据需要根据count长度来截取
     *
     * @return byte[]
     */
    public byte[] directBytes() {
        return content;
    }

    public void completed(ConvertBytesHandler handler, Consumer<JsonBytesWriter> callback) {
        handler.completed(content, 0, count, callback, this);
    }

    public byte[] toBytes() {
        byte[] copy = new byte[count];
        System.arraycopy(content, 0, copy, 0, count);
        return copy;
    }

    @Override
    public void writeString(String value) {
        if (value == null) {
            writeNull();
            return;
        }
        if (Utility.isLatin1(value)) {
            writeEscapeLatinString(Utility.latin1ByteArray(value));
            return;
        }
        byte[] utf16s = Utility.utf16ByteArray(value);
        if (utf16s != null) { // JDK9+
            writeEscapeUTF16String(utf16s);
            return;
        }
        int len = value.length();
        byte[] bytes = expand(len * 4 + 2);
        int curr = count;
        bytes[curr++] = BYTE_DQUOTE;
        for (int i = 0; i < len; i++) {
            char ch = value.charAt(i);
            if (ch < 14) {
                switch (ch) {
                    case '\n': // 10
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'n';
                        break;
                    case '\r': // 13
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'r';
                        break;
                    case '\t': // 8
                        bytes[curr++] = '\\';
                        bytes[curr++] = 't';
                        break;
                    case '\f': // 12
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'f';
                        break;
                    case '\b': // 9
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'b';
                        break;
                    default:
                        bytes[curr++] = (byte) ch;
                        break;
                }
            } else if (ch == '"' || ch == '\\') {
                bytes[curr++] = '\\';
                bytes[curr++] = (byte) ch;
            } else if (ch < 0x80) {
                bytes[curr++] = (byte) ch;
            } else if (ch < 0x800) {
                bytes[curr++] = (byte) (0xc0 | (ch >> 6));
                bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
            } else if (Character.isSurrogate(ch)) { // 连取两个
                int uc = Character.toCodePoint(ch, value.charAt(++i));
                bytes[curr++] = (byte) (0xf0 | (uc >> 18));
                bytes[curr++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                bytes[curr++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                bytes[curr++] = (byte) (0x80 | (uc & 0x3f));
            } else {
                bytes[curr++] = (byte) (0xe0 | (ch >> 12));
                bytes[curr++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
            }
        }
        bytes[curr++] = BYTE_DQUOTE;
        count = curr;
    }

    // see java.lang.StringCoding.encodeUTF8_UTF16 方法
    private void writeEscapeUTF16String(byte[] value) {
        int len = value.length;
        byte[] bytes = expand(len * 4 + 2);
        int curr = count;
        bytes[curr++] = BYTE_DQUOTE;
        byte[] src = value;
        int i = 0;
        while (i < len) {
            byte b = src[i];
            byte b2 = src[i + 1];
            i += 2;
            if (b2 == 0 && b >= 0) {
                if (b == BYTE_DQUOTE) {
                    bytes[curr++] = '\\';
                    bytes[curr++] = BYTE_DQUOTE;
                } else if (b == '\\') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = '\\';
                } else if (b < 32) {
                    if (b == '\n') {
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'n';
                    } else if (b == '\r') {
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'r';
                    } else if (b == '\f') {
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'f';
                    } else if (b == '\b') {
                        bytes[curr++] = '\\';
                        bytes[curr++] = 'b';
                    } else if (b == '\t') {
                        bytes[curr++] = '\\';
                        bytes[curr++] = 't';
                    } else {
                        bytes[curr++] = b;
                    }
                } else {
                    bytes[curr++] = b;
                }
            } else {
                char c = (char) ((b & 0xff) | ((b2 & 0xff) << 8));
                if (c < 0x800) {
                    bytes[curr++] = (byte) (0xc0 | (c >> 6));
                    bytes[curr++] = (byte) (0x80 | (c & 0x3f));
                } else if (c >= MIN_HIGH_SURROGATE && c < MAX_LOW_SURROGATE_MORE) { // Character.isSurrogate(c)
                    int uc = -1;
                    if (c < MAX_HIGH_SURROGATE_MORE && i < len) { // Character.isHighSurrogate(c)
                        char c2 = (char) ((src[i] & 0xff) | ((src[i + 1] & 0xff) << 8));
                        if (c2 >= MIN_LOW_SURROGATE && c2 < MAX_LOW_SURROGATE_MORE) { // Character.isLowSurrogate(c2)
                            uc = (c << 10) + c2 + MIN_SUPPLEMENTARY_CODE_POINT_MORE;
                        }
                    }
                    if (uc < 0) {
                        bytes[curr++] = '?';
                    } else {
                        bytes[curr++] = (byte) (0xf0 | (uc >> 18));
                        bytes[curr++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                        bytes[curr++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                        bytes[curr++] = (byte) (0x80 | (uc & 0x3f));
                        i += 2; // 2 chars
                    }
                } else {
                    // 3 bytes, 16 bits
                    bytes[curr++] = (byte) (0xe0 | (c >> 12));
                    bytes[curr++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                    bytes[curr++] = (byte) (0x80 | (c & 0x3f));
                }
            }
        }
        bytes[curr++] = BYTE_DQUOTE;
        count = curr;
    }

    private static final char MIN_LOW_SURROGATE = '\uDC00';
    private static final char MAX_LOW_SURROGATE = '\uDFFF';
    private static final char MIN_HIGH_SURROGATE = '\uD800';
    private static final char MAX_HIGH_SURROGATE = '\uDBFF';
    private static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    private static final char MAX_LOW_SURROGATE_MORE = MAX_LOW_SURROGATE + 1;
    private static final char MAX_HIGH_SURROGATE_MORE = MAX_HIGH_SURROGATE + 1;
    private static final int MIN_SUPPLEMENTARY_CODE_POINT_MORE =
            (MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE << 10) - MIN_LOW_SURROGATE);

    private void writeEscapeLatinString(byte[] value) {
        byte[] bytes = expand(value.length * 2 + 2);
        int curr = count;
        bytes[curr++] = BYTE_DQUOTE;
        for (byte b : value) {
            if (b == BYTE_DQUOTE) {
                bytes[curr++] = '\\';
                bytes[curr++] = BYTE_DQUOTE;
            } else if (b == '\\') {
                bytes[curr++] = '\\';
                bytes[curr++] = '\\';
            } else if (b < 32) {
                if (b == '\n') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'n';
                } else if (b == '\r') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'r';
                } else if (b == '\f') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'f';
                } else if (b == '\b') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'b';
                } else if (b == '\t') {
                    bytes[curr++] = '\\';
                    bytes[curr++] = 't';
                } else {
                    bytes[curr++] = b;
                }
            } else {
                bytes[curr++] = b;
            }
        }
        bytes[curr++] = BYTE_DQUOTE;
        count = curr;
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
        // 不能使用writeEscapeUTF16String
        //
        byte[] bytes = expand(value.length() * 4 + 2);
        int curr = count;
        int len = value.length();
        for (int i = 0; i < len; i++) {
            char ch = value.charAt(i);
            if (ch < 0x80) {
                bytes[curr++] = (byte) ch;
            } else if (ch < 0x800) {
                bytes[curr++] = (byte) (0xc0 | (ch >> 6));
                bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
            } else if (Character.isSurrogate(ch)) { // 连取两个
                int uc = Character.toCodePoint(ch, value.charAt(++i));
                bytes[curr++] = (byte) (0xf0 | (uc >> 18));
                bytes[curr++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                bytes[curr++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                bytes[curr++] = (byte) (0x80 | (uc & 0x3f));
            } else {
                bytes[curr++] = (byte) (0xe0 | (ch >> 12));
                bytes[curr++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
            }
        }
        count = curr;
    }

    @Override
    public final String toString() {
        return new String(content, 0, count, StandardCharsets.UTF_8);
    }

    // ----------------------------------------------------------------------------------------------
    @Override
    public void writeBoolean(boolean value) {
        byte[] bs = value ? BYTES_TUREVALUE : BYTES_FALSEVALUE;
        expand(bs.length);
        System.arraycopy(bs, 0, content, count, bs.length);
        count += bs.length;
    }

    @Override
    public void writeInt(int value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            byte[] bs = TENTHOUSAND_BYTES[value];
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
            return;
        }
        if (value < 0 && value > -TENTHOUSAND_MAX) {
            byte[] bs = TENTHOUSAND_BYTES2[-value];
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
            return;
        }
        final boolean negative = value < 0;
        int size = stringSize(value);
        byte[] bytes = expand(size);
        int charPos = count + size;
        int i = negative ? value : -value;
        int q, r;
        // Generate two digits per iteration
        while (i <= -100) {
            q = i / 100;
            r = (q * 100) - i;
            i = q;
            bytes[--charPos] = (byte) DigitOnes[r];
            bytes[--charPos] = (byte) DigitTens[r];
        }
        // We know there are at most two digits left at this point.
        bytes[--charPos] = (byte) DigitOnes[-i];
        if (i < -9) {
            bytes[--charPos] = (byte) DigitTens[-i];
        }
        if (negative) {
            bytes[--charPos] = BYTE_NEGATIVE;
        }
        count += size;
    }

    @Override
    public void writeLong(long value) {
        if (value >= 0 && value < TENTHOUSAND_MAX) {
            byte[] bs = TENTHOUSAND_BYTES[(int) value];
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
            return;
        }
        if (value < 0 && value > -TENTHOUSAND_MAX) {
            byte[] bs = TENTHOUSAND_BYTES2[(int) -value];
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
            return;
        }
        final boolean negative = value < 0;
        int size = stringSize(value);
        byte[] bytes = expand(size);
        int charPos = count + size;
        long i = negative ? value : -value;
        long q;
        int r;
        while (i <= Integer.MIN_VALUE) {
            q = i / 100;
            r = (int) ((q * 100) - i);
            i = q;
            bytes[--charPos] = (byte) DigitOnes[r];
            bytes[--charPos] = (byte) DigitTens[r];
        }
        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            r = (q2 * 100) - i2;
            i2 = q2;
            bytes[--charPos] = (byte) DigitOnes[r];
            bytes[--charPos] = (byte) DigitTens[r];
        }
        // We know there are at most two digits left at this point.
        bytes[--charPos] = (byte) DigitOnes[-i2];
        if (i2 < -9) {
            bytes[--charPos] = (byte) DigitTens[-i2];
        }

        if (negative) {
            bytes[--charPos] = BYTE_NEGATIVE;
        }
        count += size;
    }
}
