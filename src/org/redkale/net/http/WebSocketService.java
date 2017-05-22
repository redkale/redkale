/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.http;

import java.io.Serializable;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.concurrent.CompletableFuture;

/**
 * 与RestService结合使用
 * <br><p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface WebSocketService {

    public CompletableFuture<Serializable> createGroupid(final WebSocketId wsid);

    default void onConnected(final WebSocketId wsid) {
    }

    default void onPing(final WebSocketId wsid, final byte[] bytes) {
    }

    default void onPong(final WebSocketId wsid, final byte[] bytes) {
    }

    default void onClose(final WebSocketId wsid, final int code, final String reason) {
    }

    /**
     * 标记在WebSocketService的消息接收方法上
     *
     * <br><p>
     * 详情见: https://redkale.org
     *
     * @author zhangjx
     */
    @Inherited
    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    public @interface RestOnMessage {

        /**
         * 请求的方法名, 不能含特殊字符
         *
         * @return String
         */
        String name() default "";

        /**
         * 备注描述
         *
         * @return String
         */
        String comment() default "";
    }

    /**
     * 标记在WebSocketService的方法上
     *
     * <br><p>
     * 详情见: https://redkale.org
     *
     * @author zhangjx
     */
    @Inherited
    @Documented
    @Target({METHOD})
    @Retention(RUNTIME)
    public @interface RestOnOpen {

        /**
         * 备注描述
         *
         * @return String
         */
        String comment() default "";
    }

    /**
     * 标记在WebSocketService的RestOnMessage方法的boolean参数上
     *
     * <br><p>
     * 详情见: https://redkale.org
     *
     * @author zhangjx
     */
    @Inherited
    @Documented
    @Target({PARAMETER})
    @Retention(RUNTIME)
    public @interface RestMessageLast {
    }

}
