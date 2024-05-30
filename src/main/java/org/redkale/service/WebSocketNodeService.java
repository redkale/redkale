/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.util.logging.Level;
import org.redkale.annotation.*;
import org.redkale.net.http.WebSocketNode;
import org.redkale.util.AnyValue;

/**
 * 由 {@link org.redkale.net.http.WebSocketNodeService} 代替
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.net.http.WebSocketNodeService
 * @deprecated 2.6.0
 * @author zhangjx
 */
@Deprecated(since = "2.6.0")
@AutoLoad(false)
@ResourceType(WebSocketNode.class)
public class WebSocketNodeService extends org.redkale.net.http.WebSocketNodeService {

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
        logger.log(
                Level.WARNING,
                WebSocketNodeService.class.getName() + "is replaced by "
                        + org.redkale.net.http.WebSocketNodeService.class.getName());
    }
}
