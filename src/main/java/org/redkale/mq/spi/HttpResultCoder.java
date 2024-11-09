/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.convert.Convert;
import org.redkale.convert.json.JsonConvert;
import static org.redkale.mq.spi.MessageCoder.*;
import org.redkale.net.http.HttpResult;
import org.redkale.util.Utility;

/**
 * HttpResult的MessageCoder实现
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class HttpResultCoder implements MessageCoder<HttpResult> {

    private static final HttpResultCoder instance = new HttpResultCoder();

    public static HttpResultCoder getInstance() {
        return instance;
    }

    // 消息内容的类型
    @Override
    public byte ctype() {
        return MessageRecord.CTYPE_HTTP_RESULT;
    }

    @Override
    public byte[] encode(HttpResult data) {
        if (data == null) {
            return null;
        }
        byte[] contentType = MessageCoder.getBytes(data.getContentType());
        byte[] headers = MessageCoder.getSeriMapBytes(data.getHeaders());
        byte[] cookies = getBytes(data.getCookies());
        byte[] content;
        if (data.getResult() == null) {
            content = new byte[0]; // ""
        } else if (data.getResult() instanceof byte[]) {
            content = (byte[]) data.getResult();
        } else if (data.getResult() instanceof CharSequence) {
            content = MessageCoder.getBytes(data.getResult().toString());
        } else {
            Convert cc = data.convert();
            if (cc == null) {
                cc = JsonConvert.root();
            }
            content = cc.convertToBytes(data.getResult());
        }
        int count = 4
                + 2
                + contentType.length
                + headers.length
                + cookies.length
                + 4
                + (content == null ? 0 : content.length);
        final byte[] bs = new byte[count];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.putInt(data.getStatus());
        buffer.putChar((char) contentType.length);
        if (contentType.length > 0) {
            buffer.put(contentType);
        }
        buffer.put(headers);
        buffer.put(cookies);
        if (content == null || content.length == 0) {
            buffer.putInt(0);
        } else {
            buffer.putInt(content.length);
            buffer.put(content);
        }
        return bs;
    }

    @Override
    public HttpResult<byte[]> decode(byte[] data) {
        if (data == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        HttpResult result = new HttpResult();
        result.setStatus(buffer.getInt());
        result.setContentType(MessageCoder.getSmallString(buffer));
        result.setHeaders(MessageCoder.getSeriMap(buffer));
        result.setCookies(getCookieList(buffer));
        int len = buffer.getInt();
        if (len > 0) {
            byte[] bs = new byte[len];
            buffer.get(bs);
            result.setResult(bs);
        }
        return result;
    }

    public static byte[] getBytes(final List<HttpCookie> list) {
        if (list == null || list.isEmpty()) {
            return new byte[2];
        }
        final AtomicInteger len = new AtomicInteger(2);
        list.forEach(cookie -> {
            len.addAndGet(2 + (cookie.getName() == null ? 0 : Utility.encodeUTF8Length(cookie.getName())));
            len.addAndGet(2 + (cookie.getValue() == null ? 0 : Utility.encodeUTF8Length(cookie.getValue())));
            len.addAndGet(2 + (cookie.getDomain() == null ? 0 : Utility.encodeUTF8Length(cookie.getDomain())));
            len.addAndGet(2 + (cookie.getPath() == null ? 0 : Utility.encodeUTF8Length(cookie.getPath())));
            len.addAndGet(2 + (cookie.getPortlist() == null ? 0 : Utility.encodeUTF8Length(cookie.getPortlist())));
            len.addAndGet(8 + 1 + 1); // maxage Secure HttpOnly
        });
        final byte[] bs = new byte[len.get()];
        final ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.putChar((char) list.size());
        list.forEach(cookie -> {
            putSmallString(buffer, cookie.getName());
            putSmallString(buffer, cookie.getValue());
            putSmallString(buffer, cookie.getDomain());
            putSmallString(buffer, cookie.getPath());
            putSmallString(buffer, cookie.getPortlist());
            buffer.putLong(cookie.getMaxAge());
            buffer.put(cookie.getSecure() ? (byte) 1 : (byte) 0);
            buffer.put(cookie.isHttpOnly() ? (byte) 1 : (byte) 0);
        });
        return bs;
    }

    public static List<HttpCookie> getCookieList(ByteBuffer buffer) {
        int len = buffer.getChar();
        if (len == 0) {
            return null;
        }
        final List<HttpCookie> list = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            HttpCookie cookie = new HttpCookie(getSmallString(buffer), getSmallString(buffer));
            cookie.setDomain(getSmallString(buffer));
            cookie.setPath(getSmallString(buffer));
            cookie.setPortlist(getSmallString(buffer));
            cookie.setMaxAge(buffer.getLong());
            cookie.setSecure(buffer.get() == 1);
            cookie.setHttpOnly(buffer.get() == 1);
            list.add(cookie);
        }
        return list;
    }
}
