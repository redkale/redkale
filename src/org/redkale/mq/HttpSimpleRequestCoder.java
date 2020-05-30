/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.redkale.net.http.HttpSimpleRequest;

/**
 * HttpSimpleRequest的MessageCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class HttpSimpleRequestCoder implements MessageCoder<HttpSimpleRequest> {

    private static final HttpSimpleRequestCoder instance = new HttpSimpleRequestCoder();

    public static HttpSimpleRequestCoder getInstance() {
        return instance;
    }

    @Override
    public byte[] encode(HttpSimpleRequest data) {
        byte[] requestURI = data.getRequestURI() == null ? new byte[0] : data.getRequestURI().getBytes(StandardCharsets.UTF_8);
        return null;
    }

    @Override
    public HttpSimpleRequest decode(byte[] data) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    protected static String getString(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == 0) return null;
        byte[] bs = new byte[len];
        buffer.get(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }
}
