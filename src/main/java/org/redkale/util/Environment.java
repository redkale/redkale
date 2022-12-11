/*
 */
package org.redkale.util;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * 环境变量, 只读版Properties
 * 只存放system.property.、mimetype.property.、redkale.cachesource(.|[)、redkale.datasource(.|[)和其他非redkale.开头的配置项
 * 只有ResourceFactory.register(Properties properties, String environmentName, Class environmentType) 方法才能是Environment的ResourceListener起作用
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

    public boolean getBooleanProperty(String key) {
        String val = properties.getProperty(key);
        return "true".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val);
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.isEmpty()) return defaultValue;
        return "true".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val);
    }

    public short getShortProperty(String key) {
        return Short.parseShort(properties.getProperty(key));
    }

    public short getShortProperty(String key, short defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Short.parseShort(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getIntProperty(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public int getIntProperty(String key, int defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLongProperty(String key) {
        return Long.parseLong(properties.getProperty(key));
    }

    public long getLongProperty(String key, long defaultValue) {
        String val = properties.getProperty(key);
        if (val == null || val.isEmpty()) return defaultValue;
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return properties.toString();
    }
}
