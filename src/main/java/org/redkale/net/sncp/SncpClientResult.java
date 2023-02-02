/*
 *
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import org.redkale.util.ByteArray;

/**
 *
 * @author zhangjx
 */
public class SncpClientResult {

    private SncpHeader header;

    private byte[] bodyContent;

    protected boolean readHeader(ByteBuffer buffer) {
        SncpHeader h = new SncpHeader();
        boolean rs = h.read(buffer);
        this.header = h;
        return rs;
    }

    protected boolean readHeader(ByteArray array) {
        SncpHeader h = new SncpHeader();
        boolean rs = h.read(array);
        this.header = h;
        return rs;
    }

    protected boolean readBody(ByteBuffer buffer) {
        byte[] body = new byte[header.getBodyLength()];
        buffer.get(body);
        this.bodyContent = body;
        return true;
    }

    public Serializable getRequestid() {
        return header == null ? null : header.getSeqid();
    }

    public int getBodyLength() {
        return header.getBodyLength();
    }

    public SncpHeader getHeader() {
        return header;
    }

    public byte[] getBodyContent() {
        return bodyContent;
    }

    public void setBodyContent(byte[] bodyContent) {
        this.bodyContent = bodyContent;
    }

}
