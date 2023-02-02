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
    public boolean decodeMessages(ByteBuffer realBuf, ByteArray array) {
        SncpClientConnection conn = (SncpClientConnection) connection;

        ByteBuffer buffer = realBuf;
        boolean hadResult = false;
        while (buffer.hasRemaining()) {
            if (halfHeaderBytes != null) {
                if (buffer.remaining() + halfHeaderBytes.length() < SncpHeader.HEADER_SIZE) { //buffer不足以读取完整header
                    halfHeaderBytes.put(buffer);
                    return hadResult;
                }
                halfHeaderBytes.put(buffer, SncpHeader.HEADER_SIZE - halfHeaderBytes.length());
                //读取完整header
                SncpClientResult result = new SncpClientResult();
                result.readHeader(halfHeaderBytes);
                halfHeaderBytes = null;
                if (result.getBodyLength() < 1) {
                    addMessage(findRequest(result.getRequestid()), result);
                    lastResult = null;
                    continue;
                }
                //还需要读body
                lastResult = result;
            }
            if (lastResult != null) { //buffer不够
                if (halfBodyBytes != null) {
                    if (buffer.remaining() + halfBodyBytes.length() < lastResult.getBodyLength()) { //buffer不足以读取完整body
                        halfBodyBytes.put(buffer);
                        return hadResult;
                    }
                    halfBodyBytes.put(buffer, lastResult.getBodyLength() - halfHeaderBytes.length());
                    //读取完整body
                    lastResult.setBodyContent(halfBodyBytes.getBytes());
                    halfBodyBytes = null;
                    addMessage(findRequest(lastResult.getRequestid()), lastResult);
                    lastResult = null;
                    continue;
                }
                
            }
            if (buffer.remaining() < SncpHeader.HEADER_SIZE) { //内容不足以读取完整header
                halfHeaderBytes = pollArray();
                halfHeaderBytes.put(buffer);
                return hadResult;
            }

            SncpClientRequest request = null;
            buffer = realBuf;
        }
        return hadResult;
    }

}
