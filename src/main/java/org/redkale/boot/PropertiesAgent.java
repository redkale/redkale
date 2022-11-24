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

    public void compile(AnyValue conf) {
    }

    public abstract void init(ResourceFactory factory, Properties appProperties, AnyValue conf);

    public abstract void destroy(AnyValue conf);

    protected String getKeyResourceName(String key) {
        return key.startsWith("property.") ? key : ("property." + key);
    }
}
