/*
 *
 */
package org.redkale.mq.spi;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import org.redkale.mq.MessageConsumer;

/**
 * 用于识别方法是否已经动态处理过
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({TYPE})
@Retention(RUNTIME)
@Repeatable(DynForMessaged.DynForMessageds.class)
public @interface DynForMessaged {

    Class<? extends MessageConsumer> value();

    @Inherited
    @Documented
    @Target({TYPE})
    @Retention(RUNTIME)
    @interface DynForMessageds {

        DynForMessaged[] value();
    }
}
