/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 *
 * writeTo系列的方法输出的字符不能含特殊字符
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class JsonWriter extends Writer {

    protected static final int defaultSize = Integer.getInteger("convert.json.writer.buffer.defsize", Integer.getInteger("convert.writer.buffer.defsize", 1024));

    protected boolean tiny;

    @Override
    public boolean tiny() {
        return tiny;
    }

    public JsonWriter tiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    public boolean isExtFuncEmpty() {
        return this.objExtFunc == null && this.objFieldFunc == null;
    }

    //-----------------------------------------------------------------------
    public abstract void writeTo(final char ch); //只能是 0 - 127 的字符

    public abstract void writeTo(final char[] chs, final int start, final int len); //只能是 0 - 127 的字符

    public abstract void writeTo(final byte ch); //只能是 0 - 127 的字符

    public abstract void writeTo(final byte[] chs, final int start, final int len); //只能是 0 - 127 的字符

    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger转换的String
     *
     * @param quote 是否加双引号
     * @param value 非null且不含需要转义的字符的String值
     */
    public abstract void writeLatin1To(final boolean quote, final String value);

    @Override
    public abstract void writeBoolean(boolean value);

    @Override
    public abstract void writeInt(int value);

    @Override
    public abstract void writeLong(long value);

    @Override
    public abstract void writeString(String value);

    @Override //只容许JsonBytesWriter重写此方法
    public void writeFieldName(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        if (this.comma) writeTo(',');
        if (member != null) {
            writeTo(member.getJsonFieldNameChars());
        } else {
            writeLatin1To(true, fieldName);
            writeTo(':');
        }
    }

    @Override
    public final void writeSmallString(String value) {
        writeLatin1To(true, value);
    }

    //----------------------------------------------------------------------------------------------
    public final void writeTo(final char... chs) { //只能是 0 - 127 的字符
        writeTo(chs, 0, chs.length);
    }

    @Override
    public final void writeByte(byte value) {
        writeInt(value);
    }

    public final void writeTo(final byte[] chs) { //只能是 0 - 127 的字符
        writeTo(chs, 0, chs.length);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        if (values == null) {
            writeNull();
            return;
        }
        writeArrayB(values.length, null, null, values);
        boolean flag = false;
        for (byte v : values) {
            if (flag) writeArrayMark();
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
    public final void writeWrapper(StringWrapper value) {
        writeLatin1To(false, String.valueOf(value));
    }

    @Override
    public final boolean needWriteClassName() {
        return false;
    }

    @Override
    public final void writeClassName(String clazz) {
    }

    @Override
    public final int writeObjectB(Object obj) {
        super.writeObjectB(obj);
        writeTo('{');
        return -1;
    }

    @Override
    public final void writeObjectE(Object obj) {
        writeTo('}');
    }

    @Override
    public final void writeNull() {
        writeTo('n', 'u', 'l', 'l');
    }

    @Override
    public final int writeArrayB(int size, Encodeable arrayEncoder, Encodeable<Writer, Object> componentEncoder, Object obj) {
        writeTo('[');
        return -1;
    }

    @Override
    public final void writeArrayMark() {
        writeTo(',');
    }

    @Override
    public final void writeArrayE() {
        writeTo(']');
    }

    @Override
    public final int writeMapB(int size, Encodeable<Writer, Object> keyEncoder, Encodeable<Writer, Object> valueEncoder, Object obj) {
        writeTo('{');
        return -1;
    }

    @Override
    public final void writeMapMark() {
        writeTo(':');
    }

    @Override
    public final void writeMapE() {
        writeTo('}');
    }

    final static char[] DigitTens = {
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

    final static char[] DigitOnes = {
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

    final static char[] digits = {
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
    };

    final static int[] sizeTable = {9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE};
}
