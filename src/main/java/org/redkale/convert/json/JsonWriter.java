/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.*;

/**
 * writeTo系列的方法输出的字符不能含特殊字符
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class JsonWriter extends Writer {

    protected static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.json.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    private static final char[] CHARS_NULL = new char[] {'n', 'u', 'l', 'l'};

    protected static final byte BYTE_COMMA = ',';

    protected static final byte BYTE_COLON = ':';

    protected static final byte BYTE_LBRACE = '{';

    protected static final byte BYTE_RBRACE = '}';

    protected static final byte BYTE_LBRACKET = '[';

    protected static final byte BYTE_RBRACKET = ']';

    protected static final byte BYTE_DQUOTE = '"';

    protected JsonWriter() {
        this.features = JsonFactory.root().getFeatures();
    }

    @Override
    public JsonWriter withFeatures(int features) {
        return (JsonWriter) super.withFeatures(features);
    }

    @ClassDepends
    public boolean isExtFuncEmpty() {
        return this.objExtFunc == null && this.objFieldFunc == null;
    }

    public boolean charsMode() {
        return false;
    }

    // -----------------------------------------------------------------------
    public abstract void writeTo(final char ch); // 只能是 0 - 127 的字符

    public abstract void writeTo(final char[] chs, final int start, final int len); // 只能是 0 - 127 的字符

    public abstract void writeTo(final byte ch); // 只能是 0 - 127 的字符

    public abstract void writeTo(final byte[] chs, final int start, final int len); // 只能是 0 - 127 的字符

    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger、BigDecimal转换的String
     *
     * @param quote 是否加双引号
     * @param value 非null且不含需要转义的字符的String值
     */
    @ClassDepends
    public abstract void writeLatin1To(final boolean quote, final String value);

    @ClassDepends
    public void writeField(final byte[] fieldBytes, final char[] fieldChars) {
        if (charsMode()) {
            writeTo(fieldChars, 0, fieldChars.length);
        } else {
            writeTo(fieldBytes, 0, fieldBytes.length);
        }
    }

    @ClassDepends
    public abstract void writeFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value);

    @ClassDepends
    public abstract void writeFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value);

    @ClassDepends
    public abstract void writeFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value);

    @ClassDepends
    public abstract void writeFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, final String value);

    @ClassDepends
    public abstract void writeLastFieldShortValue(final byte[] fieldBytes, final char[] fieldChars, final short value);

    @ClassDepends
    public abstract void writeLastFieldIntValue(final byte[] fieldBytes, final char[] fieldChars, final int value);

    @ClassDepends
    public abstract void writeLastFieldLongValue(final byte[] fieldBytes, final char[] fieldChars, final long value);

    @ClassDepends
    public abstract void writeLastFieldLatin1Value(final byte[] fieldBytes, final char[] fieldChars, String value);

    // firstFieldBytes 必须带{开头
    @ClassDepends
    public abstract void writeObjectByOnlyOneLatin1FieldValue(
            final byte[] firstFieldBytes, final char[] firstFieldChars, final String value);

    // firstFieldBytes 必须带{开头, lastFieldBytes必须,开头
    @ClassDepends
    public abstract void writeObjectByOnlyTwoIntFieldValue(
            final byte[] firstFieldBytes,
            final char[] firstFieldChars,
            final int value1,
            final byte[] lastFieldBytes,
            final char[] lastFieldChars,
            final int value2);

    @Override
    @ClassDepends
    public abstract void writeBoolean(boolean value);

    @Override
    @ClassDepends
    public abstract void writeInt(int value);

    @Override
    @ClassDepends
    public abstract void writeLong(long value);

    public abstract void writeString(final boolean quote, String value);

    @Override
    @ClassDepends
    public abstract void writeString(String value);

    @Override // 只容许JsonBytesWriter重写此方法
    public void writeField(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        if (this.comma) {
            writeTo(BYTE_COMMA);
        }
        if (member != null) {
            if (charsMode()) {
                char[] chs = member.getJsonFieldNameChars();
                writeTo(chs, 0, chs.length);
            } else {
                writeTo(member.getJsonFieldNameBytes());
            }
        } else {
            writeLatin1To(true, fieldName);
            writeTo(BYTE_COLON);
        }
    }

    @Override
    public final void writeStandardString(String value) {
        writeLatin1To(true, value);
    }

    // ----------------------------------------------------------------------------------------------
    public final void writeTo(final char... chs) { // 只能是 0 - 127 的字符
        writeTo(chs, 0, chs.length);
    }

    @Override
    public final void writeByte(byte value) {
        writeInt(value);
    }

    public final void writeTo(final byte[] chs) { // 只能是 0 - 127 的字符
        writeTo(chs, 0, chs.length);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        if (values == null) {
            writeNull();
            return;
        }
        writeArrayB(values.length, null, values);
        boolean flag = false;
        for (byte v : values) {
            if (flag) {
                writeArrayMark();
            }
            writeByte(v);
            flag = true;
        }
        writeArrayE();
    }

    @Override
    public final void writeChar(char value) {
        writeInt(value);
    }

    @Override
    public final void writeShort(short value) {
        writeInt(value);
    }

    @Override
    public final void writeFloat(float value) {
        writeLatin1To(false, String.valueOf(value));
    }

    @Override
    public final void writeDouble(double value) {
        writeLatin1To(false, String.valueOf(value));
    }

    @Override
    public final boolean needWriteClassName() {
        return false;
    }

    @Override
    public final void writeClassName(String clazz) {}

    @Override
    public final void writeObjectB(Object obj) {
        super.writeObjectB(obj);
        writeTo(BYTE_LBRACE);
    }

    @Override
    public final void writeObjectE(Object obj) {
        writeTo(BYTE_RBRACE);
    }

    @Override
    public void writeNull() {
        writeTo(CHARS_NULL);
    }

    @Override
    public final void writeArrayB(int size, Encodeable componentEncoder, Object obj) {
        writeTo(BYTE_LBRACKET);
    }

    @Override
    public final void writeArrayMark() {
        writeTo(BYTE_COMMA);
    }

    @Override
    public final void writeArrayE() {
        writeTo(BYTE_RBRACKET);
    }

    @Override
    public final void writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj) {
        writeTo(BYTE_LBRACE);
    }

    @Override
    public final void writeMapMark() {
        writeTo(BYTE_COLON);
    }

    @Override
    public final void writeMapE() {
        writeTo(BYTE_RBRACE);
    }

    static final char[] DigitTens = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    };

    static final char[] DigitOnes = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    static final char[] digits = {
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
    };

    static final int[] sizeTable = {9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE};
}
