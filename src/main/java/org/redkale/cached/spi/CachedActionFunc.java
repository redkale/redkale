/*

*/

package org.redkale.cached.spi;

/**
 * 增加 {@link org.redkale.cached.spi.CachedAction}， {@link org.redkale.cached.CachedManager}的实现类也必须实现本接口
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface CachedActionFunc {
    void addAction(CachedAction action);
}
