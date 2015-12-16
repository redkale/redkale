/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 被标记为 @WebSocketBinary 的WebSocketServlet 将使用原始的TCP传输,  通常用于类似音频/视频传输场景
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
public @interface WebSocketBinary {

}
