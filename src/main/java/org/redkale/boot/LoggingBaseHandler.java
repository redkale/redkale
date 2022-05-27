/*
 */
package org.redkale.boot;

import java.util.logging.Handler;

/**
 * Handler基类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public abstract class LoggingBaseHandler extends Handler {

    protected Application currentApplication() {
        return Application.currentApplication; //不能直接暴露外界访问
    }
}
