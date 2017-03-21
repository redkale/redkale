/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.util.AsyncHandler;

/**
 * 异步回调函数
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <V> 结果对象的泛型
 * @param <A> 附件对象的泛型
 */
public abstract class SncpAsyncHandler<V, A> implements AsyncHandler<V, A> {

    protected Object[] params;

    public Object[] getParams() {
        return params;
    }

    public void setParams(Object... params) {
        this.params = params;
    }

}
