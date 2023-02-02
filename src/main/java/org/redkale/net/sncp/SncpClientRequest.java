/*
 *
 */
package org.redkale.net.sncp;

import java.util.Objects;
import org.redkale.net.client.*;
import org.redkale.util.ByteArray;

/**
 *
 * @author zhangjx
 */
public class SncpClientRequest extends ClientRequest {

    private SncpHeader header;

    private long seqid;

    private byte[] bodyContent;

    public SncpClientRequest() {
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
    }

    @Override
    protected boolean recycle() {
        boolean rs = super.recycle();
        this.header = null;
        this.seqid = 0;
        this.bodyContent = null;
        return rs;
    }

    @Override
    public void writeTo(ClientConnection conn, ByteArray array) {
        if (bodyContent == null) {
            header.writeTo(array, header.getAddrBytes(), header.getAddrPort(), seqid, 0, 0);
        } else {
            header.writeTo(array, header.getAddrBytes(), header.getAddrPort(), seqid, bodyContent.length, 0);
            array.put(bodyContent);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hashCode(this) + "{"
            + "header=" + header
            + ", seqid =" + seqid
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
