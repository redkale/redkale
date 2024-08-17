/*
 *
 */
package org.redkale.net.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import org.redkale.net.client.ClientCodec;
import static org.redkale.net.http.HttpRequest.*;
import org.redkale.util.ByteArray;

/**
 * 详情见: https://redkale.org
 *
 * @see org.redkale.net.http.WebClient
 * @see org.redkale.net.http.WebConnection
 * @see org.redkale.net.http.WebRequest
 * @see org.redkale.net.http.WebResult
 *
 * @author zhangjx
 * @since 2.8.0
 */
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
        if (result.chunked) {
            if (result.chunkBody == null) {
                result.chunkBody = new ByteArray();
            }
            int rs = result.readChunkedBody(buffer);
            if (rs == 0) {
                rs = unzipEncoding(result, result.chunkBody);
                result.result(result.chunkBody.getBytes());
                result.array = null;
                result.chunkBody = null;
            }
            return rs;
        } else if (result.contentLength >= 0) {
            array.put(buffer, Math.min((int) result.contentLength, buffer.remaining()));
            int lr = (int) result.contentLength - array.length();
            if (lr == 0) {
                lr = unzipEncoding(result, array);
                result.result(array.getBytes());
                if (lr < 0) {
                    return lr;
                }
            }
            return lr > 0 ? lr : 0;
        }
        return -1;
    }

    private int unzipEncoding(final WebResult result, ByteArray body) {
        if (result.contentEncoding != null) {
            try {
                if ("gzip".equalsIgnoreCase(result.contentEncoding)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ByteArrayInputStream in = new ByteArrayInputStream(body.content(), 0, body.length());
                    GZIPInputStream ungzip = new GZIPInputStream(in);
                    int n;
                    byte[] buffer = result.array().content();
                    while ((n = ungzip.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    body.clear();
                    body.put(out.toByteArray());
                } else if ("deflate".equalsIgnoreCase(result.contentEncoding)) {
                    Inflater infl = new Inflater();
                    infl.setInput(body.content(), 0, body.length());
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    int n;
                    byte[] buffer = result.array().content();
                    while (!infl.finished()) {
                        n = infl.inflate(buffer);
                        if (n == 0) {
                            break;
                        }
                        out.write(buffer, 0, n);
                    }
                    infl.end();
                    body.clear();
                    body.put(out.toByteArray());
                }
            } catch (Exception e) {
                return -1;
            }
        }
        return 0;
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
            if (HEAD_CONTENT_LENGTH.equalsIgnoreCase(name)) {
                result.contentLength = Integer.parseInt(value);
            } else if (HEAD_CONTENT_ENCODING.equalsIgnoreCase(name)) {
                result.contentEncoding = value;
            } else if (HEAD_TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                result.chunked = "chunked".equalsIgnoreCase(value);
            }
            start = posR + 2; // 跳过\r\n
        }
    }
}
