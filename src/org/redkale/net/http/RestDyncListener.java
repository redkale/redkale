/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.reflect.Method;
import org.redkale.service.Service;

/**
 * Application进程启动后动态创建RestServlet时调用 <br>
 * 只能通过application.xml配置
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.2.0
 */
public interface RestDyncListener {

    public void invoke(final ClassLoader classLoader, final Class baseServletType,
        final Class<? extends Service> serviceType, final Method method);
}
