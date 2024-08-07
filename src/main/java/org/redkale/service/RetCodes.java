/*

*/

package org.redkale.service;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.util.Utility;

/**
 * 错误码加载器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class RetCodes {

    protected RetCodes() {
        throw new IllegalStateException();
    }

    public static int load(Class codeClass) {
        return load(RetInnerCache.loadMap(codeClass));
    }

    public static int load(Map<String, Map<Integer, String>> map) {
        if (map.isEmpty()) {
            return 0;
        }
        AtomicInteger counter = new AtomicInteger();
        RetInnerCache.loadLock.lock();
        try {
            Map<String, Map<Integer, String>> newMap = new LinkedHashMap<>();
            RetInnerCache.allRets.forEach((k, v) -> newMap.put(k, new LinkedHashMap<>(v)));
            map.forEach((k, v) -> {
                newMap.computeIfAbsent(k, n -> new LinkedHashMap<>()).putAll(v);
                counter.addAndGet(v.size());
            });
            RetInnerCache.allRets = newMap;
            RetInnerCache.defRets = newMap.get("");
        } finally {
            RetInnerCache.loadLock.unlock();
        }
        return counter.get();
    }

    public static RetResult retResult(int retcode) {
        if (retcode == 0) {
            return RetResult.success();
        }
        return new RetResult(retcode, retInfo(retcode));
    }

    public static RetResult retResult(String locale, int retcode) {
        if (retcode == 0) {
            return RetResult.success();
        }
        return new RetResult(retcode, retInfo(locale, retcode));
    }

    public static RetResult retResult(int retcode, Object... args) {
        if (retcode == 0) {
            return RetResult.success();
        }
        if (Utility.isEmpty(args)) {
            return new RetResult(retcode, retInfo(retcode));
        }
        String info = MessageFormat.format(retInfo(retcode), args);
        return new RetResult(retcode, info);
    }

    public static RetResult retResult(String locale, int retcode, Object... args) {
        if (retcode == 0) {
            return RetResult.success();
        }
        if (Utility.isEmpty(args)) {
            return new RetResult(retcode, retInfo(locale, retcode));
        }
        String info = MessageFormat.format(retInfo(locale, retcode), args);
        return new RetResult(retcode, info);
    }

    public static <T> CompletableFuture<RetResult<T>> retResultFuture(int retcode) {
        return CompletableFuture.completedFuture(retResult(retcode));
    }

    public static <T> CompletableFuture<RetResult<T>> retResultFuture(String locale, int retcode) {
        return CompletableFuture.completedFuture(retResult(locale, retcode));
    }

    public static <T> CompletableFuture<RetResult<T>> retResultFuture(int retcode, Object... args) {
        return CompletableFuture.completedFuture(retResult(retcode, args));
    }

    public static <T> CompletableFuture<RetResult<T>> retResultFuture(String locale, int retcode, Object... args) {
        return CompletableFuture.completedFuture(retResult(locale, retcode, args));
    }

    public static RetResult retInfo(RetResult result, int retcode, Object... args) {
        if (retcode == 0) {
            return result.retcode(0).retinfo("");
        }
        if (Utility.isEmpty(args)) {
            return result.retcode(retcode).retinfo(retInfo(retcode));
        }
        String info = MessageFormat.format(retInfo(retcode), args);
        return result.retcode(retcode).retinfo(info);
    }

    public static RetResult retInfo(RetResult result, String locale, int retcode, Object... args) {
        if (retcode == 0) {
            return result.retcode(0).retinfo("");
        }
        if (Utility.isEmpty(args)) {
            return result.retcode(retcode).retinfo(retInfo(locale, retcode));
        }
        String info = MessageFormat.format(retInfo(locale, retcode), args);
        return result.retcode(retcode).retinfo(info);
    }

    public static String retInfo(int retcode) {
        if (retcode == 0) {
            return "Success";
        }
        return RetInnerCache.defRets.getOrDefault(retcode, "Error");
    }

    public static String retInfo(String locale, int retcode) {
        if (locale == null || locale.isEmpty()) {
            return retInfo(retcode);
        }
        if (retcode == 0) {
            return "Success";
        }
        Map<Integer, String> map = RetInnerCache.allRets.get(locale);
        if (map == null) {
            return "Error";
        }
        return map.getOrDefault(retcode, "Error");
    }
}
