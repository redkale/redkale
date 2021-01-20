/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.net.sncp.*;
import org.redkale.util.Utility;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class SncpMessageRequest extends SncpRequest {

    protected MessageRecord message;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public SncpMessageRequest(SncpContext context, MessageRecord message) {
        super(context);
        this.message = message;
        readHeader(ByteBuffer.wrap(message.getContent()));
    }

    @Override //被SncpAsyncHandler.sncp_setParams调用
    protected void sncp_setParams(SncpDynServlet.SncpServletAction action, Logger logger, Object... params) {
        if (message.localattach != null) return;
        if (logger.isLoggable(Level.FINER)) {
            message.attach(Utility.append(new Object[]{action.actionName()}, params));
        } else {
            message.attach(params);
        }
    }
}
