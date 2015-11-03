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

    public static final int HEADER_SIZE = 64;

    protected final BsonConvert convert;

    private long seqid;

    private int framecount;

    private int frameindex;

    private int framelength;

    private long nameid;

    private long serviceid;

    private DLong actionid;

    private int bodylength;

    private int bodyoffset;

    //private byte[][] paramBytes;
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
        int port = buffer.getChar();
        if (bufferbytes[0] > 0 && port > 0) {
            this.remoteAddress = new InetSocketAddress((0xff & bufferbytes[0]) + "." + (0xff & bufferbytes[1]) + "." + (0xff & bufferbytes[2]) + "." + (0xff & bufferbytes[3]), port);
        }
        this.bodylength = buffer.getInt();
        this.framecount = buffer.get();
        this.frameindex = buffer.get();
        if (buffer.getInt() != 0) {
            context.getLogger().finest("sncp buffer header.retcode not 0");
            return -1;
        }
        this.bodyoffset = buffer.getInt();
        this.framelength = buffer.getChar();
        //---------------------body----------------------------------
        if (this.framecount == 1) {  //只有一帧的数据
            this.body = new byte[this.framelength];
            buffer.get(body);
            return 0;
        }
        //多帧数据
        final SncpContext scontext = (SncpContext) this.context;
        RequestEntry entry = scontext.getRequestEntity(this.seqid);
        if (entry == null) entry = scontext.addRequestEntity(this.seqid, new byte[this.bodylength]);
        entry.add(buffer, this.bodyoffset);

        if (entry.isCompleted()) {  //数据读取完毕
            this.body = entry.body;
            scontext.removeRequestEntity(this.seqid);
            return 0;
        } else {
            scontext.expireRequestEntry(10 * 1000); //10秒过期
        }
        if (this.channel.isTCP()) return this.bodylength - this.framelength;
        return Integer.MIN_VALUE; //多帧数据返回 Integer.MIN_VALUE
    }

    @Override
    protected int readBody(ByteBuffer buffer) { // TCP 模式会调用此方法
        long rseqid = buffer.getLong();
        if (rseqid != this.seqid) throw new RuntimeException("sncp frame receive seqid = " + seqid + ", but first receive seqid =" + rseqid);
        if (buffer.getChar() != HEADER_SIZE) throw new RuntimeException("sncp buffer receive header.length not " + HEADER_SIZE);
        long rserviceid = buffer.getLong();
        if (rserviceid != this.serviceid) throw new RuntimeException("sncp frame receive serviceid = " + serviceid + ", but first receive serviceid =" + rserviceid);
        long rnameid = buffer.getLong();
        if (rnameid != this.nameid) throw new RuntimeException("sncp frame receive nameid = " + nameid + ", but first receive nameid =" + rnameid);
        long ractionid1 = buffer.getLong();
        long ractionid2 = buffer.getLong();
        if (!this.actionid.compare(ractionid1, ractionid2)) throw new RuntimeException("sncp frame receive actionid = " + actionid + ", but first receive actionid =(" + ractionid1 + "_" + ractionid2 + ")");
        buffer.getInt();  //地址
        buffer.getChar();  //端口
        final int bodylen = buffer.getInt();
        if (bodylen != this.bodylength) throw new RuntimeException("sncp frame receive bodylength = " + bodylen + ", but first bodylength =" + bodylength);
        final int frameCount = buffer.get();
        if (frameCount != this.framecount) throw new RuntimeException("sncp frame receive nameid = " + nameid + ", but first frame.count =" + frameCount);
        final int frameIndex = buffer.get();
        if (frameIndex < 0 || frameIndex >= frameCount) throw new RuntimeException("sncp frame receive nameid = " + nameid + ", but first frame.count =" + frameCount + " & frame.index =" + frameIndex);
        final int retcode = buffer.getInt();
        if (retcode != 0) throw new RuntimeException("sncp frame receive retcode error (retcode=" + retcode + ")");
        final int bodyOffset = buffer.getInt();
        final int framelen = buffer.getChar();
        final SncpContext scontext = (SncpContext) this.context;
        RequestEntry entry = scontext.getRequestEntity(this.seqid);
        if (entry == null) entry = scontext.addRequestEntity(this.seqid, new byte[this.bodylength]);
        entry.add(buffer, bodyOffset);
        if (entry.isCompleted()) {  //数据读取完毕
            this.body = entry.body;
            scontext.removeRequestEntity(this.seqid);
        }
        return framelen;
    }

    @Override
    protected void prepare() {
        this.keepAlive = this.channel.isTCP();
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "{seqid=" + this.seqid
                + ",serviceid=" + this.serviceid + ",actionid=" + this.actionid
                + ",framecount=" + this.framecount + ",frameindex=" + this.frameindex + ",framelength=" + this.framelength
                + ",bodylength=" + this.bodylength + ",bodyoffset=" + this.bodyoffset + ",remoteAddress=" + remoteAddress + "}";
    }

    @Override
    protected void recycle() {
        this.seqid = 0;
        this.framecount = 0;
        this.frameindex = 0;
        this.framelength = 0;
        this.serviceid = 0;
        this.actionid = null;
        this.bodylength = 0;
        this.bodyoffset = 0;
        this.body = null;
        //this.paramBytes = null;
        this.ping = false;
        this.remoteAddress = null;
        this.bufferbytes[0] = 0;
        super.recycle();
    }

    protected boolean isPing() {
        return ping;
    }

//    public byte[][] getParamBytes() {
//        return paramBytes;
//    }
    public byte[] getBody() {
        return body;
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
