/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.redkale.convert.ConvertType;
import org.redkale.net.http.HttpSimpleRequest;

/**
 * HttpSimpleRequest的MessageCoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpSimpleRequestCoder implements MessageCoder<HttpSimpleRequest> {

    private static final HttpSimpleRequestCoder instance = new HttpSimpleRequestCoder();

    public static HttpSimpleRequestCoder getInstance() {
        return instance;
    }

    @Override
    public byte[] encode(HttpSimpleRequest data) {
        byte[] traceid = MessageCoder.getBytes(data.getTraceid());//short-string
        byte[] requestURI = MessageCoder.getBytes(data.getRequestURI()); //long-string
        byte[] path = MessageCoder.getBytes(data.getPath()); //short-string
        byte[] remoteAddr = MessageCoder.getBytes(data.getRemoteAddr());//short-string
        byte[] sessionid = MessageCoder.getBytes(data.getSessionid());//short-string
        byte[] contentType = MessageCoder.getBytes(data.getContentType());//short-string
        byte[] headers = MessageCoder.getBytes(data.getHeaders());
        byte[] params = MessageCoder.getBytes(data.getParams());
        byte[] body = MessageCoder.getBytes(data.getBody());
        byte[] userid = MessageCoder.encodeUserid(data.getCurrentUserid());
        int count = 1 //rpc + frombody
            + 4 //hashid
            + 4 //reqConvertType
            + 4 //respConvertType
            + 2 + traceid.length
            + 4 + requestURI.length
            + 2 + path.length
            + 2 + remoteAddr.length
            + 2 + sessionid.length
            + 2 + contentType.length
            + 2 + userid.length
            + headers.length + params.length
            + 4 + body.length;
        byte[] bs = new byte[count];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.put((byte) ((data.isRpc() ? 0b01 : 0) | (data.isFrombody() ? 0b10 : 0)));
        buffer.putInt(data.getHashid());
        buffer.putInt(data.getReqConvertType() == null ? 0 : data.getReqConvertType().getValue());
        buffer.putInt(data.getRespConvertType() == null ? 0 : data.getRespConvertType().getValue());
        buffer.putChar((char) traceid.length);
        if (traceid.length > 0) buffer.put(traceid);
        buffer.putInt(requestURI.length);
        if (requestURI.length > 0) buffer.put(requestURI);
        buffer.putChar((char) path.length);
        if (path.length > 0) buffer.put(path);
        buffer.putChar((char) remoteAddr.length);
        if (remoteAddr.length > 0) buffer.put(remoteAddr);
        buffer.putChar((char) sessionid.length);
        if (sessionid.length > 0) buffer.put(sessionid);
        buffer.putChar((char) contentType.length);
        if (contentType.length > 0) buffer.put(contentType);
        buffer.putChar((char) userid.length);
        if (userid.length > 0) buffer.put(userid);
        buffer.put(headers);
        buffer.put(params);
        buffer.putInt(body.length);
        if (body.length > 0) buffer.put(body);
        return bs;
    }

    @Override
    public HttpSimpleRequest decode(byte[] data) {
        if (data == null) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        HttpSimpleRequest req = new HttpSimpleRequest();
        byte opt = buffer.get();
        req.setRpc((opt & 0b01) > 0);
        req.setFrombody((opt & 0b10) > 0);
        req.setHashid(buffer.getInt());
        int reqformat = buffer.getInt();
        int respformat = buffer.getInt();
        if (reqformat != 0) req.setReqConvertType(ConvertType.find(reqformat));
        if (respformat != 0) req.setRespConvertType(ConvertType.find(respformat));
        req.setTraceid(MessageCoder.getShortString(buffer));
        req.setRequestURI(MessageCoder.getLongString(buffer));
        req.setPath(MessageCoder.getShortString(buffer));
        req.setRemoteAddr(MessageCoder.getShortString(buffer));
        req.setSessionid(MessageCoder.getShortString(buffer));
        req.setContentType(MessageCoder.getShortString(buffer));
        req.setCurrentUserid(MessageCoder.decodeUserid(buffer));
        req.setHeaders(MessageCoder.getMap(buffer));
        req.setParams(MessageCoder.getMap(buffer));
        int len = buffer.getInt();
        if (len > 0) {
            byte[] bs = new byte[len];
            buffer.get(bs);
            req.setBody(bs);
        }
        return req;
    }

    protected static String getString(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == 0) return null;
        byte[] bs = new byte[len];
        buffer.get(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }
}
