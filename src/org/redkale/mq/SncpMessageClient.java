/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import org.redkale.convert.ConvertType;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.1.0
 */
public class SncpMessageClient extends MessageClient {

    protected SncpMessageClient(MessageAgent messageAgent) {
        super(messageAgent);
        this.respTopic = messageAgent.generateSncpRespTopic();
        this.convertType = ConvertType.BSON;
    }
}
