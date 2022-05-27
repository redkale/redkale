/*
 */
package org.redkale.util;

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

    private static final boolean disabled = !Boolean.getBoolean("redkale.trace.enable");

    private static ThreadLocal<String> localTrace = new ThreadLocal<>();

    public static boolean enable() {
        return !disabled;
    }

    public static String onceTraceid() {
        return disabled ? null : Utility.uuid();
    }

    public static String createTraceid() {
        if (disabled) return null;
        String traceid = localTrace.get();
        if (traceid == null) {
            traceid = Utility.uuid();
            localTrace.set(traceid);
        }
        return traceid;
    }

    public static void currTraceid(String traceid) {
        localTrace.set(traceid);
    }

    public static String currTraceid() {
        return disabled ? null : localTrace.get();
    }

}
