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
 *
 * writeTo系列的方法输出的字符不能含特殊字符
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.3.0
 */
public class JsonBytesWriter extends JsonWriter implements ByteTuple {

    private static final boolean greatejdk8 = Utility.greaterJDK8();

    private static final byte[] BYTES_TUREVALUE = "true".getBytes();

    private static final byte[] BYTES_FALSEVALUE = "false".getBytes();

    private int count;

    private byte[] content;

    public JsonBytesWriter() {
        this(defaultSize);
    }

    public JsonBytesWriter(int size) {
        this.content = new byte[size > 1024 ? size : 1024];
    }

    public JsonBytesWriter(ByteArray array) {
        this.content = array.content();
        this.count = array.length();
    }

    public JsonBytesWriter(boolean tiny, ByteArray array) {
        this.tiny = tiny;
        this.content = array.content();
        this.count = array.length();
    }

    protected byte[] expand(int len) {
        int newcount = count + len;
        if (newcount <= content.length) return content;
        byte[] newdata = new byte[Math.max(content.length * 3 / 2, newcount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return newdata;
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
    public final void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        if (this.comma) writeTo(',');
        if (member != null) {
            byte[] bs = member.getJsonFieldNameBytes();
            expand(bs.length);
            System.arraycopy(bs, 0, content, count, bs.length);
            count += bs.length;
        } else {
            writeLatin1To(true, fieldName);
            writeTo(':');
        }
    }

    @Override
    public void writeTo(final char ch) { //只能是 0 - 127 的字符
        expand(1);
        content[count++] = (byte) ch;
    }

    @Override
    public void writeTo(final char[] chs, final int start, final int len) { //只能是 0 - 127 的字符
        expand(len);
        for (int i = 0; i < len; i++) {
            content[count + i] = (byte) chs[start + i];
        }
        count += len;
    }

    @Override
    public void writeTo(final byte ch) { //只能是 0 - 127 的字符
        expand(1);
        content[count++] = ch;
    }

    @Override
    public void writeTo(final byte[] chs, final int start, final int len) { //只能是 0 - 127 的字符
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger转换的String
     *
     * @param quote 是否加双引号
     * @param value 非null且不含需要转义的字符的String值
     */
    @Override
    public void writeLatin1To(final boolean quote, final String value) {
        byte[] bs = Utility.byteArray(value);
        int len = bs.length;
        expand(len + (quote ? 2 : 0));
        if (quote) content[count++] = '"';
        System.arraycopy(bs, 0, content, count, bs.length);
        count += len;
        if (quote) content[count++] = '"';
    }

    public JsonBytesWriter clear() {
        this.count = 0;
        return this;
    }

    @Override
    public boolean recycle() {
        super.recycle();
        this.count = 0;
        this.specify = null;
        if (this.content != null && this.content.length > defaultSize * 100) {
            this.content = new byte[defaultSize];
        }
        return true;
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

    private void writeEscapeLatinString(byte[] value) {
        byte[] bytes = expand(value.length * 2 + 2);
        int curr = count;
        bytes[curr++] = '"';
        for (byte b : value) {
            if (b == '"') {
                bytes[curr++] = '\\';
                bytes[curr++] = '"';
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
        bytes[curr++] = '"';
        count = curr;
    }

    @Override
    public void writeString(String value) {
        if (value == null) {
            writeNull();
            return;
        }
        if (greatejdk8 && Utility.isLatin1(value)) {
            writeEscapeLatinString(Utility.byteArray(value));
            return;
        }
        byte[] bytes = expand(value.length() * 4 + 2);
        int curr = count;
        bytes[curr++] = '"';
        int len = value.length();
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
                case '\t':
                    bytes[curr++] = '\\';
                    bytes[curr++] = 't';
                    break;
                case '\\':
                    bytes[curr++] = '\\';
                    bytes[curr++] = '\\';
                    break;
                case '"':
                    bytes[curr++] = '\\';
                    bytes[curr++] = '"';
                    break;
                default:
                    if (ch < 0x80) {
                        bytes[curr++] = (byte) ch;
                    } else if (ch < 0x800) {
                        bytes[curr++] = (byte) (0xc0 | (ch >> 6));
                        bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
                    } else if (Character.isSurrogate(ch)) { //连取两个
                        int uc = Character.toCodePoint(ch, value.charAt(++i));
                        bytes[curr++] = (byte) (0xf0 | ((uc >> 18)));
                        bytes[curr++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                        bytes[curr++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                        bytes[curr++] = (byte) (0x80 | (uc & 0x3f));
                    } else {
                        bytes[curr++] = (byte) (0xe0 | ((ch >> 12)));
                        bytes[curr++] = (byte) (0x80 | ((ch >> 6) & 0x3f));
                        bytes[curr++] = (byte) (0x80 | (ch & 0x3f));
                    }
                    break;
            }
        }
        bytes[curr++] = '"';
        count = curr;
    }

    @Override
    public String toString() {
        return new String(content, 0, count, StandardCharsets.UTF_8);
    }
    //----------------------------------------------------------------------------------------------

    @Override
    public void writeBoolean(boolean value) {
        byte[] bs = value ? BYTES_TUREVALUE : BYTES_FALSEVALUE;
        expand(bs.length);
        System.arraycopy(bs, 0, content, count, bs.length);
        count += bs.length;
    }

    @Override
    public void writeInt(int value) {
        final char sign = value >= 0 ? 0 : '-';
        if (value < 0) value = -value;
        int size;
        for (int i = 0;; i++) {
            if (value <= sizeTable[i]) {
                size = i + 1;
                break;
            }
        }
        if (sign != 0) size++; //负数
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
        for (;;) {
            q = (value * 52429) >>> (16 + 3);
            r = value - ((q << 3) + (q << 1));  // r = i-(q*10) ...
            bytes[--charPos] = (byte) digits[r];
            value = q;
            if (value == 0) break;
        }
        if (sign != 0) bytes[--charPos] = (byte) sign;
        count += size;
    }

    @Override
    public void writeLong(long value) {
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
        if (sign != 0) size++; //负数
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
        for (;;) {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            bytes[--charPos] = (byte) digits[r];
            i2 = q2;
            if (i2 == 0) break;
        }
        if (sign != 0) bytes[--charPos] = (byte) sign;
        count += size;
    }

}
