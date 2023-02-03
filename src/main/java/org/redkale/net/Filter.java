/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.IOException;
import org.redkale.annotation.Priority;
import org.redkale.util.AnyValue;

/**
 * 协议拦截器类, 类似JavaEE中的javax.servlet.Filter <br>
 * javax.servlet.Filter方法doFilter是同步操作，此Filter.doFilter则是异步操作，方法return前需要调用Response.nextEvent()方可执行下一个Filter <br>
 * 通过给Filter标记注解&#064;Priority来确定执行的顺序, Priority.value值越大越先执行
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <C> Context的子类型
 * @param <R> Request的子类型
 * @param <P> Response的子类型
 */
public abstract class Filter<C extends Context, R extends Request<C>, P extends Response<C, R>> implements Comparable {

    AnyValue _conf; //当前Filter的配置

    Filter<C, R, P> _next; //下一个Filter

    public void init(C context, AnyValue config) {
    }

    public abstract void doFilter(R request, P response) throws IOException;

    public abstract boolean isNonBlocking();

    public void destroy(C context, AnyValue config) {
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof Filter)) {
            return 1;
        }
        Priority p1 = this.getClass().getAnnotation(Priority.class);
        Priority p2 = o.getClass().getAnnotation(Priority.class);
        return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
    }
}
