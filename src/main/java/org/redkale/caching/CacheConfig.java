/*
 *
 */
package org.redkale.caching;

import org.redkale.convert.json.JsonConvert;

/**
 *
 * 缓存配置
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class CacheConfig {

    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
