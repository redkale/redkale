/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq.spi;

import java.nio.ByteBuffer;
import org.redkale.net.sncp.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.1.0
 */
public class SncpMessageRequest extends SncpRequest {

    protected MessageRecord message;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public SncpMessageRequest(SncpContext context, MessageRecord message) {
        super(context);
        this.message = message;
        this.createTime = System.currentTimeMillis();
        readHeader(ByteBuffer.wrap(message.getContent()), -1);
    }
}
