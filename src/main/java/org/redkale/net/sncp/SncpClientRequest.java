/*
 *
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.util.Objects;
import org.redkale.net.client.*;
import org.redkale.util.ByteArray;

/**
 * client版请求
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class SncpClientRequest extends ClientRequest {

    private SncpHeader header;

    private long seqid;

    private byte[] bodyContent;

    public SncpClientRequest() {
        // do nothing
    }

    public SncpClientRequest prepare(SncpHeader header, long seqid, String traceid, byte[] bodyContent) {
        super.prepare();
        this.header = header;
        this.seqid = seqid;
        this.traceid = traceid;
        this.bodyContent = bodyContent;
        return this;
    }

    @Override
    protected void prepare() {
        // do nothing
    }

    @Override
    protected boolean recycle() {
        boolean rs = super.recycle();
        this.header = null;
        this.seqid = 0;
        this.traceid = null;
        this.bodyContent = null;
        return rs;
    }

    @Override
    public Serializable getRequestid() {
        return seqid;
    }

    @Override
    public void writeTo(ClientConnection conn, ByteArray array) {
        array.putPlaceholder(SncpHeader.calcHeaderSize(this));
        if (bodyContent == null) {
            header.writeTo(array, this, SncpHeader.KEEPALIVE_ON, 0, 0);
        } else {
            header.writeTo(array, this, SncpHeader.KEEPALIVE_ON, bodyContent.length, 0);
            array.put(bodyContent);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hashCode(this) + "{"
                + "header=" + header + ", seqid =" + seqid + ", traceid =" + traceid
                + ", body=[" + (bodyContent == null ? -1 : bodyContent.length) + "]"
                + "}";
    }

    public SncpHeader getHeader() {
        return header;
    }

    public long getSeqid() {
        return seqid;
    }

    public byte[] getBodyContent() {
        return bodyContent;
    }
}
