/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.logging.*;
import org.redkale.convert.bson.BsonConvert;
import org.redkale.net.Request;
import static org.redkale.net.sncp.SncpHeader.HEADER_SIZE;
import org.redkale.util.Uint128;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpRequest extends Request<SncpContext> {

    public static final byte[] DEFAULT_HEADER = new byte[HEADER_SIZE];

    protected static final int READ_STATE_ROUTE = 1;

    protected static final int READ_STATE_HEADER = 2;

    protected static final int READ_STATE_BODY = 3;

    protected static final int READ_STATE_END = 4;

    protected final BsonConvert convert;

    protected int readState = READ_STATE_ROUTE;

    private SncpHeader header;

    private int bodyOffset;

    private boolean ping;

    private byte[] body;

    protected SncpRequest(SncpContext context) {
        super(context);
        this.convert = context.getBsonConvert();
    }

    @Override  //request.header与response.header数据格式保持一致
    protected int readHeader(ByteBuffer buffer, Request last) {
        //---------------------head----------------------------------
        if (this.readState == READ_STATE_ROUTE) {
            if (buffer.remaining() < HEADER_SIZE) {
                return HEADER_SIZE - buffer.remaining(); //小于60
            }
            this.header = new SncpHeader();
            int headerSize = this.header.read(buffer);
            if (headerSize != HEADER_SIZE) {
                context.getLogger().log(Level.WARNING, "sncp buffer header.length not " + HEADER_SIZE);
                return -1;
            }
            if (this.header.getRetcode() != 0) { // retcode
                context.getLogger().log(Level.WARNING, "sncp buffer header.retcode not 0");
                return -1;
            }
            this.body = new byte[this.header.getBodyLength()];
            this.readState = READ_STATE_BODY;
        }
        //---------------------body----------------------------------
        if (this.readState == READ_STATE_BODY) {
            int bodyLength = this.header.getBodyLength();
            if (bodyLength == 0) {
                this.readState = READ_STATE_END;
                if (this.header.getSeqid() == 0 && this.header.getServiceid() == Uint128.ZERO && this.header.getActionid() == Uint128.ZERO) {
                    this.ping = true;
                }
                return 0;
            }
            int len = Math.min(bodyLength, buffer.remaining());
            buffer.get(body, 0, len);
            this.bodyOffset = len;
            int rs = bodyLength - len;
            if (rs == 0) {
                this.readState = READ_STATE_END;
            }
            return rs;
        }
        return 0;
    }

    @Override
    protected Serializable getRequestid() {
        return header.getSeqid();
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
        return SncpRequest.class.getSimpleName() + "{header=" + this.header + ",bodyOffset=" + this.bodyOffset + ",body=[" + (this.body == null ? -1 : this.body.length) + "]}";
    }

    @Override
    protected void recycle() {
        this.readState = READ_STATE_ROUTE;
        this.header = null;
        this.bodyOffset = 0;
        this.body = null;
        this.ping = false;
        super.recycle();
    }

    protected boolean isPing() {
        return ping;
    }

    public byte[] getBody() {
        return body;
    }

    public SncpHeader getHeader() {
        return header;
    }

}
