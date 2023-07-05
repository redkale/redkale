/*
 */
package org.redkale.util;

import java.util.UUID;
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

    private static final boolean enable = Boolean.getBoolean("redkale.trace.enable");

    private static final ThreadLocal<String> localTrace = new ThreadLocal<>();

    private static final Supplier<String> tidSupplier = () -> UUID.randomUUID().toString().replace("-", "");

    public static boolean enable() {
        return enable;
    }

    public static String createTraceid() {
        return enable ? tidSupplier.get() : null;
    }

    public static String currentTraceid() {
        return enable ? localTrace.get() : null;
    }

    public static String computeIfAbsent(String requestTraceid) {
        if (enable) {
            String rs = requestTraceid;
            if (rs == null || rs.isEmpty()) {
                rs = tidSupplier.get();
            }
            localTrace.set(rs);
            return rs;
        }
        return requestTraceid;
    }

}
