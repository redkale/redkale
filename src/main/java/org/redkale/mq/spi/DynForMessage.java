/*
 *
 */
package org.redkale.mq.spi;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.redkale.mq.MessageConsumer;

/**
 * 只标准在类上面，因动态方法不作变动，只增加内部类
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
@Repeatable(DynForMessage.DynForMessages.class)
public @interface DynForMessage {

    Class<? extends MessageConsumer> value();

    @Inherited
    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    @interface DynForMessages {

        DynForMessage[] value();
    }
}
