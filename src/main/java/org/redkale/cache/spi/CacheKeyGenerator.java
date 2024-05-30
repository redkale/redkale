/*

*/

package org.redkale.cache.spi;

/**
 * 缓存key生成器
 *
 * @see org.redkale.cache.Cached#key()
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface CacheKeyGenerator {

    /**
     * 根据service和方法名生成key
     *
     * @param target Service对象
     * @param action CacheAction对象
     * @param params 参数值
     * @return key值
     */
    public String generate(Object target, CacheAction action, Object... params);

    /**
     * 生成器的名字
     *
     * @see org.redkale.cache.Cached#key()
     *
     * @return  name
     */
    default String name() {
        return "";
    }
}
