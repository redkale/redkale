/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.function.*;

/**
 * 没有返回值的异步接口
 *
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <A> 附件对象的泛型
 */
public interface AsyncVoidHandler< A> extends AsyncHandler<Void, A> {

    /**
     * 创建 AsyncVoidHandler 对象
     *
     * @param <A>     附件对象的泛型
     * @param success 成功的回调函数
     * @param fail    失败的回调函数
     *
     * @return AsyncHandler
     */
    public static < A> AsyncVoidHandler< A> create(final Consumer< A> success, final BiConsumer<Throwable, A> fail) {
        return new AsyncVoidHandler< A>() {
            @Override
            public void completed(Void result, A attachment) {
                if (success != null) success.accept(attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (fail != null) fail.accept(exc, attachment);
            }
        };
    }
}
