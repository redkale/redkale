/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import javax.net.ssl.SSLContext;
import org.redkale.util.*;

/**
 * 根据配置生成SSLContext
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface SSLCreator {

    default SSLContext create(Server server, AnyValue sslConf) throws IOException {

        return null;
    }
}
