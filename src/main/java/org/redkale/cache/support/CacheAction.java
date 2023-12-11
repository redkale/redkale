/*
 *
 */
package org.redkale.cache.support;

import java.lang.reflect.Method;
import org.redkale.convert.json.JsonConvert;

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

    private Method method;
    
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
