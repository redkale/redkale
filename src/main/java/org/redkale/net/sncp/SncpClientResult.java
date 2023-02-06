/*
 *
 */
package org.redkale.net.sncp;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Objects;
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
public class SncpClientResult {

    private SncpHeader header;

    private byte[] bodyContent;

    protected void prepare() {
    }

    protected boolean recycle() {
        this.header = null;
        this.bodyContent = null;
        return true;
    }

    protected int readHeader(ByteBuffer buffer) {
        this.header = new SncpHeader();
        return this.header.read(buffer);
    }

    protected int readHeader(ByteArray array) {
        this.header = new SncpHeader();
        return this.header.read(array);
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
