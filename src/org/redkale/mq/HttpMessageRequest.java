/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.convert.*;
import org.redkale.net.http.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class HttpMessageRequest extends HttpRequest {

    protected MessageRecord message;

    protected Convert diyConvert;

    public HttpMessageRequest(HttpContext context, MessageRecord message) {
        super(context, message.decodeContent(HttpSimpleRequestCoder.getInstance()));
        this.message = message;
        this.currentUserid = message.getUserid();
        if (message.getFormat() != ConvertType.JSON) {
            this.diyConvert = ConvertFactory.findConvert(message.getFormat());
        }
    }

    public void setRequestURI(String uri) {
        this.requestURI = uri;
    }

    @Override
    public <T> T getBodyJson(java.lang.reflect.Type type) {
        if (diyConvert != null) return (T) diyConvert.convertFrom(type, getBody());
        return super.getBodyJson(type);
    }

    @Override
    public String getParameter(String name) {
        if (diyConvert != null) return (String) diyConvert.convertFrom(String.class, getBody());
        return super.getParameter(name);
    }

    @Override
    public String getParameter(String name, String defaultValue) {
        if (diyConvert != null) {
            String val = (String) diyConvert.convertFrom(String.class, getBody());
            return val == null ? defaultValue : val;
        }
        return super.getParameter(name, defaultValue);
    }

    @Override
    public <T> T getJsonParameter(java.lang.reflect.Type type, String name) {
        if (diyConvert != null) return (T) diyConvert.convertFrom(type, getBody());
        return super.getJsonParameter(type, name);
    }
}
