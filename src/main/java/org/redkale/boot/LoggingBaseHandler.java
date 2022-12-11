/*
 */
package org.redkale.boot;

import java.util.logging.*;
import org.redkale.util.Traces;

/**
 * Handler基类
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public abstract class LoggingBaseHandler extends Handler {

    static boolean traceflag = false; //防止设置system.property前调用Traces类导致enable提前初始化

    protected static void fillLogRecord(LogRecord log) {
        if (traceflag && Traces.enable()) {
            String traceid = Traces.currTraceid();
            if (traceid == null || traceid.isEmpty()) {
                traceid = "[TID:N/A] ";
            } else {
                traceid = "[TID:" + traceid + "] ";
            }
            if (log.getMessage() == null) {
                log.setMessage(traceid);
            } else {
                log.setMessage(traceid + log.getMessage());
            }
        }
    }
}
