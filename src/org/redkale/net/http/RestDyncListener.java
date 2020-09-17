/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.lang.reflect.Method;
import org.redkale.boot.Application;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;

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

    /**
     * 初始化方法
     *
     * @param config 配置参数
     */
    default void init(AnyValue config) {

    }

    /**
     * Application 初始化完所有Server后调用
     *
     * @param application Application
     */
    default void postApplicationStarted(Application application) {
    }

    /**
     * 动态生成RestMapping方法后调用
     *
     * @param classLoader     ClassLoader
     * @param baseServletType Rest的Servlet基类
     * @param serviceType     Service类
     * @param method          Service的RestMapping方法
     */
    public void invoke(final ClassLoader classLoader, final Class baseServletType,
        final Class<? extends Service> serviceType, final Method method);
}
