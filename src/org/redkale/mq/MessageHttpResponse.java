/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.net.Response;
import org.redkale.net.http.*;
import org.redkale.util.ObjectPool;

/**
 *
 * @author zhangjx
 */
public class MessageHttpResponse extends HttpResponse {

    public MessageHttpResponse(HttpContext context, MessageHttpRequest request,
        ObjectPool<Response> responsePool, HttpResponseConfig config) {
        super(context, request, responsePool, config);
    }
}
