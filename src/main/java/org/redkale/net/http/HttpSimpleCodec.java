/*
 *
 */
package org.redkale.net.http;

import java.nio.ByteBuffer;
import java.util.logging.Logger;
import org.redkale.net.client.ClientCodec;
import static org.redkale.net.http.HttpRequest.*;
import org.redkale.util.ByteArray;

/**
 *
 * @author zhangjx
 */
class HttpSimpleCodec extends ClientCodec<HttpSimpleRequest, HttpSimpleResult> {

    protected static final Logger logger = Logger.getLogger(HttpSimpleCodec.class.getSimpleName());

    private ByteArray recyclableArray;

    private ByteArray halfBytes;

    private HttpSimpleResult lastResult = null;

    public HttpSimpleCodec(HttpSimpleConnection connection) {
        super(connection);
    }

    protected HttpSimpleResult pollResult(HttpSimpleRequest request) {
        return new HttpSimpleResult();
    }

    protected void offerResult(HttpSimpleResult rs) {
        //do nothing
    }

    private ByteArray pollArray(ByteArray array) {
        if (recyclableArray == null) {
            recyclableArray = new ByteArray();
        }
        recyclableArray.clear();
        if (array != null) {
            recyclableArray.put(array);
        }
        return recyclableArray;
    }

    @Override
    public void decodeMessages(final ByteBuffer realBuf, final ByteArray array) {
        int rs;
        HttpSimpleResult result = this.lastResult;
        final ByteBuffer buffer = realBuf;
        while (buffer.hasRemaining()) {
            if (result == null) {
                result = new HttpSimpleResult();
                this.lastResult = result;
            }
            array.clear();
            if (this.halfBytes != null) {
                array.put(this.halfBytes);
                this.halfBytes = null;
            }
            if (result.readState == READ_STATE_ROUTE) {
                rs = readStatusLine(result, buffer, array);
                if (rs > 0) { //数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { //数据异常
                    occurError(null, new HttpException("http data not valid"));
                    return;
                }
                result.readState = READ_STATE_HEADER;
            }
            if (result.readState == READ_STATE_HEADER) {
                rs = readHeaderLines(result, buffer, array);
                if (rs > 0) { //数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { //数据异常
                    occurError(null, new HttpException("http data not valid"));
                    return;
                }
                result.readState = READ_STATE_BODY;
            }
            if (result.readState == READ_STATE_BODY) {
                rs = readBody(result, buffer, array);
                if (rs > 0) { //数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { //数据异常
                    occurError(null, new HttpException("http data not valid"));
                    return;
                }
                result.readState = READ_STATE_END;
            }
            addMessage(nextRequest(), result);
            lastResult = null;
        }
    }

    //解析 HTTP/1.1 200 OK  
    private int readStatusLine(final HttpSimpleResult result, final ByteBuffer buffer, final ByteArray array) {
        int remain = buffer.remaining();
        if (array.length() > 0 && array.getLastByte() == '\r') { //array存在半截数据
            if (buffer.get() != '\n') {
                return -1;
            }
        } else {
            for (;;) {
                if (remain-- < 1) {
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\r') {
                    if (remain-- < 1) {
                        array.put((byte) '\r');
                        return 1;
                    }
                    if (buffer.get() != '\n') {
                        return -1;
                    }
                    break;
                }
                array.put(b);
            }
        }
        String value = array.toString(null);
        int pos = value.indexOf(' ');
        result.setStatus(Integer.decode(value.substring(pos + 1, value.indexOf(" ", pos + 2))));
        array.clear();
        return 0;
    }

    //解析Header Connection: keep-alive
    //返回0表示解析完整，非0表示还需继续读数据
    private int readHeaderLines(final HttpSimpleResult result, final ByteBuffer buffer, final ByteArray array) {
        int remain = buffer.remaining();
        for (;;) {
            array.clear();
            if (remain-- < 2) {
                if (remain == 1) {
                    array.put(buffer.get());
                    return 1;
                }
                return 1;
            }
            remain--;
            byte b1 = buffer.get();
            byte b2 = buffer.get();
            if (b1 == '\r' && b2 == '\n') {
                return 0;
            }
            boolean latin1 = true;
            if (latin1 && (b1 < 0x20 || b1 >= 0x80)) {
                latin1 = false;
            }
            if (latin1 && (b2 < 0x20 || b2 >= 0x80)) {
                latin1 = false;
            }
            array.put(b1, b2);
            for (;;) {  // name
                if (remain-- < 1) {
                    buffer.clear();
                    buffer.put(array.content(), 0, array.length());
                    return 1;
                }
                byte b = buffer.get();
                if (b == ':') {
                    break;
                } else if (latin1 && (b < 0x20 || b >= 0x80)) {
                    latin1 = false;
                }
                array.put(b);
            }
            String name = parseHeaderName(latin1, array, null);
            array.clear();
            boolean first = true;
            int space = 0;
            for (;;) {  // value
                if (remain-- < 1) {
                    buffer.clear();
                    buffer.put(name.getBytes());
                    buffer.put((byte) ':');
                    if (space == 1) {
                        buffer.put((byte) ' ');
                    } else if (space > 0) {
                        for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                    }
                    buffer.put(array.content(), 0, array.length());
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\r') {
                    if (remain-- < 1) {
                        buffer.clear();
                        buffer.put(name.getBytes());
                        buffer.put((byte) ':');
                        if (space == 1) {
                            buffer.put((byte) ' ');
                        } else if (space > 0) {
                            for (int i = 0; i < space; i++) buffer.put((byte) ' ');
                        }
                        buffer.put(array.content(), 0, array.length());
                        buffer.put((byte) '\r');
                        return 1;
                    }
                    if (buffer.get() != '\n') {
                        return -1;
                    }
                    break;
                }
                if (first) {
                    if (b <= ' ') {
                        space++;
                        continue;
                    }
                    first = false;
                }
                array.put(b);
            }
            String value;
            switch (name) {
                case "Content-Length":
                case "content-length":
                    value = array.toString(true, null);
                    result.contentLength = Integer.decode(value);
                    result.header(name, value);
                    break;
                default:
                    value = array.toString(null);
                    result.header(name, value);
            }
        }
    }

    private int readBody(final HttpSimpleResult result, final ByteBuffer buffer, final ByteArray array) {
        return 0;
    }
}
