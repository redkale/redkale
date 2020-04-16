/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.util.Objects;
import org.redkale.convert.*;
import static org.redkale.convert.Reader.SIGN_NULL;
import org.redkale.convert.ext.ByteSimpledCoder;
import org.redkale.util.Utility;

/**
 * BSON数据源
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonMaskReader extends BsonReader {

    protected ConvertMask mask;

    public BsonMaskReader(ConvertMask mask) {
        Objects.requireNonNull(mask);
        this.mask = mask;
    }

    public void setMask(ConvertMask mask) {
        this.mask = mask;
    }

    @Override
    protected boolean recycle() {
        boolean rs = super.recycle();
        this.mask = null;
        return rs;
    }

    @Override
    protected byte currentByte() {
        return mask.unmask(content[this.position]);
    }

    @Override
    public int readMapB(DeMember member, byte[] typevals, Decodeable keyDecoder, Decodeable valueDecoder) {
        short bt = readShort();
        if (bt == Reader.SIGN_NULL) return bt;
        int rs = (bt & 0xffff) << 16 | ((mask.unmask(content[++this.position]) & 0xff) << 8) | (mask.unmask(content[++this.position]) & 0xff);
        byte kt = readByte();
        byte vt = readByte();
        if (typevals != null) {
            typevals[0] = kt;
            typevals[1] = vt;
        }
        return rs;
    }

    @Override
    public int readArrayB(DeMember member, byte[] typevals, Decodeable componentDecoder) { //componentDecoder可能为null
        short bt = readShort();
        if (bt == Reader.SIGN_NULL) return bt;
        int rs = (bt & 0xffff) << 16 | ((mask.unmask(content[++this.position]) & 0xff) << 8) | (mask.unmask(content[++this.position]) & 0xff);
        if (componentDecoder != null && componentDecoder != ByteSimpledCoder.instance) {
            byte comval = readByte();
            if (typevals != null) typevals[0] = comval;
        }
        return rs;
    }

    @Override
    public boolean readBoolean() {
        return mask.unmask(content[++this.position]) == 1;
    }

    @Override
    public byte readByte() {
        return mask.unmask(content[++this.position]);
    }

    @Override
    public char readChar() {
        return (char) ((0xff00 & (mask.unmask(content[++this.position]) << 8)) | (0xff & mask.unmask(content[++this.position])));
    }

    @Override
    public short readShort() {
        return (short) ((0xff00 & (mask.unmask(content[++this.position]) << 8)) | (0xff & mask.unmask(content[++this.position])));
    }

    @Override
    public int readInt() {
        return ((mask.unmask(content[++this.position]) & 0xff) << 24) | ((mask.unmask(content[++this.position]) & 0xff) << 16)
            | ((mask.unmask(content[++this.position]) & 0xff) << 8) | (mask.unmask(content[++this.position]) & 0xff);
    }

    @Override
    public long readLong() {
        return ((((long) mask.unmask(content[++this.position]) & 0xff) << 56)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 48)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 40)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 32)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 24)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 16)
            | (((long) mask.unmask(content[++this.position]) & 0xff) << 8)
            | (((long) mask.unmask(content[++this.position]) & 0xff)));
    }

    @Override
    public String readSmallString() {
        int len = 0xff & readByte();
        if (len == 0) return "";
        byte[] bs = new byte[len];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = mask.unmask(content[++this.position]);
        }
        String value = new String(bs);
        this.position += len - 1; //上一行已经++this.position，所以此处要-1
        return value;
    }

    @Override
    public String readString() {
        int len = readInt();
        if (len == SIGN_NULL) return null;
        if (len == 0) return "";
        byte[] bs = new byte[len];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = mask.unmask(content[++this.position]);
        }
        String value = new String(Utility.decodeUTF8(bs, 0, len));
        this.position += len - 1;//上一行已经++this.position，所以此处要-1
        return value;
    }
}
