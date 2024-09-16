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
        this.content = new byte[size > 1024 ? size : 1024];
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
    public void writeNull() {
        writeTo(BYTES_NULL);
    }

    @Override
    public final void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos) {
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
    public void writeTo(final char ch) { // 只能是 0 - 127 的字符
        expand(1);
        content[count++] = (byte) ch;
    }

    @Override
    public void writeTo(final char[] chs, final int start, final int len) { // 只能是 0 - 127 的字符
        expand(len);
        for (int i = 0; i < len; i++) {
            content[count + i] = (byte) chs[start + i];
        }
        count += len;
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
    public void writeFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[(int) value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[(int) -value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
    }

    @Override
    public void writeFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, final String value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = Utility.latin1ByteArray(value);
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2 + 2);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        src[count++] = BYTE_DQUOTE;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_DQUOTE;
    }

    @Override
    public void writeLastFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value) {
        byte[] bs1 = fieldBytes;
        byte[] bs2 = (value >= 0 && value < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[(int) value]
                : ((value < 0 && value > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[(int) -value]
                        : Utility.latin1ByteArray(String.valueOf(value)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        byte[] src = expand(len1 + len2 + 1);
        System.arraycopy(bs1, 0, src, count, len1);
        count += len1;
        System.arraycopy(bs2, 0, src, count, len2);
        count += len2;
        src[count++] = BYTE_RBRACE;
    }

    @Override
    public void writeLastFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, final String value) {
        if (value == null && nullable()) {
            writeTo(fieldBytes);
            writeNull();
            writeTo(BYTE_RBRACE);
            return;
        }
        if (value == null || (tiny() && value.isEmpty())) {
            expand(1);
            content[count++] = BYTE_RBRACE;
        } else {
            byte[] bs1 = fieldBytes;
            byte[] bs2 = Utility.latin1ByteArray(value);
            int len1 = bs1.length;
            int len2 = bs2.length;
            byte[] src = expand(len1 + len2 + 3);
            int c = count;
            System.arraycopy(bs1, 0, src, c, len1);
            c += len1;
            src[c++] = BYTE_DQUOTE;
            System.arraycopy(bs2, 0, src, c, len2);
            c += len2;
            src[c++] = BYTE_DQUOTE;
            src[c++] = BYTE_RBRACE;
            count = c;
        }
    }

    @Override // firstFieldBytes 必须带{开头
    public void writeObjectByOnlyOneLatin1FieldValue(
            final byte[] firstFieldBytes, final char[] firstFieldChars, final String value) {
        if (value == null && nullable()) {
            writeTo(BYTE_LBRACE);
            writeTo(firstFieldBytes);
            writeNull();
            writeTo(BYTE_RBRACE);
            return;
        }
        if (value == null || (tiny() && value.isEmpty())) {
            expand(2);
            content[count++] = BYTE_LBRACE;
            content[count++] = BYTE_RBRACE;
        } else {
            byte[] bs1 = firstFieldBytes;
            byte[] bs2 = Utility.latin1ByteArray(value);
            int len1 = bs1.length;
            int len2 = bs2.length;
            byte[] src = expand(len1 + len2 + 3);
            int c = count;
            System.arraycopy(bs1, 0, src, c, len1);
            c += len1;
            src[c++] = BYTE_DQUOTE;
            System.arraycopy(bs2, 0, src, c, len2);
            c += len2;
            src[c++] = BYTE_DQUOTE;
            src[c++] = BYTE_RBRACE;
            count = c;
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
        byte[] bs1 = firstFieldBytes;
        byte[] bs2 = (value1 >= 0 && value1 < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value1]
                : ((value1 < 0 && value1 > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value1]
                        : Utility.latin1ByteArray(String.valueOf(value1)));
        byte[] bs3 = lastFieldBytes;
        byte[] bs4 = (value2 >= 0 && value2 < TENTHOUSAND_MAX)
                ? TENTHOUSAND_BYTES[value2]
                : ((value2 < 0 && value2 > -TENTHOUSAND_MAX)
                        ? TENTHOUSAND_BYTES2[-value2]
                        : Utility.latin1ByteArray(String.valueOf(value2)));
        int len1 = bs1.length;
        int len2 = bs2.length;
        int len3 = bs3.length;
        int len4 = bs4.length;
        byte[] src = expand(len1 + len2 + len3 + len4 + 1);
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
        if (Utility.isLatin1(value)) {
            writeEscapeLatinString(quote, Utility.latin1ByteArray(value));
            return;
        }
        byte[] utf16s = Utility.byteUTF16Array(value);
        if (utf16s != null) { // JDK9+
            writeUTF16String(quote, utf16s);
            return;
        }
        int len = value.length();
        byte[] bytes = expand(len * 4 + 2);
        int curr = count;
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
        for (int i = 0; i < len; i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\n':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'n';
                    break;
                case '\r':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'r';
                    break;
                case '\f':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'f';
                    break;
                case '\b':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 'b';
                    break;
                case '\t':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 't';
                    break;
                case '\\':
                    bytes[curr++] = '\\';
                    bytes[curr++] = '\\';
                    break;
                case BYTE_DQUOTE:
                    bytes[curr++] = '\\';
                    bytes[curr++] = BYTE_DQUOTE;
                    break;
                default:
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
                    break;
            }
        }
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
        count = curr;
    }

    // see java.lang.StringCoding.encodeUTF8_UTF16 方法
    private void writeUTF16String(final boolean quote, byte[] value) {
        int len = value.length;
        byte[] bytes = expand(len * 4 + 2);
        int curr = count;
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
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
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
        count = curr;
    }

    static final char MIN_LOW_SURROGATE = '\uDC00';
    static final char MAX_LOW_SURROGATE = '\uDFFF';
    static final char MIN_HIGH_SURROGATE = '\uD800';
    static final char MAX_HIGH_SURROGATE = '\uDBFF';
    static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
    static final char MAX_LOW_SURROGATE_MORE = MAX_LOW_SURROGATE + 1;
    static final char MAX_HIGH_SURROGATE_MORE = MAX_HIGH_SURROGATE + 1;
    static final int MIN_SUPPLEMENTARY_CODE_POINT_MORE =
            (MIN_SUPPLEMENTARY_CODE_POINT - (MIN_HIGH_SURROGATE << 10) - MIN_LOW_SURROGATE);

    private void writeEscapeLatinString(final boolean quote, byte[] value) {
        byte[] bytes = expand(value.length * 2 + 2);
        int curr = count;
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
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
        if (quote) {
            bytes[curr++] = BYTE_DQUOTE;
        }
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
        byte[] utf16s = Utility.byteUTF16Array(value);
        if (utf16s != null) { // JDK9+
            writeUTF16String(false, utf16s);
            return;
        }
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
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) {
            value = -value;
        }
        int size;
        for (int i = 0; ; i++) {
            if (value <= sizeTable[i]) {
                size = i + 1;
                break;
            }
        }
        if (sign != 0) {
            size++; // 负数
        }
        byte[] bytes = expand(size);

        int q, r;
        int charPos = count + size;

        // Generate two digits per iteration
        while (value >= 65536) {
            q = value / 100;
            // really: r = i - (q * 100);
            r = value - ((q << 6) + (q << 5) + (q << 2));
            value = q;
            bytes[--charPos] = (byte) DigitOnes[r];
            bytes[--charPos] = (byte) DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (; ; ) {
            q = (value * 52429) >>> (16 + 3);
            r = value - ((q << 3) + (q << 1)); // r = i-(q*10) ...
            bytes[--charPos] = (byte) digits[r];
            value = q;
            if (value == 0) {
                break;
            }
        }
        if (sign != 0) {
            bytes[--charPos] = (byte) sign;
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
            size++; // 负数
        }
        byte[] bytes = expand(size);

        long q;
        int r;
        int charPos = count + size;

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (value > Integer.MAX_VALUE) {
            q = value / 100;
            // really: r = i - (q * 100);
            r = (int) (value - ((q << 6) + (q << 5) + (q << 2)));
            value = q;
            content[--charPos] = (byte) DigitOnes[r];
            content[--charPos] = (byte) DigitTens[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) value;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            bytes[--charPos] = (byte) DigitOnes[r];
            bytes[--charPos] = (byte) DigitTens[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        for (; ; ) {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1)); // r = i2-(q2*10) ...
            bytes[--charPos] = (byte) digits[r];
            i2 = q2;
            if (i2 == 0) {
                break;
            }
        }
        if (sign != 0) {
            bytes[--charPos] = (byte) sign;
        }
        count += size;
    }
}
