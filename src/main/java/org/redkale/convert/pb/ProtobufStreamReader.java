/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.io.*;
import org.redkale.convert.*;
import org.redkale.util.ByteArray;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class ProtobufStreamReader extends ProtobufByteBufferReader {

    private InputStream in;

    private Byte backByte;

    protected ProtobufStreamReader(InputStream in) {
        super();
        this.in = in instanceof BufferedInputStream ? in : new BufferedInputStream(in);
    }

    @Override
    protected boolean recycle() {
        super.recycle(); // this.position 初始化值为-1
        this.in = null;
        this.backByte = null;
        return false;
    }

    @Override
    protected byte nextByte() {
        if (backByte != null) {
            byte b = backByte;
            backByte = null;
            this.position++;
            return b;
        }
        try {
            int v = in.read();
            if (v == -1) {
                throw new ConvertException("eof");
            }
            this.position++;
            return (byte) v;
        } catch (IOException e) {
            throw new ConvertException(e);
        }
    }

    @Override
    protected byte[] nextBytes(int size) {
        byte[] bs = new byte[size];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = nextByte();
        }
        return bs;
    }

    @Override
    public byte[] remainBytes() {
        ByteArray array = new ByteArray();
        try {
            int v;
            while ((v = in.read()) != -1) {
                this.position++;
                array.putByte(v);
            }
        } catch (IOException e) {
            throw new ConvertException(e);
        }
        return array.getBytes();
    }

    @Override
    public boolean hasNext() {
        if (backByte != null) {
            return true;
        }
        if (this.limit > 0 && (this.position + 1) >= this.limit) {
            return false;
        }
        try {
            int v = in.read();
            if (v == -1) {
                return false;
            }
            backByte = (byte) v;
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
