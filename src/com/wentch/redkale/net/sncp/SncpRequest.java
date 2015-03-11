/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.BsonConvert;
import com.wentch.redkale.convert.bson.BsonFactory;
import com.wentch.redkale.net.Request;
import com.wentch.redkale.net.Context;
import com.wentch.redkale.util.TwoLong;
import java.nio.ByteBuffer;

/**
 *
 * @author zhangjx
 */
public final class SncpRequest extends Request {

    public static final int HEADER_SIZE = 49;

    protected final BsonConvert convert;

    private long seqid;

    private int frame;

    private long nameid;

    private long serviceid;

    private TwoLong actionid;

    private int bodylength;

    private byte[][] paramBytes;

    private boolean ping;

    protected SncpRequest(Context context, BsonFactory factory) {
        super(context);
        this.convert = factory.getConvert();
    }

    @Override
    protected int readHeader(ByteBuffer buffer) {
        if (buffer.remaining() < 8) {
            this.ping = true;
            return 0;
        }
        //---------------------head----------------------------------
        this.seqid = buffer.getLong();
        if (buffer.getChar() != HEADER_SIZE) {
            context.getLogger().finest("sncp buffer header.length not " + HEADER_SIZE);
            return -1;
        }
        this.serviceid = buffer.getLong();
        this.nameid = buffer.getLong();
        this.actionid = new TwoLong(buffer.getLong(), buffer.getLong());
        this.frame = buffer.get();
        if (buffer.getInt() != 0) {
            context.getLogger().finest("sncp buffer header.retcode not 0");
            return -1;
        }
        this.bodylength = buffer.getChar();
        //---------------------body----------------------------------
        int paramlen = buffer.getChar();
        byte[][] bbytes = new byte[paramlen + 1][]; //占位第0个byte[]
        for (int i = 1; i <= paramlen; i++) {
            byte[] bytes = new byte[(int) buffer.getChar()];
            buffer.get(bytes);
            bbytes[i] = bytes;
        }
        this.paramBytes = bbytes;
        return 0;
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "{seqid=" + this.seqid
                + ",serviceid=" + this.serviceid + ",actionid=" + this.actionid
                + ",frame=" + this.frame + ",bodylength=" + this.bodylength + "}";
    }

    protected void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    protected boolean isKeepAlive() {
        return this.keepAlive;
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
    }

    @Override
    protected void recycle() {
        this.seqid = 0;
        this.frame = 0;
        this.serviceid = 0;
        this.actionid = null;
        this.bodylength = 0;
        this.paramBytes = null;
        this.ping = false;
        super.recycle();
    }

    protected boolean isPing() {
        return ping;
    }

    public byte[][] getParamBytes() {
        return paramBytes;
    }

    public long getSeqid() {
        return seqid;
    }

    public long getServiceid() {
        return serviceid;
    }

    public long getNameid() {
        return nameid;
    }

    public TwoLong getActionid() {
        return actionid;
    }

}
