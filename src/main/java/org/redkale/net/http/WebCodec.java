/*
 *
 */
package org.redkale.net.http;

import static org.redkale.net.http.HttpRequest.*;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.redkale.net.client.ClientCodec;
import org.redkale.util.ByteArray;

/** @author zhangjx */
class WebCodec extends ClientCodec<WebRequest, WebResult> {

    protected static final Logger logger = Logger.getLogger(WebCodec.class.getSimpleName());

    private ByteArray recyclableArray;

    private ByteArray halfBytes;

    private WebResult lastResult = null;

    public WebCodec(WebConnection connection) {
        super(connection);
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
        final ByteBuffer buffer = realBuf;
        while (buffer.hasRemaining()) {
            WebResult result = this.lastResult;
            if (result == null) {
                result = new WebResult();
                result.readState = READ_STATE_ROUTE;
                this.lastResult = result;
            }
            array.clear();
            if (this.halfBytes != null) {
                array.put(this.halfBytes);
                this.halfBytes = null;
            }
            if (result.readState == READ_STATE_ROUTE) {
                rs = readStatusLine(result, buffer, array);
                if (rs > 0) { // 数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { // 数据异常
                    occurError(null, new HttpException("http status not valid"));
                    return;
                }
                result.readState = READ_STATE_HEADER;
            }
            if (result.readState == READ_STATE_HEADER) {
                rs = readHeaderBytes(result, buffer, array);
                if (rs > 0) { // 数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { // 数据异常
                    occurError(null, new HttpException("http header not valid"));
                    return;
                }
                result.readState = READ_STATE_BODY;
            }
            if (result.readState == READ_STATE_BODY) {
                rs = readBody(result, buffer, array);
                if (rs > 0) { // 数据不全
                    this.halfBytes = pollArray(array);
                    return;
                } else if (rs < 0) { // 数据异常
                    occurError(null, new HttpException("http data not valid"));
                    return;
                }
                result.readState = READ_STATE_END;
            }
            addMessage(nextRequest(), result);
            lastResult = null;
        }
    }

    // 解析 HTTP/1.1 200 OK
    private int readStatusLine(final WebResult result, final ByteBuffer buffer, final ByteArray array) {
        int remain = buffer.remaining();
        if (array.length() > 0 && array.getLastByte() == '\r') { // array存在半截数据
            if (buffer.get() != '\n') {
                return -1;
            }
        } else {
            for (; ; ) {
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

    // 解析Header Connection: keep-alive
    // 返回0表示解析完整，非0表示还需继续读数据
    private int readHeaderBytes(final WebResult result, final ByteBuffer buffer, final ByteArray array) {
        byte b;
        while (buffer.hasRemaining()) {
            b = buffer.get();
            if (b == '\n') {
                int len = array.length();
                if (len >= 3
                        && array.get(len - 1) == '\r'
                        && array.get(len - 2) == '\n'
                        && array.get(len - 3) == '\r') {
                    // 最后一个\r\n不写入
                    readHeaderLines(result, array.removeLastByte()); // 移除最后一个\r
                    array.clear();
                    return 0;
                }
            }
            array.put(b);
        }
        return 1;
    }

    private int readBody(final WebResult result, final ByteBuffer buffer, final ByteArray array) {
        if (result.contentLength >= 0) {
            array.put(buffer, Math.min((int) result.contentLength, buffer.remaining()));
            int lr = (int) result.contentLength - array.length();
            if (lr == 0) {
                result.result(array.getBytes());
            }
            return lr > 0 ? lr : 0;
        }
        return -1;
    }

    private void readHeaderLines(final WebResult result, ByteArray bytes) {
        int start = 0;
        int posC, posR;
        Charset charset = StandardCharsets.UTF_8;
        while (start < bytes.length()) {
            posC = bytes.indexOf(start, ':');
            String name = bytes.toString(start, posC - start, charset).trim();
            posR = bytes.indexOf(posC + 1, '\r');
            String value = bytes.toString(posC + 1, posR - posC - 1, charset).trim();
            result.header(name, value);
            if ("Content-Length".equalsIgnoreCase(name)) {
                result.contentLength = Integer.parseInt(value);
            }
            start = posR + 2; // 跳过\r\n
        }
    }
}
