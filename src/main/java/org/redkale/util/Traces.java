/*
 */
package org.redkale.util;

import java.util.function.Supplier;

/**
 * 创建traceid工具类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class Traces {

    private static final boolean enable = !Boolean.getBoolean("redkale.trace.enable");

    private static final ThreadLocal<String> localTrace = new ThreadLocal<>();

    private static final Supplier<String> tidSupplier = () -> Utility.uuid();

    public static boolean enable() {
        return enable;
    }

    public static String onceTraceid() {
        return enable ? tidSupplier.get() : null;
    }

    public static String loadTraceid() {
        if (!enable) return null;
        String traceid = localTrace.get();
        if (traceid == null) {
            traceid = tidSupplier.get();
            localTrace.set(traceid);
        }
        return traceid;
    }

    public static void currTraceid(String traceid) {
        if (enable) {
            localTrace.set(traceid);
        }
    }

    public static String currTraceid() {
        return enable ? localTrace.get() : null;
    }

}
