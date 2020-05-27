/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import org.redkale.net.http.*;

/**
 *
 * @author zhangjx
 */
public class MessageHttpRequest extends HttpRequest {

    protected String remoteAddr;

    public MessageHttpRequest(HttpContext context) {
        super(context, null);
    }

    @Override
    public String getRemoteAddr() {
        return remoteAddr;
    }

    @Override
    public int readHeader(ByteBuffer buffer) {
        return super.readHeader(buffer);
    }
}
