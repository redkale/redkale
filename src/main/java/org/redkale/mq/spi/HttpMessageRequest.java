/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import org.redkale.convert.Convert;
import org.redkale.net.http.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class HttpMessageRequest extends HttpRequest {

    protected MessageRecord message;

    public HttpMessageRequest(HttpContext context) {
        this(context, (MessageRecord) null);
    }

    public HttpMessageRequest(HttpContext context, MessageRecord message) {
        super(context, (WebRequest) null);
        if (message != null) {
            prepare(message);
        }
    }

    protected HttpMessageRequest prepare(MessageRecord message) {
        super.initWebRequest(message.decodeContent(WebRequestCoder.getInstance()), false);
        this.message = message;
        this.currentUserid = message.getUserid();
        this.createTime = System.currentTimeMillis();
        return this;
    }

    @Override
    public HttpMessageRequest setRequestPath(String path) {
        this.requestPath = path;
        return this;
    }

    @Override
    public Convert getRespConvert() {
        return this.respConvert;
    }

    @Override
    protected void prepare() {
        super.prepare();
        this.keepAlive = false;
    }

    @Override
    protected void recycle() {
        super.recycle();
        this.message = null;
    }
}
