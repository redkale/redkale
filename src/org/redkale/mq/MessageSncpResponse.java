/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.net.Response;
import org.redkale.net.sncp.*;
import org.redkale.util.ObjectPool;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class MessageSncpResponse extends SncpResponse {

    public MessageSncpResponse(SncpContext context, MessageSncpRequest request, ObjectPool<Response> responsePool) {
        super(context, request, responsePool);
    }
}
