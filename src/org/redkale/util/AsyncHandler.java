/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.function.BiConsumer;
import java.nio.channels.CompletionHandler;
import java.util.function.*;

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
public interface AsyncHandler<V, A> extends CompletionHandler<V, A> {

    /**
     * 创建 AsyncHandler 对象
     *
     * @param <V>     结果对象的泛型
     * @param <A>     附件对象的泛型
     * @param success 成功的回调函数
     * @param fail    失败的回调函数
     *
     * @return AsyncHandler
     */
    public static <V, A> AsyncHandler<V, A> create(final BiConsumer<V, A> success, final BiConsumer<Throwable, A> fail) {
        return new AsyncHandler<V, A>() {
            @Override
            public void completed(V result, A attachment) {
                if (success != null) success.accept(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (fail != null) fail.accept(exc, attachment);
            }
        };
    }

    /**
     * 创建 AsyncHandler 对象
     *
     * @param <V>     结果对象的泛型
     * @param <A>     附件对象的泛型
     * @param success 成功的回调函数
     * @param fail    失败的回调函数
     * @param handler 子回调函数
     *
     * @return AsyncHandler
     */
    public static <V, A> AsyncHandler<V, A> create(final BiConsumer<V, A> success, final BiConsumer<Throwable, A> fail, final AsyncHandler<V, A> handler) {
        return new AsyncHandler<V, A>() {
            @Override
            public void completed(V result, A attachment) {
                if (success != null) success.accept(result, attachment);
                if (handler != null) handler.completed(result, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (fail != null) fail.accept(exc, attachment);
                if (handler != null) handler.failed(exc, attachment);
            }
        };
    }

    /**
     * 创建没有返回结果的 AsyncHandler 对象
     *
     * @param <A>     附件对象的泛型
     * @param success 成功的回调函数
     * @param fail    失败的回调函数
     *
     * @return AsyncHandler
     */
    public static <A> AsyncHandler<Void, A> createNoResultHandler(final Consumer<A> success, final BiConsumer<Throwable, A> fail) {
        return new AsyncHandler<Void, A>() {
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

    /**
     * 创建没有附件对象的 AsyncNoResultHandler 对象
     *
     * @param <V>     结果对象的泛型
     * @param success 成功的回调函数
     * @param fail    失败的回调函数
     *
     * @return AsyncHandler
     */
    public static <V> AsyncHandler<V, Void> createNoAttachHandler(final Consumer<V> success, final Consumer<Throwable> fail) {
        return new AsyncHandler<V, Void>() {
            @Override
            public void completed(V result, Void attachment) {
                if (success != null) success.accept(result);
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (fail != null) fail.accept(exc);
            }
        };
    }

}
