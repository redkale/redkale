/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.redkale.convert.ConvertType;
import org.redkale.net.http.HttpHeaders;
import org.redkale.net.http.HttpParameters;
import org.redkale.net.http.HttpSimpleRequest;
import org.redkale.util.Utility;

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

    //消息内容的类型
    @Override
    public byte ctype() {
        return MessageRecord.CTYPE_HTTP_REQUEST;
    }

    @Override
    public byte[] encode(HttpSimpleRequest data) {
        byte[] traceid = MessageCoder.getBytes(data.getTraceid());//short-string
        byte[] path = MessageCoder.getBytes(data.getPath()); //long-string
        byte[] contextPath = MessageCoder.getBytes(data.getContextPath()); //short-string
        byte[] method = MessageCoder.getBytes(data.getMethod());//short-string
        byte[] remoteAddr = MessageCoder.getBytes(data.getRemoteAddr());//short-string
        byte[] sessionid = MessageCoder.getBytes(data.getSessionid());//short-string
        byte[] contentType = MessageCoder.getBytes(data.getContentType());//short-string
        byte[] userid = MessageCoder.encodeUserid(data.getCurrentUserid());
        byte[] headers = MessageCoder.getSeriMapBytes(data.getHeaders() == null ? null : data.getHeaders().map());
        byte[] params = MessageCoder.getStringMapBytes(data.getParams() == null ? null : data.getParams().map());
        byte[] body = MessageCoder.getBytes(data.getBody());
        int count = 1 //rpc
            + 4 //reqConvertType
            + 4 //respConvertType
            + 2 + traceid.length
            + 4 + path.length
            + 2 + contextPath.length
            + 2 + method.length
            + 2 + remoteAddr.length
            + 2 + sessionid.length
            + 2 + contentType.length
            + 2 + userid.length
            + headers.length
            + params.length
            + 4 + body.length;

        byte[] bs = new byte[count];
        ByteBuffer buffer = ByteBuffer.wrap(bs);
        buffer.put((byte) (data.isRpc() ? 0b01 : 0));
        buffer.putInt(data.getReqConvertType() == null ? 0 : data.getReqConvertType().getValue());
        buffer.putInt(data.getRespConvertType() == null ? 0 : data.getRespConvertType().getValue());

        if (data.getTraceid() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) traceid.length);
            buffer.put(traceid);
        }

        if (data.getPath() == null) {
            buffer.putInt(-1);
        } else {
            buffer.putInt(path.length);
            buffer.put(path);
        }

        if (data.getContextPath() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) contextPath.length);
            buffer.put(contextPath);
        }

        if (data.getMethod() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) method.length);
            buffer.put(method);
        }

        if (data.getRemoteAddr() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) remoteAddr.length);
            buffer.put(remoteAddr);
        }

        if (data.getSessionid() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) sessionid.length);
            buffer.put(sessionid);
        }

        if (data.getContentType() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) contentType.length);
            buffer.put(contentType);
        }

        if (data.getCurrentUserid() == null) {
            buffer.putShort((short) -1);
        } else {
            buffer.putShort((short) userid.length);
            buffer.put(userid);
        }

        buffer.put(headers);
        buffer.put(params);
        if (data.getBody() == null) {
            buffer.putInt(-1);
        } else {
            buffer.putInt(body.length);
            buffer.put(body);
        }
        return bs;
    }

    @Override
    public HttpSimpleRequest decode(byte[] data) {
        if (data == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data);
        HttpSimpleRequest req = new HttpSimpleRequest();
        byte opt = buffer.get();
        req.setRpc((opt & 0b01) > 0);
        int reqformat = buffer.getInt();
        int respformat = buffer.getInt();
        if (reqformat != 0) {
            req.setReqConvertType(ConvertType.find(reqformat));
        }
        if (respformat != 0) {
            req.setRespConvertType(ConvertType.find(respformat));
        }
        req.setTraceid(MessageCoder.getSmallString(buffer));
        req.setPath(MessageCoder.getBigString(buffer));
        req.setContextPath(MessageCoder.getSmallString(buffer));
        req.setMethod(MessageCoder.getSmallString(buffer));
        req.setRemoteAddr(MessageCoder.getSmallString(buffer));
        req.setSessionid(MessageCoder.getSmallString(buffer));
        req.setContentType(MessageCoder.getSmallString(buffer));
        req.setCurrentUserid(MessageCoder.decodeUserid(buffer));
        Map<String, Serializable> headerMap = MessageCoder.getSeriMap(buffer);
        if (Utility.isNotEmpty(headerMap)) {
            req.setHeaders(HttpHeaders.ofValid(headerMap));
        }
        Map<String, String> paramsMap = MessageCoder.getStringMap(buffer);
        if (Utility.isNotEmpty(paramsMap)) {
            req.setParams(HttpParameters.ofValid(paramsMap));
        }
        int len = buffer.getInt();
        if (len >= 0) {
            byte[] bs = new byte[len];
            buffer.get(bs);
            req.setBody(bs);
        }
        return req;
    }

    protected static String getString(ByteBuffer buffer) {
        int len = buffer.getInt();
        if (len == 0) {
            return null;
        }
        byte[] bs = new byte[len];
        buffer.get(bs);
        return new String(bs, StandardCharsets.UTF_8);
    }
}
