/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.nio.ByteBuffer;
import org.redkale.net.sncp.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpMessageRequest extends SncpRequest {

    public SncpMessageRequest(SncpContext context) {
        super(context, null);
    }

    @Override
    public int readHeader(ByteBuffer buffer) {
        return super.readHeader(buffer);
    }
}
