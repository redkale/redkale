/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.proto;

import java.io.*;
import org.redkale.convert.*;

/**
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
class ProtobufStreamReader extends ProtobufByteBufferReader {

    private InputStream in;

    private byte currByte;

    protected ProtobufStreamReader(InputStream in) {
        super();
        this.in = in;
    }

    @Override
    protected boolean recycle() {
        super.recycle();   // this.position 初始化值为-1
        this.in = null;
        this.currByte = 0;
        return false;
    }

    @Override
    public byte nextByte() {
        try {
            byte b = (currByte = (byte) in.read());
            this.position++;
            return b;
        } catch (IOException e) {
            throw new ConvertException(e);
        }
    }

    @Override
    protected byte currentByte() {
        return currByte;
    }

}
