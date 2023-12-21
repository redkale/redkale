/*
 *
 */
package org.redkale.cache.spi;

import org.redkale.convert.ConvertColumn;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * 缓存过期对象
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public class CacheExpire {

    //为0表示不过期
    @ConvertColumn(index = 1)
    protected long time;

    @ConvertDisabled
    public boolean isExpired() {
        return time > 0 && System.currentTimeMillis() > time;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
