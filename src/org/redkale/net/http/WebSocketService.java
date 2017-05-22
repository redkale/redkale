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
 * 与RestService结合使用
 * <br><p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public interface WebSocketService {

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

        String comment() default ""; //备注描述
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

        String comment() default ""; //备注描述
    }

    /**
     * 标记在WebSocketService的方法上 <br>
     * 方法的返回值必须是CompletableFuture&lt;Serializable&gt;, 且方法没有参数
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
    public @interface RestCreateGroup {

        String comment() default ""; //备注描述
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
    public @interface RestOnConnected {

        String comment() default ""; //备注描述
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
    public @interface RestOnClose {

        String comment() default ""; //备注描述
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
    public @interface RestOnPing {

        String comment() default ""; //备注描述
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
    public @interface RestOnPong {

        String comment() default ""; //备注描述
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

        String comment() default ""; //备注描述
    }

    /**
     * 标记在WebSocketService的方法的String参数上
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
    public @interface RestClientAddress {

        String comment() default ""; //备注描述
    }

    /**
     * 标记在WebSocketService的RestOnMessage方法的Serializable参数上
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
    public @interface RestGroupid {

        String comment() default ""; //备注描述
    }

    /**
     * 标记在WebSocketService的方法的Serializable参数上
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
    public @interface RestSessionid {

        String comment() default ""; //备注描述
    }
}
