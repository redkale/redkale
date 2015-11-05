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

    private long nameid;

    private long serviceid;

    private DLong actionid;

    private int bodylength;

    private int bodyoffset;

    private int framelength;

    private boolean ping;

    private byte[] body;

    private byte[] bufferbytes = new byte[6];

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
        this.bodylength = buffer.getInt();
        this.bodyoffset = buffer.getInt();
        this.framelength = buffer.getInt();

        if (buffer.getInt() != 0) {
            context.getLogger().finest("sncp buffer header.retcode not 0");
            return -1;
        }
        //---------------------body----------------------------------
        if (this.channel.isTCP()) { // TCP模式， 不管数据包大小 只传一帧数据
            this.body = new byte[this.bodylength];
            int len = Math.min(this.bodylength, buffer.remaining());
            buffer.get(body, 0, len);
            this.bodyoffset = len;
            return bodylength - len;
        }
        //--------------------- UDP 模式 ----------------------------
        if (this.bodylength == this.framelength) {  //只有一帧的数据
            if (this.framelength > buffer.remaining()) { //缺失一部分数据 
                throw new RuntimeException(SncpRequest.class.getSimpleName() + " data need " + this.framelength + " bytes, but only " + buffer.remaining() + " bytes");
            }
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
    protected int readBody(ByteBuffer buffer) { // 只有 TCP 模式会调用此方法
        final int framelen = buffer.remaining();
        buffer.get(this.body, this.bodyoffset, framelen);
        this.bodyoffset += framelen;
        return framelen;
    }

    @Override
    protected void prepare() {
        this.keepAlive = this.channel.isTCP();
    }

    protected boolean isTCP() {
        return this.channel.isTCP();
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "{seqid=" + this.seqid
                + ",serviceid=" + this.serviceid + ",actionid=" + this.actionid
                + ",bodylength=" + this.bodylength + ",bodyoffset=" + this.bodyoffset
                + ",framelength=" + this.framelength + ",remoteAddress=" + getRemoteAddress() + "}";
    }

    @Override
    protected void recycle() {
        this.seqid = 0;
        this.framelength = 0;
        this.serviceid = 0;
        this.actionid = null;
        this.bodylength = 0;
        this.bodyoffset = 0;
        this.body = null;
        this.ping = false;
        this.bufferbytes[0] = 0;
        super.recycle();
    }

    protected boolean isPing() {
        return ping;
    }

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
        if (bufferbytes[0] == 0) return null;
        return new InetSocketAddress((0xff & bufferbytes[0]) + "." + (0xff & bufferbytes[1]) + "." + (0xff & bufferbytes[2]) + "." + (0xff & bufferbytes[3]),
                ((0xff00 & (bufferbytes[4] << 8)) | (0xff & bufferbytes[5])));
    }

}
