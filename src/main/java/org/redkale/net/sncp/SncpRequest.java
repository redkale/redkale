/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.logging.Level;
import org.redkale.convert.bson.*;
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

    protected static final int READ_STATE_ROUTE = 1;

    protected static final int READ_STATE_HEADER = 2;

    protected static final int READ_STATE_BODY = 3;

    protected static final int READ_STATE_END = 4;

    protected final BsonReader reader = new BsonReader();

    protected int readState = READ_STATE_ROUTE;

    private SncpHeader header;

    private int bodyOffset;

    private boolean ping;

    private byte[] body;

    protected SncpRequest(SncpContext context) {
        super(context);
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
                context.getLogger().log(Level.WARNING, "sncp buffer header.length not " + HEADER_SIZE + ", but " + headerSize);
                return -1;
            }
            if (this.header.getRetcode() != 0) { // retcode
                context.getLogger().log(Level.WARNING, "sncp buffer header.retcode not 0");
                return -1;
            }
            if (this.header.getBodyLength() > context.getMaxBody()) {
                context.getLogger().log(Level.WARNING, "sncp buffer body.length must lower " + context.getMaxBody() + ", but " + this.header.getBodyLength());
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
            int len = Math.min(bodyLength - this.bodyOffset, buffer.remaining());
            buffer.get(body, this.bodyOffset, len);
            this.bodyOffset += len;
            int rs = bodyLength - this.bodyOffset;
            if (rs == 0) {
                this.readState = READ_STATE_END;
            } else {
                buffer.clear();
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

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "_" + Objects.hashCode(this) + "{header=" + this.header + ",bodyOffset=" + this.bodyOffset + ",body=[" + (this.body == null ? -1 : this.body.length) + "]}";
    }

    @Override
    protected void recycle() {
        this.reader.clear();
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

    public BsonConvert getBsonConvert() {
        return context.getBsonConvert();
    }

    public BsonReader getBsonReader() {
        return body == null ? null : reader.setBytes(body);
    }

    public byte[] getBody() {
        return body;
    }

    public SncpHeader getHeader() {
        return header;
    }

}
