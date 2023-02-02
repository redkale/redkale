/*
 *
 */
package org.redkale.net.sncp;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import org.redkale.net.client.ClientCodec;
import org.redkale.util.ByteArray;

/**
 *
 * @author zhangjx
 */
public class SncpClientCodec extends ClientCodec<SncpClientRequest, SncpClientResult> {

    protected static final Logger logger = Logger.getLogger(SncpClientCodec.class.getSimpleName());

    private ByteArray recyclableArray;

    protected ByteArray halfBodyBytes;

    protected ByteArray halfHeaderBytes;

    SncpClientResult lastResult = null;

    public SncpClientCodec(SncpClientConnection connection) {
        super(connection);
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
            if (halfHeaderBytes != null) {
                if (buffer.remaining() + halfHeaderBytes.length() < SncpHeader.HEADER_SIZE) { //buffer不足以读取完整header
                    halfHeaderBytes.put(buffer);
                    return;
                }
                halfHeaderBytes.put(buffer, SncpHeader.HEADER_SIZE - halfHeaderBytes.length());
                //读取完整header
                SncpClientResult result = new SncpClientResult();
                if (!result.readHeader(halfHeaderBytes)) {
                    occurError(null, null); //request不一定存在
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
                    halfBodyBytes.put(buffer, lastResult.getBodyLength() - halfHeaderBytes.length());
                    //读取完整body
                    lastResult.setBodyContent(halfBodyBytes.getBytes());
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
                lastResult.readBody(buffer);
                halfBodyBytes = null;
                addMessage(findRequest(lastResult.getRequestid()), lastResult);
                lastResult = null;
                continue;
            }
            if (buffer.remaining() < SncpHeader.HEADER_SIZE) { //buffer不足以读取完整header
                halfHeaderBytes = pollArray();
                halfHeaderBytes.put(buffer);
                return;
            }
            SncpClientResult result = new SncpClientResult();
            if (!result.readHeader(buffer)) {
                occurError(null, null); //request不一定存在
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
