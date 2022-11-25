/*
 */
package org.redkale.boot;

import java.util.Properties;
import java.util.logging.Logger;
import org.redkale.util.*;

/**
 * 配置源Agent, 在init方法内需要实现读取配置信息，如果支持配置更改通知，也需要在init里实现监听
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.7.0
 */
public abstract class PropertiesAgent {

    public static final String PROP_KEY_NAMESPACE = "namespace";

    protected final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    /**
     * 编译时进行的操作
     *
     * @param conf 节点配置
     */
    public void compile(AnyValue conf) {
    }

    /**
     * ServiceLoader时判断配置是否符合当前实现类
     *
     * @param config 节点配置
     *
     * @return boolean
     */
    public abstract boolean acceptsConf(AnyValue config);

    /**
     * 初始化配置源，配置项需要写入appProperties，并监听配置项的变化
     *
     * @param factory          依赖注入资源工厂
     * @param appProperties    全局property.开头的配置项
     * @param sourceProperties 全局source数据源的配置项
     * @param conf             节点配置
     */
    public abstract void init(ResourceFactory factory, Properties appProperties, Properties sourceProperties, AnyValue conf);

    /**
     * 销毁动作
     *
     * @param conf 节点配置
     */
    public abstract void destroy(AnyValue conf);

    protected String getKeyResourceName(String key) {
        return key.startsWith("redkale.")
            || key.startsWith("property.")
            || key.startsWith("system.property.")
            || key.startsWith("mimetype.property.") ? key : ("property." + key);
    }

    protected void putProperties(Properties appProperties, Properties sourceProperties, Properties newProps) {
        newProps.forEach((k, v) -> putProperties(appProperties, sourceProperties, k.toString(), v));
    }

    protected void putProperties(Properties appProperties, Properties sourceProperties, String key, Object value) {
        if (key.startsWith("redkale.datasource.") || key.startsWith("redkale.datasource[")
            || key.startsWith("redkale.cachesource.") || key.startsWith("redkale.cachesource[")) {
            sourceProperties.put(key, value);
        }
        appProperties.put(key, value);
    }
}
