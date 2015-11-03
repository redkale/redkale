/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.bson;

import com.wentch.redkale.convert.*;
import com.wentch.redkale.convert.ext.*;
import com.wentch.redkale.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public final class BsonReader implements Reader {

    public static final short SIGN_OBJECTB = (short) 0xBB;

    public static final short SIGN_OBJECTE = (short) 0xEE;

    public static final byte SIGN_HASNEXT = 1;

    public static final byte SIGN_NONEXT = 0;

    public static final byte VERBOSE_NO = 1;

    public static final byte VERBOSE_YES = 2;

    private int position = -1;

    private byte typeval;  //字段的类型值  对应  BsonWriter.writeField

    private byte[] content;

    public BsonReader() {
    }

    public static ObjectPool<BsonReader> createPool(int max) {
        return new ObjectPool<>(max, (Object... params) -> new BsonReader(), null, (x) -> x.recycle());
    }

    public BsonReader(byte[] bytes) {
        setBytes(bytes, 0, bytes.length);
    }

    public BsonReader(byte[] bytes, int start, int len) {
        setBytes(bytes, start, len);
    }

    public final void setBytes(byte[] bytes) {
        setBytes(bytes, 0, bytes.length);
    }

    public final void setBytes(byte[] bytes, int start, int len) {
        if (bytes == null) {
            this.position = 0;
        } else {
            this.content = bytes;
            this.position = start - 1;
            //this.limit = start + len - 1;
        }
    }

    protected boolean recycle() {
        this.position = -1;
        this.typeval = 0;
        //this.limit = -1;
        this.content = null;
        return true;
    }

    public void close() {
        this.recycle();
    }

    /**
     * 跳过属性的值
     */
    @Override
    public final void skipValue() {
        if (typeval == 0) return;
        final byte val = this.typeval;
        this.typeval = 0;
        switch (val) {
            case 1: readBoolean();
                break;
            case 2: readByte();
                break;
            case 3: readShort();
                break;
            case 4: readChar();
                break;
            case 5: readInt();
                break;
            case 6: readLong();
                break;
            case 7: readFloat();
                break;
            case 8: readDouble();
                break;
            case 9: readString();
                break;
            case 101:
                BoolArraySimpledCoder.instance.convertFrom(this);
                break;
            case 102:
                ByteArraySimpledCoder.instance.convertFrom(this);
                break;
            case 103:
                ShortArraySimpledCoder.instance.convertFrom(this);
                break;
            case 104:
                CharArraySimpledCoder.instance.convertFrom(this);
                break;
            case 105:
                IntArraySimpledCoder.instance.convertFrom(this);
                break;
            case 106:
                LongArraySimpledCoder.instance.convertFrom(this);
                break;
            case 107:
                FloatArraySimpledCoder.instance.convertFrom(this);
                break;
            case 108:
                DoubleArraySimpledCoder.instance.convertFrom(this);
                break;
            case 109:
                StringArraySimpledCoder.instance.convertFrom(this);
                break;
            case 127:
                BsonFactory.objectDecoder.convertFrom(this);
                break;
        }
    }

    /**
     * 判断下一个非空白字节是否为{
     *
     */
    @Override
    public int readObjectB() {
        short bt = readShort();
        if (bt == Reader.SIGN_NULL) return bt;
        if (bt != SIGN_OBJECTB) {
            throw new ConvertException("a bson object must begin with " + (SIGN_OBJECTB)
                    + " (position = " + position + ") but '" + this.content[this.position] + "'");
        }
        return bt;
    }

    @Override
    public void readObjectE() {
        if (readShort() != SIGN_OBJECTE) {
            throw new ConvertException("a bson object must end with " + (SIGN_OBJECTE)
                    + " (position = " + position + ") but '" + this.content[this.position] + "'");
        }
    }

    @Override
    public int readMapB() {
        return readArrayB();
    }

    @Override
    public void readMapE() {
    }

    /**
     * 判断下一个非空白字节是否为[
     *
     * @return
     */
    @Override
    public int readArrayB() {
        return readInt();
    }

    @Override
    public void readArrayE() {
    }

    /**
     * 判断下一个非空白字节是否:
     */
    @Override
    public void skipBlank() {
    }

    /**
     * 判断对象是否存在下一个属性或者数组是否存在下一个元素
     *
     * @return
     */
    @Override
    public boolean hasNext() {
        byte b = readByte();
        if (b == SIGN_HASNEXT) return true;
        if (b != SIGN_NONEXT) throw new ConvertException("hasNext option must be (" + (SIGN_HASNEXT)
                    + " or " + (SIGN_NONEXT) + ") but '" + b + "' at position(" + this.position + ")");
        return false;
    }

    @Override
    public DeMember readField(final AtomicInteger index, final DeMember[] members) {
        final String exceptedfield = readSmallString();
        this.typeval = readByte();
        final int len = members.length;
        int v = index.get();
        if (v >= len) {
            v = 0;
            index.set(0);
        }
        for (int k = v; k < len; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                index.set(k);
                return members[k];
            }
        }
        for (int k = 0; k < v; k++) {
            if (exceptedfield.equals(members[k].getAttribute().field())) {
                index.set(k);
                return members[k];
            }
        }
        return null;
    }

    //------------------------------------------------------------
    @Override
    public boolean readBoolean() {
        return content[++this.position] == 1;
    }

    @Override
    public byte readByte() {
        return content[++this.position];
    }

    @Override
    public char readChar() {
        return (char) ((0xff00 & (content[++this.position] << 8)) | (0xff & content[++this.position]));
    }

    @Override
    public short readShort() {
        return (short) ((0xff00 & (content[++this.position] << 8)) | (0xff & content[++this.position]));
    }

    @Override
    public int readInt() {
        return ((content[++this.position] & 0xff) << 24) | ((content[++this.position] & 0xff) << 16)
                | ((content[++this.position] & 0xff) << 8) | (content[++this.position] & 0xff);
    }

    @Override
    public long readLong() {
        return ((((long) content[++this.position] & 0xff) << 56)
                | (((long) content[++this.position] & 0xff) << 48)
                | (((long) content[++this.position] & 0xff) << 40)
                | (((long) content[++this.position] & 0xff) << 32)
                | (((long) content[++this.position] & 0xff) << 24)
                | (((long) content[++this.position] & 0xff) << 16)
                | (((long) content[++this.position] & 0xff) << 8)
                | (((long) content[++this.position] & 0xff)));
    }

    @Override
    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    @Override
    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    @Override
    public String readClassName() {
        return readSmallString();
    }

    @Override
    public String readSmallString() {
        int len = 0xff & readByte();
        if (len == 0) return "";
        String value = new String(content, ++this.position, len);
        this.position += len - 1;
        return value;
    }

    @Override
    public String readString() {
        int len = readInt();
        if (len == SIGN_NULL) return null;
        if (len == 0) return "";
        String value = new String(Utility.decodeUTF8(content, ++this.position, len));
        this.position += len - 1;
        return value;
    }

}
