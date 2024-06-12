/*

*/

package org.redkale.service;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 错误码加载器
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public abstract class RetCodes {

    private static final ReentrantLock loadLock = new ReentrantLock();

    private static Map<String, Map<Integer, String>> rets = new LinkedHashMap<>();

    private static Map<Integer, String> defret = new LinkedHashMap<>();

    protected RetCodes() {
        throw new IllegalStateException();
    }

    public static void load(Class codeClass) {
        load(RetLabel.RetLoader.loadMap(codeClass));
    }

    public static void load(Map<String, Map<Integer, String>> map) {
        if (map.isEmpty()) {
            return;
        }
        loadLock.lock();
        try {
            Map<String, Map<Integer, String>> newMap = new LinkedHashMap<>();
            rets.forEach((k, v) -> newMap.put(k, new LinkedHashMap<>(v)));
            map.forEach((k, v) -> {
                Map<Integer, String> m = newMap.get(k);
                if (m != null) {
                    m.putAll(v);
                } else {
                    newMap.put(k, v);
                }
            });
            rets = newMap;
            defret = rets.get("");
        } finally {
            loadLock.unlock();
        }
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
        if (args == null || args.length < 1) {
            return new RetResult(retcode, retInfo(retcode));
        }
        String info = MessageFormat.format(retInfo(retcode), args);
        return new RetResult(retcode, info);
    }

    public static RetResult retResult(String locale, int retcode, Object... args) {
        if (retcode == 0) {
            return RetResult.success();
        }
        if (args == null || args.length < 1) {
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
        if (args == null || args.length < 1) {
            return result.retcode(retcode).retinfo(retInfo(retcode));
        }
        String info = MessageFormat.format(retInfo(retcode), args);
        return result.retcode(retcode).retinfo(info);
    }

    public static RetResult retInfo(RetResult result, String locale, int retcode, Object... args) {
        if (retcode == 0) {
            return result.retcode(0).retinfo("");
        }
        if (args == null || args.length < 1) {
            return result.retcode(retcode).retinfo(retInfo(locale, retcode));
        }
        String info = MessageFormat.format(retInfo(locale, retcode), args);
        return result.retcode(retcode).retinfo(info);
    }

    public static String retInfo(int retcode) {
        if (retcode == 0) {
            return "Success";
        }
        return defret.getOrDefault(retcode, "Error");
    }

    public static String retInfo(String locale, int retcode) {
        if (locale == null || locale.isEmpty()) {
            return retInfo(retcode);
        }
        if (retcode == 0) {
            return "Success";
        }
        Map<Integer, String> map = rets.get(locale);
        if (map == null) {
            return "Error";
        }
        return map.getOrDefault(retcode, "Error");
    }
}
