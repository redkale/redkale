/*

*/

package org.redkale.cached.spi;

import java.util.Objects;
import org.redkale.util.MultiHashKey;

/**
 * 缓存key生成器
 *
 * @see org.redkale.cached.Cached#key()
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface CachedKeyGenerator {

    /**
     * 根据service和方法名生成key
     *
     * @param target 依附对象
     * @param action {@link org.redkale.cached.spi.CachedAction}对象
     * @param params 参数值
     * @return key值
     */
    public String generate(Object target, CachedAction action, Object... params);

    /**
     * 生成器的名字
     *
     * @see org.redkale.cached.Cached#key()
     *
     * @return  key
     */
    public String key();

    /**
     * 根据MultiHashKey生成一个CachedKeyGenerator
     * @param key {@link org.redkale.util.MultiHashKey} 不能为空
     * @return CachedKeyGenerator
     */
    public static CachedKeyGenerator create(MultiHashKey key) {
        Objects.requireNonNull(key);
        return new CachedKeyGenerator() {
            @Override
            public String generate(Object target, CachedAction action, Object... params) {
                return key.keyFor(params);
            }

            @Override
            public String key() {
                return "";
            }
        };
    }
}
