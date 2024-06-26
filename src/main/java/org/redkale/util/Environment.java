/*
 */
package org.redkale.util;

import java.util.*;
import java.util.function.*;

/**
 * 环境变量, 只读版Properties
 * 只存放system.property.、mimetype.property.、redkale.cachesource(.|[)、redkale.datasource(.|[)和其他非redkale.开头的配置项
 * 只有ResourceFactory.register(Properties properties, String environmentName, Class environmentType)
 * 方法才能是Environment的ResourceChanged起作用
 *
 * <p>详情见: https://redkale.org
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

    public Properties newProperties() {
        return new Properties(properties);
    }

    public Set<String> keySet() {
        return (Set) properties.keySet();
    }

    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    public void forEach(BiConsumer<String, String> action) {
        properties.forEach((BiConsumer) action);
    }

    public void forEach(Predicate<String> predicate, BiConsumer<String, String> action) {
        properties.entrySet().stream()
                .filter(en -> predicate.test(en.getKey().toString()))
                .forEach(en -> action.accept(en.getKey().toString(), String.valueOf(en.getValue())));
    }

    public String getProperty(String name) {
        return getProperty(name, null);
    }

    public String getProperty(String name, String defaultValue) {
        return getPropertyValue(getRawProperty(name, defaultValue));
    }

    public String getPropertyValue(String val, Properties... envs) {
        if (val == null || val.isBlank()) {
            return val;
        }
        char last = 0;
        char[] chars = val.toCharArray();
        int startIndex = -1;
        int endIndex = -1;
        for (int i = 0; i < chars.length; i++) {
            char ch = chars[i];
            if (ch == '{' && last == '$') {
                startIndex = i - 1;
            } else if (last != '\\' && ch == '}' && startIndex >= 0) {
                endIndex = i;
                break;
            }
            last = ch;
        }

        // ${domain}/${path}/xxx    ${aa${bbb}}
        // school_#{name}_${haha_${age}}_${bb}_#{dd}  -> school_#{name}_xxx_xxx_#{dd}
        if (startIndex >= 0 && endIndex > 0) {
            String key = val.substring(startIndex + 2, endIndex);
            int pos3 = key.lastIndexOf(':');
            String defVal = null;
            if (pos3 > 0) {
                key = key.substring(0, pos3);
                defVal = key.substring(pos3 + 1);
                if ("null".equals(defVal)) {
                    defVal = null;
                }
            }
            String subVal = properties.getProperty(key);
            if (subVal != null) {
                String newVal = getPropertyValue(subVal, envs);
                newVal = val.substring(0, startIndex) + newVal + val.substring(endIndex + 1);
                return endIndex == chars.length - 1 ? newVal : getPropertyValue(newVal);
            } else {
                for (Properties prop : envs) {
                    subVal = prop.getProperty(key);
                    if (subVal != null) {
                        String newVal = getPropertyValue(subVal, envs);
                        newVal = val.substring(0, startIndex) + newVal + val.substring(endIndex + 1);
                        return endIndex == chars.length - 1 ? newVal : getPropertyValue(newVal);
                    }
                }
                if (pos3 > 0) {
                    return defVal;
                }
                throw new RedkaleException("Not found '" + key + "' value");
            }
        }
        return val;
    }

    public AnyValue getAnyValue(String name, boolean autoCreated) {
        Properties props = new Properties();
        String prefix = name + ".";
        properties.forEach((k, v) -> {
            if (k.toString().equals(name) || k.toString().startsWith(prefix)) {
                props.put(k, getProperty(k.toString()));
            }
        });
        if (props.isEmpty()) {
            return autoCreated ? AnyValueWriter.create() : null;
        }
        return AnyValueWriter.loadFromProperties(props);
    }

    public String getRawProperty(String key) {
        return getRawProperty(key, null);
    }

    public String getRawProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public boolean getBooleanProperty(String key) {
        String val = getProperty(key);
        return "true".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val);
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(val) || "1".equalsIgnoreCase(val);
    }

    public short getShortProperty(String key) {
        return Short.parseShort(getProperty(key));
    }

    public short getShortProperty(String key, short defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Short.parseShort(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getIntProperty(String key) {
        return Integer.parseInt(getProperty(key));
    }

    public int getIntProperty(String key, int defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public float getFloatProperty(String key) {
        return Float.parseFloat(getProperty(key));
    }

    public float getFloatProperty(String key, float defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLongProperty(String key) {
        return Long.parseLong(getProperty(key));
    }

    public long getLongProperty(String key, long defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public double getDoubleProperty(String key) {
        return Double.parseDouble(getProperty(key));
    }

    public double getDoubleProperty(String key, double defaultValue) {
        String val = getProperty(key);
        if (val == null || val.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return properties.toString();
    }
}
