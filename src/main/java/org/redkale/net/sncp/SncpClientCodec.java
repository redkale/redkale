/*
 *
 */
package org.redkale.net.sncp;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import org.redkale.net.client.ClientCodec;
import org.redkale.util.*;

/**
 * SncpClient编解码器
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class SncpClientCodec extends ClientCodec<SncpClientRequest, SncpClientResult> {

    protected static final Logger logger = Logger.getLogger(SncpClientCodec.class.getSimpleName());

    private ByteArray recyclableArray;

    private ByteArray halfBodyBytes;

    private ByteArray halfHeaderBytes;

    private int halfHeaderSize;

    private Byte halfHeaderSizeFirstByte;

    SncpClientResult lastResult = null;

    public SncpClientCodec(SncpClientConnection connection) {
        super(connection);
    }

    protected SncpClientResult pollResult(SncpClientRequest request) {
        return new SncpClientResult();
    }

    protected void offerResult(SncpClientResult rs) {
        //do nothing
    }

    protected ByteArray pollArray() {
        if (recyclableArray == null) {
            recyclableArray = new ByteArray();
            return recyclableArray;
        }
        recyclableArray.clear();
        return recyclableArray;
    }

    @Override
    public void decodeMessages(ByteBuffer realBuf, ByteArray array) {
        ByteBuffer buffer = realBuf;
        while (buffer.hasRemaining()) {
            if (this.halfHeaderBytes != null) {
                if (buffer.remaining() + halfHeaderBytes.length() < halfHeaderSize - 2) { //buffer不足以读取完整header
                    halfHeaderBytes.put(buffer);
                    return;
                }
                halfHeaderBytes.put(buffer, halfHeaderSize - 2 - halfHeaderBytes.length());
                //读取完整header
                SncpClientResult result = new SncpClientResult();
                result.readHeader(halfHeaderBytes, halfHeaderSize);
                halfHeaderSize = 0;
                if (!result.getHeader().isValid()) {
                    occurError(null, new SncpException("sncp header not valid"));
                    return;
                }
                halfHeaderBytes = null;
                if (result.getBodyLength() < 1) {
                    addMessage(findRequest(result.getRequestid()), result);
                    lastResult = null;
                    continue;
                }
                //还需要读body
                lastResult = result;
            }
            if (lastResult != null) { //lastResult的body没有读完
                if (halfBodyBytes != null) {
                    if (buffer.remaining() + halfBodyBytes.length() < lastResult.getBodyLength()) { //buffer不足以读取完整body
                        halfBodyBytes.put(buffer);
                        return;
                    }
                    halfBodyBytes.put(buffer, lastResult.getBodyLength() - halfBodyBytes.length());
                    //读取完整body
                    lastResult.setBodyContent(halfBodyBytes.getBytes());
                    //if (halfBodyBytes.length() != lastResult.getBodyLength()) {
                    //    logger.log(Level.SEVERE, "halfBodyBytes.length must be " + lastResult.getBodyLength() + ", but " + halfBodyBytes.length());
                    //}
                    halfBodyBytes = null;
                    addMessage(findRequest(lastResult.getRequestid()), lastResult);
                    lastResult = null;
                    continue;
                }
                if (buffer.remaining() < lastResult.getBodyLength()) {  //buffer不足以读取完整body
                    halfBodyBytes = pollArray();
                    halfBodyBytes.put(buffer);
                    return;
                }
                //有足够的数据读取完整body
                lastResult.readBody(buffer);
                halfBodyBytes = null;
                //if (lastResult.getBodyContent().length != lastResult.getBodyLength()) {
                //    logger.log(Level.SEVERE, "lastResult.length must be " + lastResult.getBodyLength() + ", but " + lastResult.getBodyContent().length);
                //}
                addMessage(findRequest(lastResult.getRequestid()), lastResult);
                lastResult = null;
                continue;
            }
            if (this.halfHeaderSize < 1) {
                if (buffer.remaining() < 2) { //只有一个字节
                    this.halfHeaderSizeFirstByte = buffer.get();
                    return;
                } else {
                    if (this.halfHeaderSizeFirstByte != null) {
                        byte secondByte = buffer.get();
                        this.halfHeaderSize = (0xff00 & (this.halfHeaderSizeFirstByte << 8)) | (0xff & secondByte);
                    } else {
                        this.halfHeaderSize = buffer.getChar();
                    }
                    this.halfHeaderSizeFirstByte = null;
                    if (buffer.remaining() < this.halfHeaderSize - 2) { //buffer不足以读取完整header
                        this.halfHeaderBytes = pollArray();
                        this.halfHeaderBytes.put(buffer);
                        return;
                    }
                }
            }
            SncpClientResult result = new SncpClientResult();
            result.readHeader(buffer, halfHeaderSize);
            halfHeaderSize = 0;
            if (!result.getHeader().isValid()) {
                occurError(null, new SncpException("sncp header not valid"));
                return;
            }
            if (result.getBodyLength() < 1) {
                addMessage(findRequest(result.getRequestid()), result);
                lastResult = null;
                continue;
            }
            if (buffer.remaining() < result.getBodyLength()) {  //buffer不足以读取完整body
                lastResult = result;
                halfBodyBytes = pollArray();
                halfBodyBytes.put(buffer);
                return;
            }
            result.readBody(buffer);
            addMessage(findRequest(result.getRequestid()), result);
            lastResult = null;
        }
    }

}
