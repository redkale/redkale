/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.logging.Level;
import org.redkale.convert.*;
import org.redkale.convert.bson.BsonReader;
import org.redkale.net.Request;
import static org.redkale.net.client.ClientRequest.EMPTY_TRACEID;
import org.redkale.util.*;

/**
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

    private int headerSize;

    private ByteArray halfArray;

    private SncpHeader header;

    private boolean ping;

    private byte[] body;

    protected SncpRequest(SncpContext context) {
        super(context);
    }

    @Override // request.header与response.header数据格式保持一致
    protected int readHeader(ByteBuffer buffer, Request last) {
        // ---------------------route----------------------------------
        if (this.readState == READ_STATE_ROUTE) {
            int remain = buffer.remaining();
            int expect = halfArray == null ? 2 : 2 - halfArray.length();
            if (remain < expect) {
                if (remain == 1) {
                    if (halfArray == null) {
                        halfArray = new ByteArray();
                    }
                    halfArray.clear().put(buffer.get());
                }
                buffer.clear();
                return expect - remain; // 小于2
            } else {
                if (halfArray == null) {
                    this.headerSize = buffer.getChar();
                } else {
                    halfArray.put(buffer.get());
                    this.headerSize = halfArray.getChar(0);
                    halfArray.clear();
                }
            }
            if (this.headerSize < SncpHeader.HEADER_SUBSIZE) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "sncp buffer header.length must more " + SncpHeader.HEADER_SUBSIZE + ", but "
                                        + this.headerSize);
                return -1;
            }
            this.readState = READ_STATE_HEADER;
        }
        // ---------------------head----------------------------------
        if (this.readState == READ_STATE_HEADER) {
            int remain = buffer.remaining();
            int expect = halfArray == null ? this.headerSize - 2 : this.headerSize - 2 - halfArray.length();
            if (remain < expect) {
                if (halfArray == null) {
                    halfArray = new ByteArray();
                }
                halfArray.put(buffer);
                buffer.clear();
                return expect - remain;
            }
            if (halfArray == null || halfArray.length() == 0) {
                this.header = SncpHeader.read(buffer, this.headerSize);
            } else {
                halfArray.put(buffer, expect);
                this.header = SncpHeader.read(halfArray, this.headerSize);
                halfArray.clear();
            }
            if (this.header.getRetcode() != 0) { // retcode
                context.getLogger().log(Level.WARNING, "sncp buffer header.retcode not 0");
                return -1;
            }
            if (this.header.getBodyLength() > context.getMaxBody()) {
                context.getLogger()
                        .log(
                                Level.WARNING,
                                "sncp buffer body.length must lower " + context.getMaxBody() + ", but "
                                        + this.header.getBodyLength());
                return -1;
            }
            this.traceid = this.header.getTraceid();
            // completed=true时ProtocolCodec会继续读下一个request
            this.completed = true;
            this.readState = READ_STATE_BODY;
        }
        // ---------------------body----------------------------------
        if (this.readState == READ_STATE_BODY) {
            int bodyLength = this.header.getBodyLength();
            if (bodyLength == 0) {
                this.body = new byte[0];
                this.readState = READ_STATE_END;
                halfArray = null;
                if (this.header.getSeqid() == 0
                        && this.header.getServiceid() == Uint128.ZERO
                        && this.header.getActionid() == Uint128.ZERO) {
                    this.ping = true;
                }
                return 0;
            }
            int remain = buffer.remaining();
            int expect = halfArray == null ? bodyLength : bodyLength - halfArray.length();
            if (remain < expect) {
                if (halfArray == null) {
                    halfArray = new ByteArray();
                }
                halfArray.put(buffer);
                buffer.clear();
                return expect - remain;
            }
            if (halfArray == null || halfArray.length() == 0) {
                this.body = new byte[bodyLength];
                buffer.get(body);
            } else {
                halfArray.put(buffer, expect);
                this.body = halfArray.getBytes();
            }
            this.readState = READ_STATE_END;
            halfArray = null;
            return 0;
        }
        return 0;
    }

    @Override
    protected final Serializable getRequestid() {
        return header.getSeqid();
    }

    @Override
    protected void prepare() {
        this.keepAlive = true;
    }

    @Override
    public String toString() {
        return SncpRequest.class.getSimpleName() + "_" + Objects.hashCode(this) + "{header=" + this.header + ",body=["
                + (this.body == null ? -1 : this.body.length) + "]}";
    }

    @Override
    protected void recycle() {
        this.reader.clear();
        this.readState = READ_STATE_ROUTE;
        this.header = null;
        this.halfArray = null;
        this.body = null;
        this.ping = false;
        super.recycle();
    }

    protected boolean isPing() {
        return ping;
    }

    public Convert getConvert() {
        return context.getBsonConvert();
    }

    public Reader getReader() {
        return body == null ? null : reader.setBytes(body);
    }

    public byte[] traceBytes() {
        return Utility.isEmpty(traceid) ? EMPTY_TRACEID : traceid.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getBody() {
        return body;
    }

    public SncpHeader getHeader() {
        return header;
    }
}
