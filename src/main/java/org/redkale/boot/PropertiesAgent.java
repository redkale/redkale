/*
 */
package org.redkale.boot;

import java.util.Properties;
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

    public static final String PROP_KEY_URL = "url";

    public static final String PROP_KEY_NAMESPACE = "namespace";

    public static final String PROP_NAMESPACE_APPLICATION = "application";

    /**
     * 编译时进行的操作
     *
     * @param conf 节点配置
     */
    public void compile(AnyValue conf) {
    }

    /**
     * 初始化配置源，配置项需要写入appProperties，并监听配置项的变化
     *
     * @param factory       依赖注入资源工厂
     * @param appProperties 全局property.开头的配置项
     * @param conf          节点配置
     */
    public abstract void init(ResourceFactory factory, Properties appProperties, AnyValue conf);

    /**
     * 销毁动作
     *
     * @param conf 节点配置
     */
    public abstract void destroy(AnyValue conf);

    protected String getKeyResourceName(String key) {
        return key.startsWith("system.property.") || key.startsWith("property.") ? key : ("property." + key);
    }
}
