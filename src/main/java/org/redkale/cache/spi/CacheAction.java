/*
 *
 */
package org.redkale.cache.spi;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.redkale.annotation.Nullable;
import org.redkale.annotation.Resource;
import org.redkale.cache.CacheManager;
import org.redkale.cache.Cached;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.sncp.Sncp;
import org.redkale.util.Environment;
import org.redkale.util.TypeToken;
import org.redkale.util.CombinedKey;

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
public class CacheAction {

    @Resource
    private Environment environment;

    @Resource
    private CacheManager manager;

    private final Cached cached;

    //Supplier对象的类型
    private final Type resultType;

    //对象是否异步
    private final boolean async;

    //是否可以缓存null
    private final boolean nullable;

    //宿主对象的类
    private final Class serviceClass;

    //无法获取动态的Method，只能存方法名
    private final String methodName;

    //方法参数类型
    @Nullable
    private final Class[] paramTypes;

    //方法参数名
    @Nullable
    private final String[] paramNames;

    //缓存的hash
    private String hash;

    //缓存的key
    private String key;

    //缓存的key
    private CombinedKey dynKey;

    //本地缓存过期时长
    private Duration localExpire;

    //远程缓存过期时长
    private Duration remoteExpire;

    CacheAction(Cached cached, Type returnType, Class serviceClass, Class[] paramTypes, String[] paramNames, String methodName) {
        this.cached = cached;
        this.nullable = cached.nullable();
        this.serviceClass = Objects.requireNonNull(serviceClass);
        this.paramTypes = paramTypes;
        this.paramNames = paramNames;
        this.methodName = Objects.requireNonNull(methodName);
        this.async = CompletableFuture.class.isAssignableFrom(TypeToken.typeToClass(returnType));
        this.resultType = this.async ? ((ParameterizedType) returnType).getActualTypeArguments()[0] : returnType;
    }

    void init() {
        this.hash = cached.hash().trim().isEmpty()
            ? Sncp.getResourceType(serviceClass).getSimpleName()
            : environment.getPropertyValue(cached.hash());
        this.key = environment.getPropertyValue(cached.key());
        this.dynKey = CombinedKey.create(paramTypes, paramNames, key);
        this.localExpire = createDuration(cached.localExpire());
        this.remoteExpire = createDuration(cached.remoteExpire());
    }

    public <T> T get(Supplier<T> supplier, Object... args) {
        if (async) {
            Supplier supplier0 = supplier;
            return (T) manager.bothGetAsync(hash, dynKey.keyFor(args), resultType, nullable, localExpire, remoteExpire, supplier0);
        } else {
            return manager.bothGet(hash, dynKey.keyFor(args), resultType, nullable, localExpire, remoteExpire, supplier);
        }
    }

    private Duration createDuration(String val) {
        String str = environment.getPropertyValue(val);
        if ("-1".equals(str) || "null".equalsIgnoreCase(str)) {
            return null;
        } else if ("0".equals(str)) {
            return Duration.ZERO;
        } else {
            return Duration.ofMillis(cached.timeUnit().toMillis(Long.parseLong(str)));
        }
    }

    @Override
    public String toString() {
        return "{"
            + "\"serviceClass\":" + serviceClass.getName()
            + ",\"methodName\":\"" + methodName + "\""
            + ",\"paramTypes\":" + JsonConvert.root().convertTo(paramTypes)
            + ",\"paramNames\":" + JsonConvert.root().convertTo(paramNames)
            + ",\"hash\":\"" + cached.hash() + "\""
            + ",\"key\":" + JsonConvert.root().convertTo(key)
            + ",\"localExpire\":\"" + cached.localExpire() + "\""
            + ",\"remoteExpire\":\"" + cached.remoteExpire() + "\""
            + ",\"timeUnit\":\"" + cached.timeUnit() + "\""
            + ",\"nullable\":" + cached.nullable()
            + "}";
    }

}
