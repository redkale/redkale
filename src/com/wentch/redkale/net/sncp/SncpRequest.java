/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.convert.bson.*;
import com.wentch.redkale.net.*;
import com.wentch.redkale.net.sncp.SncpContext.RequestEntry;
import com.wentch.redkale.util.*;
import java.net.*;
import java.nio.*;

/**
 *
 * @author zhangjx
 */
public final class SncpRequest extends Request {

    public static final int HEADER_SIZE = 60;

    protected final BsonConvert convert;

    private long seqid;

    private int framecount;

    private int frameindex;

    private long nameid;

    private long serviceid;

    private DLong actionid;

    private int bodylength;

    private byte[][] paramBytes;

    private boolean ping;

    private byte[] body;

    private byte[] bufferbytes = new byte[4];

    private InetSocketAddress remoteAddress;

    protected SncpRequest(SncpContext context, BsonFactory factory) {
        super(context);
        this.convert = factory.getConvert();
    }

    @Override
    protected int readHeader(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_SIZE) {
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
        this.actionid = new DLong(buffer.getLong(), buffer.getLong());
        buffer.get(bufferbytes);
        int port = buffer.getInt();
        if (bufferbytes[0] > 0 && port > 0) {
            this.remoteAddress = new InetSocketAddress((0xff & bufferbytes[0]) + "." + (0xff & bufferbytes[1]) + "." + (0xff & bufferbytes[2]) + "." + (0xff & bufferbytes[3]), port);
        }
        this.framecount = buffer.get();
        this.frameindex = buffer.get();
        if (buffer.getInt() != 0) {
            context.getLogger().finest("sncp buffer header.retcode not 0");
            return -1;
        }
        this.bodylength = buffer.getInt();
        //---------------------body----------------------------------
        if (this.framecount == 1) {  //只有一帧的数据
            int paramlen = buffer.getChar();
            byte[][] bbytes = new byte[paramlen + 1][]; //占位第0个byte[]
            for (int i = 1; i <= paramlen; i++) {
                byte[] bytes = new byte[buffer.getInt()];
                buffer.get(bytes);
                bbytes[i] = bytes;
            }
            this.paramBytes = bbytes;
            return 0;
        }
        //多帧数据
        final SncpContext scontext = (SncpContext) this.context;
        RequestEntry entry = scontext.getRequestEntity(this.seqid);
        if (entry == null) entry = scontext.addRequestEntity(this.seqid, new byte[this.bodylength]);
        entry.add(buffer, (this.framecount - this.frameindex - 1) * (buffer.capacity() - HEADER_SIZE));

        if (entry.isCompleted()) {  //数据读取完毕
            this.body = entry.body;
            scontext.removeRequestEntity(this.seqid);
            return 0;
        } else {
            scontext.expireRequestEntry(10 * 1000); //10秒过期
        }
        return Integer.MIN_VALUE; //多帧数据返回 Integer.MIN_VALUE
    }

    @Override
    protected void readBody(ByteBuffer buffer) {
    }

    @Override
    protected void prepare() {
        if (this.body == null) return;
        byte[] bytes = this.body;
        int pos = 0;
        int paramlen = ((0xff00 & (bytes[pos++] << 8)) | (0xff & bytes[pos++]));
        byte[][] bbytes = new byte[paramlen + 1][]; //占位第0个byte[]
        for (int i = 1; i <= paramlen; i++) {
            byte[] bs = new byte[(0xff000000 & (bytes[pos++] << 24)) | (0xff0000 & (bytes[pos++] << 16))
                    | (0xff00 & (bytes[pos++] << 8)) | (0xff & bytes[pos++])];
            System.arraycopy(bytes, pos, bs, 0, bs.length);
            pos += bs.length;
            bbytes[i] = bs;
        }
        this.paramBytes = bbytes;
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "{seqid=" + this.seqid
                + ",serviceid=" + this.serviceid + ",actionid=" + this.actionid
                + ",framecount=" + this.framecount + ",frameindex=" + this.frameindex + ",bodylength=" + this.bodylength + ",remoteAddress=" + remoteAddress + "}";
    }

    @Override
    protected void recycle() {
        this.seqid = 0;
        this.framecount = 0;
        this.frameindex = 0;
        this.serviceid = 0;
        this.actionid = null;
        this.bodylength = 0;
        this.body = null;
        this.paramBytes = null;
        this.ping = false;
        this.remoteAddress = null;
        this.bufferbytes[0] = 0;
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

    public DLong getActionid() {
        return actionid;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

}
