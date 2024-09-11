/*

*/

package org.redkale.cached.spi;

import java.io.Serializable;
import org.redkale.convert.json.JsonConvert;

/**
 * 缓存推送的消息对象
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 */
public class CachedEventMessage implements Serializable {

    // CachedManager唯一标识
    protected String node;

    // name
    protected String name;
    // key
    protected String key;

    // 时间
    protected long time;

    public CachedEventMessage() {}

    public CachedEventMessage(String node, String name, String key) {
        this.node = node;
        this.name = name;
        this.key = key;
        this.time = System.currentTimeMillis();
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
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
