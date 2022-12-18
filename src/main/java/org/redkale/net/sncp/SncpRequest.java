/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.net.*;
import java.nio.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.convert.bson.*;
import org.redkale.net.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpRequest extends Request<SncpContext> {

    public static final int HEADER_SIZE = 60;

    public static final byte[] DEFAULT_HEADER = new byte[HEADER_SIZE];

    protected static final int READ_STATE_ROUTE = 1;

    protected static final int READ_STATE_HEADER = 2;

    protected static final int READ_STATE_BODY = 3;

    protected static final int READ_STATE_END = 4;

    protected final BsonConvert convert;

    private long seqid;

    protected int readState = READ_STATE_ROUTE;

    private int serviceVersion;

    private DLong serviceid;

    private DLong actionid;

    private int bodyLength;

    private int bodyOffset;

    private boolean ping;

    private byte[] body;

    private final byte[] addrBytes = new byte[6];

    protected SncpRequest(SncpContext context) {
        super(context);
        this.convert = context.getBsonConvert();
    }

    @Override  //request.header与response.header数据格式保持一致
    protected int readHeader(ByteBuffer buffer, Request last) {
        if (buffer.remaining() == Sncp.PING_BUFFER.remaining()) {
            if (buffer.hasRemaining()) buffer.get(new byte[buffer.remaining()]);
            this.ping = true;  //Sncp.PING_BUFFER
            this.readState = READ_STATE_END;
            return 0;
        }
        //---------------------head----------------------------------
        if (this.readState == READ_STATE_ROUTE) {
            if (buffer.remaining() < HEADER_SIZE) return 1; //小于60
            this.seqid = buffer.getLong(); //8
            if (buffer.getChar() != HEADER_SIZE) { //2
                if (context.getLogger().isLoggable(Level.FINEST)) context.getLogger().finest("sncp buffer header.length not " + HEADER_SIZE);
                return -1;
            }
            this.serviceid = DLong.read(buffer); //16
            this.serviceVersion = buffer.getInt(); //4
            this.actionid = DLong.read(buffer); //16
            buffer.get(addrBytes); //ipaddr   //6
            this.bodyLength = buffer.getInt(); //4

            if (buffer.getInt() != 0) { //4
                if (context.getLogger().isLoggable(Level.FINEST)) context.getLogger().finest("sncp buffer header.retcode not 0");
                return -1;
            }
            this.body = new byte[this.bodyLength];
            this.readState = READ_STATE_BODY;
        }
        //---------------------body----------------------------------
        if (this.readState == READ_STATE_BODY) {
            int len = Math.min(this.bodyLength, buffer.remaining());
            buffer.get(body, 0, len);
            this.bodyOffset = len;
            int rs = bodyLength - len;
            if (rs == 0) this.readState = READ_STATE_END;
            return rs;
        }
        return 0;
    }

    @Override
    protected void prepare() {
        this.keepAlive = true;
    }

    //被SncpAsyncHandler.sncp_setParams调用
    protected void sncp_setParams(SncpDynServlet.SncpServletAction action, Logger logger, Object... params) {
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "{seqid=" + this.seqid
            + ",serviceVersion=" + this.serviceVersion + ",serviceid=" + this.serviceid
            + ",actionid=" + this.actionid + ",bodyLength=" + this.bodyLength
            + ",bodyOffset=" + this.bodyOffset + ",remoteAddress=" + getRemoteAddress() + "}";
    }

    @Override
    protected void recycle() {
        this.seqid = 0;
        this.readState = READ_STATE_ROUTE;
        this.serviceid = null;
        this.serviceVersion = 0;
        this.actionid = null;
        this.bodyLength = 0;
        this.bodyOffset = 0;
        this.body = null;
        this.ping = false;
        this.addrBytes[0] = 0;
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

    public int getServiceVersion() {
        return serviceVersion;
    }

    public DLong getServiceid() {
        return serviceid;
    }

    public DLong getActionid() {
        return actionid;
    }

    public InetSocketAddress getRemoteAddress() {
        if (addrBytes[0] == 0) return null;
        return new InetSocketAddress((0xff & addrBytes[0]) + "." + (0xff & addrBytes[1]) + "." + (0xff & addrBytes[2]) + "." + (0xff & addrBytes[3]),
            ((0xff00 & (addrBytes[4] << 8)) | (0xff & addrBytes[5])));
    }

}
