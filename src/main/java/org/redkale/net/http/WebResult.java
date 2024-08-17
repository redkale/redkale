/*
 *
 */
package org.redkale.net.http;

import java.nio.ByteBuffer;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.client.ClientResult;
import static org.redkale.net.http.HttpRequest.READ_STATE_END;
import org.redkale.util.ByteArray;
import org.redkale.util.RedkaleException;

/**
 * 详情见: https://redkale.org
 *
 * @see org.redkale.net.http.WebClient
 * @see org.redkale.net.http.WebConnection
 * @see org.redkale.net.http.WebRequest
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public class WebResult<T> extends HttpResult<T> implements ClientResult {

    int readState;

    int contentLength = -1;

    String contentEncoding;

    ByteArray array;

    ByteArray chunkBody;

    boolean chunked;

    // 是否已读\r
    boolean chunkedCR = false;

    int chunkedLength = -1;

    int chunkedCurrOffset = -1;

    byte[] chunkedHalfLenBytes;

    @Override
    @ConvertDisabled
    public boolean isKeepAlive() {
        return true;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(HttpResult.class, this);
    }

    ByteArray array() {
        if (array == null) {
            array = new ByteArray();
        }
        return array;
    }

    int readChunkedBody(final ByteBuffer buf) {
        final ByteBuffer buffer = buf;
        int remain = buffer.remaining();
        if (this.chunkedLength < 0) { // 需要读取length
            ByteArray input = array();
            input.clear();
            if (this.chunkedHalfLenBytes != null) {
                input.put(this.chunkedHalfLenBytes);
                this.chunkedHalfLenBytes = null;
            }
            for (; ; ) {
                if (remain-- < 1) {
                    buffer.clear();
                    if (input.length() > 0) {
                        this.chunkedHalfLenBytes = input.getBytes();
                    }
                    return 1;
                }
                byte b = buffer.get();
                if (b == '\n') {
                    break;
                }
                input.put(b);
            }
            this.chunkedLength = HttpRequest.parseHexLength(input);
            this.chunkedCurrOffset = 0;
            this.chunkedCR = false;
        }
        if (this.chunkedLength == 0) {
            if (remain < 1) {
                buffer.clear();
                return 1;
            }
            if (!this.chunkedCR) { // 读\r
                remain--;
                if (buffer.get() != '\r') {
                    throw new RedkaleException("invalid chunk end");
                }
                this.chunkedCR = true;
                if (remain < 1) {
                    buffer.clear();
                    return 1;
                }
            }
            // 读\n
            remain--;
            if (buffer.get() != '\n') {
                throw new RedkaleException("invalid chunk end");
            }
            this.readState = READ_STATE_END;
            return 0;
        } else {
            ByteArray bodyBytes = this.chunkBody;
            if (this.chunkedCurrOffset < this.chunkedLength) {
                for (; ; ) {
                    if (remain-- < 1) {
                        buffer.clear();
                        return 1;
                    }
                    byte b = buffer.get();
                    bodyBytes.put(b);
                    this.chunkedCurrOffset++;
                    if (this.chunkedCurrOffset == this.chunkedLength) {
                        this.chunkedCR = false;
                        break;
                    }
                }
            }
            if (remain < 1) {
                buffer.clear();
                return 1;
            }
            // 读\r
            if (!this.chunkedCR) {
                remain--;
                if (buffer.get() != '\r') {
                    throw new RedkaleException("invalid chunk end");
                }
                this.chunkedCR = true;
                if (remain < 1) {
                    buffer.clear();
                    return 1;
                }
            }
            // 读\n
            remain--;
            if (buffer.get() != '\n') {
                throw new RedkaleException("invalid chunk end");
            }
            this.chunkedLength = -1;
            // 继续读下一个chunk
            return readChunkedBody(buffer);
        }
    }
}
