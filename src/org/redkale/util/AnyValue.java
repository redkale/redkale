/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiPredicate;

/**
 * 该类主要用于读取xml配置文件
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public interface AnyValue {

    /**
     * 可读写的AnyValue默认实现类
     *
     * @author zhangjx
     */
    @SuppressWarnings("unchecked")
    public static final class DefaultAnyValue implements AnyValue {

        public static final BiPredicate<String, String> EQUALS = (String name1, String name2) -> name1.equals(name2);

        public static final BiPredicate<String, String> EQUALSIGNORE = (String name1, String name2) -> name1.equalsIgnoreCase(name2);

        private final BiPredicate<String, String> predicate;

        private Entry<String>[] stringValues = new Entry[0];

        private Entry<AnyValue>[] entityValues = new Entry[0];

        public static final DefaultAnyValue create() {
            return new DefaultAnyValue();
        }

        public static final DefaultAnyValue create(String name, String value) {
            DefaultAnyValue conf = new DefaultAnyValue();
            conf.addValue(name, value);
            return conf;
        }

        public static final DefaultAnyValue create(String name, AnyValue value) {
            DefaultAnyValue conf = new DefaultAnyValue();
            conf.addValue(name, value);
            return conf;
        }

        public DefaultAnyValue() {
            this(false);
        }

        public DefaultAnyValue(boolean ignoreCase) {
            this.predicate = ignoreCase ? EQUALSIGNORE : EQUALS;
        }

        public DefaultAnyValue(BiPredicate<String, String> predicate) {
            this.predicate = predicate;
        }

        public DefaultAnyValue duplicate() {
            DefaultAnyValue rs = new DefaultAnyValue(this.predicate);
            rs.stringValues = this.stringValues;
            rs.entityValues = this.entityValues;
            return rs;
        }

        public DefaultAnyValue addAll(final AnyValue av) {
            if (av == null) return this;
            if (av instanceof DefaultAnyValue) {
                final DefaultAnyValue adv = (DefaultAnyValue) av;
                if (adv.stringValues != null) {
                    for (Entry<String> en : adv.stringValues) {
                        this.addValue(en.name, en.value);
                    }
                }
                if (adv.entityValues != null) {
                    for (Entry<AnyValue> en : adv.entityValues) {
                        this.addValue(en.name, en.value);
                    }
                }
            } else {
                final Entry<String>[] strings = av.getStringEntrys();
                if (strings != null) {
                    for (Entry<String> en : strings) {
                        this.addValue(en.name, en.value);
                    }
                }
                final Entry<AnyValue>[] anys = av.getAnyEntrys();
                if (anys != null) {
                    for (Entry<AnyValue> en : anys) {
                        this.addValue(en.name, en.value);
                    }
                }
            }
            return this;
        }

        public DefaultAnyValue setAll(final AnyValue av) {
            if (av == null) return this;
            if (av instanceof DefaultAnyValue) {
                final DefaultAnyValue adv = (DefaultAnyValue) av;
                if (adv.stringValues != null) {
                    for (Entry<String> en : adv.stringValues) {
                        this.setValue(en.name, en.value);
                    }
                }
                if (adv.entityValues != null) {
                    for (Entry<AnyValue> en : adv.entityValues) {
                        this.setValue(en.name, en.value);
                    }
                }
            } else {
                final Entry<String>[] strings = av.getStringEntrys();
                if (strings != null) {
                    for (Entry<String> en : strings) {
                        this.setValue(en.name, en.value);
                    }
                }
                final Entry<AnyValue>[] anys = av.getAnyEntrys();
                if (anys != null) {
                    for (Entry<AnyValue> en : anys) {
                        this.setValue(en.name, en.value);
                    }
                }
            }
            return this;
        }

        @Override
        public Entry<String>[] getStringEntrys() {
            return stringValues;
        }

        @Override
        public Entry<AnyValue>[] getAnyEntrys() {
            return entityValues;
        }

        @Override
        public String[] getNames() {
            Set<String> set = new LinkedHashSet<>();
            for (Entry en : this.stringValues) {
                set.add(en.name);
            }
            for (Entry en : this.entityValues) {
                set.add(en.name);
            }
            return set.toArray(new String[set.size()]);
        }

        @Override
        public String[] getValues(String... names) {
            return Entry.getValues(this.predicate, String.class, this.stringValues, names);
        }

        @Override
        public AnyValue[] getAnyValues(String... names) {
            return Entry.getValues(this.predicate, AnyValue.class, this.entityValues, names);
        }

        @Override
        public String toString() {
            return toString(0);
        }

        public DefaultAnyValue clear() {
            this.stringValues = new Entry[0];
            this.entityValues = new Entry[0];
            return this;
        }

        public DefaultAnyValue setValue(String name, String value) {
            if (name == null) return this;
            if (getValue(name) == null) {
                this.addValue(name, value);
            } else {
                for (Entry<String> en : this.stringValues) {
                    if (predicate.test(en.name, name)) {
                        en.value = value;
                        return this;
                    }
                }
            }
            return this;
        }

        public DefaultAnyValue setValue(String name, AnyValue value) {
            if (name == null) return this;
            if (getValue(name) == null) {
                this.addValue(name, value);
            } else {
                for (Entry<AnyValue> en : this.entityValues) {
                    if (predicate.test(en.name, name)) {
                        en.value = value;
                        return this;
                    }
                }
            }
            return this;
        }

        public DefaultAnyValue addValue(String name, String value) {
            if (name == null) return this;
            int len = this.stringValues.length;
            Entry[] news = new Entry[len + 1];
            System.arraycopy(this.stringValues, 0, news, 0, len);
            news[len] = new Entry(name, value);
            this.stringValues = news;
            return this;
        }

        public DefaultAnyValue addValue(String name, AnyValue value) {
            if (name == null || value == null) return this;
            int len = this.entityValues.length;
            Entry[] news = new Entry[len + 1];
            System.arraycopy(this.entityValues, 0, news, 0, len);
            news[len] = new Entry(name, value);
            this.entityValues = news;
            return this;
        }

        @Override
        public AnyValue getAnyValue(String name) {
            for (Entry<AnyValue> en : this.entityValues) {
                if (predicate.test(en.name, name)) {
                    return en.value;
                }
            }
            return null;
        }

        @Override
        public String getValue(String name) {
            for (Entry<String> en : this.stringValues) {
                if (predicate.test(en.name, name)) {
                    return en.value;
                }
            }
            return null;
        }

        @Override
        public String[] getValues(String name) {
            return Entry.getValues(this.predicate, String.class, this.stringValues, name);
        }

        @Override
        public AnyValue[] getAnyValues(String name) {
            return Entry.getValues(this.predicate, AnyValue.class, this.entityValues, name);
        }

    }

    public final class Entry<T> {

        public final String name;

        T value;

        public Entry(String name0, T value0) {
            this.name = name0;
            this.value = value0;
        }

        public T getValue() {
            return value;
        }

        static <T> T[] getValues(BiPredicate<String, String> comparison, Class<T> clazz, Entry<T>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) return (T[]) Array.newInstance(clazz, len);
            T[] rs = (T[]) Array.newInstance(clazz, len);
            int i = 0;
            for (Entry<T> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en.value;
                }
            }
            return rs;
        }

        static <T> T[] getValues(BiPredicate<String, String> comparison, Class<T> clazz, Entry<T>[] entitys, String... names) {
            int len = 0;
            for (Entry en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        ++len;
                        break;
                    }
                }
            }
            if (len == 0) return (T[]) Array.newInstance(clazz, len);
            T[] rs = (T[]) Array.newInstance(clazz, len);
            int i = 0;
            for (Entry<T> en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        rs[i++] = en.value;
                        break;
                    }
                }
            }
            return rs;
        }
    }

    public static AnyValue create() {
        return new DefaultAnyValue();
    }

    default String toString(int len) {
        if (len < 0) len = 0;
        char[] chars = new char[len];
        Arrays.fill(chars, ' ');
        final String space = new String(chars);
        StringBuilder sb = new StringBuilder();
        sb.append("{\r\n");
        for (Entry<String> en : getStringEntrys()) {
            sb.append(space).append("    '").append(en.name).append("': '").append(en.value).append("',\r\n");
        }
        for (Entry<AnyValue> en : getAnyEntrys()) {
            sb.append(space).append("    '").append(en.name).append("': '").append(en.value.toString(len + 4)).append("',\r\n");
        }
        sb.append(space).append('}');
        return sb.toString();
    }

    public Entry<String>[] getStringEntrys();

    public Entry<AnyValue>[] getAnyEntrys();

    public String[] getNames();

    public String[] getValues(String name);

    public String[] getValues(String... names);

    public AnyValue[] getAnyValues(String name);

    public AnyValue[] getAnyValues(String... names);

    public AnyValue getAnyValue(String name);

    public String getValue(String name);

    default boolean getBoolValue(String name) {
        return Boolean.parseBoolean(getValue(name));
    }

    default boolean getBoolValue(String name, boolean defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    default byte getByteValue(String name) {
        return Byte.parseByte(getValue(name));
    }

    default byte getByteValue(String name, byte defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Byte.decode(value);
    }

    default char getCharValue(String name) {
        return getValue(name).charAt(0);
    }

    default char getCharValue(String name, char defaultValue) {
        String value = getValue(name);
        return value == null || value.length() == 0 ? defaultValue : value.charAt(0);
    }

    default short getShortValue(String name) {
        return Short.decode(getValue(name));
    }

    default short getShortValue(String name, short defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Short.decode(value);
    }

    default int getIntValue(String name) {
        return Integer.decode(getValue(name));
    }

    default int getIntValue(String name, int defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Integer.decode(value);
    }

    default long getLongValue(String name) {
        return Long.decode(getValue(name));
    }

    default long getLongValue(String name, long defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Long.decode(value);
    }

    default float getFloatValue(String name) {
        return Float.parseFloat(getValue(name));
    }

    default float getFloatValue(String name, float defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Float.parseFloat(value);
    }

    default double getDoubleValue(String name) {
        return Double.parseDouble(getValue(name));
    }

    default double getDoubleValue(String name, double defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : Double.parseDouble(value);
    }

    default String getValue(String name, String defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : value;
    }

}
