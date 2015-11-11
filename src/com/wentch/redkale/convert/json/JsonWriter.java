/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.json;

import com.wentch.redkale.convert.*;
import com.wentch.redkale.util.*;
import java.nio.*;

/**
 *
 * writeTo系列的方法输出的字符不能含特殊字符
 *
 * @author zhangjx
 */
public class JsonWriter implements Writer {

    private static final char[] CHARS_TUREVALUE = "true".toCharArray();

    private static final char[] CHARS_FALSEVALUE = "false".toCharArray();

    private static final int defaultSize = Integer.getInteger("convert.json.writer.buffer.defsize", 1024);

    private int count;

    private char[] content;

    protected boolean tiny;

    public static ObjectPool<JsonWriter> createPool(int max) {
        return new ObjectPool<>(max, (Object... params) -> new JsonWriter(), null, (x) -> x.recycle());
    }

    public JsonWriter() {
        this(defaultSize);
    }

    public JsonWriter(int size) {
        this.content = new char[size > 128 ? size : 128];
    }

    @Override
    public boolean isTiny() {
        return tiny;
    }

    public JsonWriter setTiny(boolean tiny) {
        this.tiny = tiny;
        return this;
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    /**
     * 返回指定至少指定长度的缓冲区
     *
     * @param len
     * @return
     */
    private char[] expand(int len) {
        int newcount = count + len;
        if (newcount <= content.length) return content;
        char[] newdata = new char[Math.max(content.length * 3 / 2, newcount)];
        System.arraycopy(content, 0, newdata, 0, count);
        this.content = newdata;
        return newdata;
    }

    public void writeTo(final char ch) { //只能是 0 - 127 的字符
        expand(1);
        content[count++] = ch;
    }

    public void writeTo(final char[] chs, final int start, final int len) { //只能是 0 - 127 的字符
        expand(len);
        System.arraycopy(chs, start, content, count, len);
        count += len;
    }

    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger转换的String
     *
     * @param quote
     * @param value
     */
    public void writeTo(final boolean quote, final String value) {
        int len = value.length();
        expand(len + (quote ? 2 : 0));
        if (quote) content[count++] = '"';
        value.getChars(0, len, content, count);
        count += len;
        if (quote) content[count++] = '"';
    }

    protected boolean recycle() {
        this.count = 0;
        if (this.content.length > defaultSize) {
            this.content = new char[defaultSize];
        }
        return true;
    }

    public ByteBuffer[] toBuffers() {
        return new ByteBuffer[]{ByteBuffer.wrap(Utility.encodeUTF8(content, 0, count))};
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
        expand(value.length() * 2 + 2);
        content[count++] = '"';
        for (char ch : Utility.charArray(value)) {
            switch (ch) {
                case '\n': content[count++] = '\\';
                    content[count++] = 'n';
                    break;
                case '\r': content[count++] = '\\';
                    content[count++] = 'r';
                    break;
                case '\t': content[count++] = '\\';
                    content[count++] = 't';
                    break;
                case '\\': content[count++] = '\\';
                    content[count++] = ch;
                    break;
                case '"': content[count++] = '\\';
                    content[count++] = ch;
                    break;
                default: content[count++] = ch;
                    break;
            }
        }
        content[count++] = '"';
    }

    @Override
    public void writeField(boolean comma, Attribute attribute) {
        if (comma) writeTo(',');
        writeTo('"');
        writeSmallString(attribute.field());
        writeTo('"');
        writeTo(':');
    }

    @Override
    public void writeSmallString(String value) {
        writeTo(false, value);
    }

    @Override
    public String toString() {
        return new String(content, 0, count);
    }

    //----------------------------------------------------------------------------------------------
    public final void writeTo(final char... chs) { //只能是 0 - 127 的字符
        writeTo(chs, 0, chs.length);
    }

    @Override
    public final void writeBoolean(boolean value) {
        writeTo(value ? CHARS_TUREVALUE : CHARS_FALSEVALUE);
    }

    @Override
    public final void writeByte(byte value) {
        writeInt(value);
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
    public final void writeInt(int value) {
        writeSmallString(String.valueOf(value));
    }

    @Override
    public final void writeLong(long value) {
        writeSmallString(String.valueOf(value));
    }

    @Override
    public final void writeFloat(float value) {
        writeSmallString(String.valueOf(value));
    }

    @Override
    public final void writeDouble(double value) {
        writeSmallString(String.valueOf(value));
    }

    @Override
    public final void wirteClassName(String clazz) {
    }

    @Override
    public final void writeObjectB(int fieldCount, Object obj) {
        writeTo('{');
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
    public final void writeArrayB(int size) {
        writeTo('[');
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
    public final void writeMapB(int size) {
        writeTo('{');
    }

    @Override
    public final void writeMapMark() {
        writeTo(':');
    }

    @Override
    public final void writeMapE() {
        writeTo('}');
    }
}
