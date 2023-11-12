/*
 *
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.redkale.net.client.ClientResult;
import org.redkale.util.ByteArray;

/**
 * client版响应结果
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpClientResult implements ClientResult {

    private SncpHeader header;

    private byte[] bodyContent;

    protected void prepare() {
        //do nothing
    }

    protected boolean recycle() {
        this.header = null;
        this.bodyContent = null;
        return true;
    }

    protected boolean readHeader(ByteBuffer buffer, int headerSize) {
        this.header = SncpHeader.read(buffer, headerSize);
        return this.header.isValid();
    }

    protected boolean readHeader(ByteArray array, int headerSize) {
        this.header = SncpHeader.read(array, headerSize);
        return this.header.isValid();
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

    @Override
    public boolean isKeepAlive() {
        return header != null && header.isKeepAlive();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "_" + Objects.hashCode(this) + "{"
            + "header=" + header
            + ", body=[" + (bodyContent == null ? -1 : bodyContent.length) + "]"
            + "}";
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
