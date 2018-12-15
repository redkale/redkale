/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.nio.ByteBuffer;
import org.redkale.net.*;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class SncpContext extends Context {

    public SncpContext(SncpContextConfig config) {
        super(config);
    }

    @Override
    protected void offerBuffer(ByteBuffer buffer) {
        super.offerBuffer(buffer);
    }

    @Override
    protected void offerBuffer(ByteBuffer... buffers) {
        super.offerBuffer(buffers);
    }

    public static class SncpContextConfig extends ContextConfig {

    }
}
