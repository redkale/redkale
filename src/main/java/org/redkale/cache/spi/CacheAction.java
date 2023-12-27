/*
 *
 */
package org.redkale.cache.spi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.asm.AsmDepends;
import org.redkale.cache.CacheManager;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Environment;
import org.redkale.util.MultiHashKey;
import org.redkale.util.ThrowSupplier;
import org.redkale.util.TypeToken;

/**
 *
 * 缓存的方法对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
@AsmDepends
public class CacheAction {

    @Resource
    private Environment environment;

    @Resource
    private CacheManager manager;

    private final CacheEntry cached;

    //Supplier对象的类型
    private final Type resultType;

    //缓存方法是否异步
    private final boolean async;

    //是否可以缓存null
    private final boolean nullable;

    //宿主对象的类
    private final Class serviceClass;

    //无法获取动态的Method，只能存方法名
    private final String methodName;

    //获取动态的字段名
    private final String fieldName;

    //方法参数类型
    @Nullable
    private final Class[] paramTypes;

    //方法参数名
    @Nullable
    private final String[] paramNames;

    //缓存的hash
    private String hash;

    //缓存的key
    private MultiHashKey dynKey;

    //本地缓存过期时长，Duration.ZERO为永不过期，为null表示不本地缓存
    private Duration localExpire;

    //远程缓存过期时长，Duration.ZERO为永不过期，为null表示不远程缓存
    private Duration remoteExpire;

    CacheAction(CacheEntry cached, Type returnType, Class serviceClass, Class[] paramTypes,
        String[] paramNames, String methodName, String fieldName) {
        this.cached = cached;
        this.nullable = cached.isNullable();
        this.serviceClass = Objects.requireNonNull(serviceClass);
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.methodName = Objects.requireNonNull(methodName);
        this.fieldName = Objects.requireNonNull(fieldName);
        this.async = CompletableFuture.class.isAssignableFrom(TypeToken.typeToClass(returnType));
        this.resultType = this.async ? ((ParameterizedType) returnType).getActualTypeArguments()[0] : returnType;
    }

    void init() {
        this.hash = cached.getHash().trim().isEmpty()
            || CacheManager.DEFAULT_HASH.equals(cached.getHash())
            ? CacheManager.DEFAULT_HASH
            : environment.getPropertyValue(cached.getHash());
        String key = environment.getPropertyValue(cached.getKey());
        this.dynKey = MultiHashKey.create(paramNames, key);
        this.localExpire = createDuration(cached.getLocalExpire());
        this.remoteExpire = createDuration(cached.getRemoteExpire());
    }

    @AsmDepends
    public <T> T get(ThrowSupplier<T> supplier, Object... args) {
        if (async) {
            ThrowSupplier supplier0 = supplier;
            return (T) manager.bothGetSetAsync(hash, dynKey.keyFor(args), resultType, nullable, localExpire, remoteExpire, supplier0);
        } else {
            return manager.bothGetSet(hash, dynKey.keyFor(args), resultType, nullable, localExpire, remoteExpire, supplier);
        }
    }

    private Duration createDuration(String val) {
        String str = environment.getPropertyValue(val);
        if ("-1".equals(str) || "null".equalsIgnoreCase(str)) {
            return null;
        } else if ("0".equals(str)) {
            return Duration.ZERO;
        } else {
            return Duration.ofMillis(cached.getTimeUnit().toMillis(Long.parseLong(str)));
        }
    }

    @Override
    public String toString() {
        return "{"
            + "\"serviceClass\":" + serviceClass.getName()
            + ",\"methodName\":\"" + methodName + "\""
            + ",\"fieldName\":\"" + fieldName + "\""
            + ",\"paramTypes\":" + JsonConvert.root().convertTo(paramTypes)
            + ",\"paramNames\":" + JsonConvert.root().convertTo(paramNames)
            + ",\"resultType\":\"" + resultType + "\""
            + ",\"cache\":" + cached
            + "}";
    }

}
