/*
 */
package org.redkale.util;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 环境变量, 只读版Properties
 * 只存放system.property.、mimetype.property.、redkale.cachesource(.|[)、redkale.datasource(.|[)和其他非redkale.开头的配置项
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.7.0
 */
public class Environment implements java.io.Serializable {

    private final Properties properties;

    public Environment() {
        this(new Properties());
    }

    public Environment(Properties properties) {
        this.properties = properties;
    }

    public Set<String> keySet() {
        return (Set) properties.keySet();
    }

    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void forEach(BiConsumer<String, String> action) {
        properties.forEach((BiConsumer) action);
    }

    @Override
    public String toString() {
        return properties.toString();
    }
}
