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
     * 初始化配置源，配置项需要写入envProperties，并监听配置项的变化
     *
     * @param application Application
     * @param conf        节点配置
     */
    public abstract void init(Application application, AnyValue conf);

    /**
     * 销毁动作
     *
     * @param conf 节点配置
     */
    public abstract void destroy(AnyValue conf);

    protected void putEnvironmentProperties(Application application, Properties props) {
        Properties notifyCache = new Properties();
        props.forEach((k, v) -> application.putEnvironmentProperty(k.toString(), v, notifyCache));
        application.resourceFactory.register(notifyCache);
    }

    protected void putEnvironmentProperty(Application application, String key, Object value) {
        application.putEnvironmentProperty(key, value, null);
    }

    protected void reconfigLogging(Application application, Properties loggingProperties) {
        application.reconfigLogging(loggingProperties);
    }
}
