/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.util.logging.Logger;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class SncpMessageProcessor implements MessageProcessor {

    protected final Logger logger;

    protected final MessageAgent agent;

    public SncpMessageProcessor(Logger logger, MessageAgent agent) {
        this.logger = logger;
        this.agent = agent;
    }

    @Override
    public void process(MessageRecord message) {

    }
}
