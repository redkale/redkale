/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.function.*;
import org.redkale.annotation.ConstructorParameters;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.ConvertDisabled;
import org.redkale.convert.json.JsonArray;
import org.redkale.convert.json.JsonObject;
import static org.redkale.util.Utility.isEmpty;

/**
 * 该类提供类似JSONObject的数据结构，主要用于读取xml配置文件和http-header存储
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public abstract class AnyValue {

    /** xml的文本节点name */
    public static final String XML_TEXT_NODE_NAME = "";

    /** merge两节点是否覆盖的判断函数 */
    public enum MergeEnum {
        /** 异常 */
        DEFAULT,
        /** 替换 */
        REPLACE,
        /** 合并 */
        MERGE,
        /** 丢弃 */
        IGNORE;
    }

    /** merge两节点是否覆盖的判断函数 */
    public static interface MergeStrategy {

        public MergeEnum apply(String path, String name, AnyValue val1, AnyValue val2);
    }

    /**
     * @see org.redkale.util.AnyValueWriter
     * @deprecated replace {@link org.redkale.util.AnyValueWriter}
     */
    @Deprecated(since = "2.8.0")
    public static final class DefaultAnyValue extends AnyValueWriter {

        public static final DefaultAnyValue create() {
            return new DefaultAnyValue();
        }
    }
    //

    /**
     * 字段名和值的组合对象
     *
     * @param <T> 泛型
     */
    public static final class Entry<T> {

        /** 字段名 */
        @ConvertColumn(index = 1)
        public final String name;

        @ConvertColumn(index = 2)
        T value;

        @ConstructorParameters({"name", "value"})
        public Entry(String name, T value) {
            this.name = name;
            this.value = value;
        }

        /**
         * 获取字段名
         *
         * @return 字段名
         */
        public String getName() {
            return name;
        }

        /**
         * 获取字段值
         *
         * @return 字段值
         */
        public T getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "{'" + name + "': " + value + "}";
        }

        static Entry<AnyValue>[] getEntryAnyValueArray(
                BiPredicate<String, String> comparison, Entry<AnyValue>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) {
                return new Entry[len];
            }
            Entry[] rs = new Entry[len];
            int i = 0;
            for (Entry<AnyValue> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en;
                }
            }
            return rs;
        }

        static String[] getStringArray(BiPredicate<String, String> comparison, Entry<String>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) {
                return new String[len];
            }
            String[] rs = new String[len];
            int i = 0;
            for (Entry<String> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en.value;
                }
            }
            return rs;
        }

        static AnyValue[] getAnyValueArray(
                BiPredicate<String, String> comparison, Entry<AnyValue>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) {
                return new AnyValue[len];
            }
            AnyValue[] rs = new AnyValue[len];
            int i = 0;
            for (Entry<AnyValue> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en.value;
                }
            }
            return rs;
        }

        static String[] getStringArray(
                BiPredicate<String, String> comparison, Entry<String>[] entitys, String... names) {
            int len = 0;
            for (Entry en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        ++len;
                        break;
                    }
                }
            }
            if (len == 0) {
                return new String[len];
            }
            String[] rs = new String[len];
            int i = 0;
            for (Entry<String> en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        rs[i++] = en.value;
                        break;
                    }
                }
            }
            return rs;
        }

        static AnyValue[] getAnyValueArray(
                BiPredicate<String, String> comparison, Entry<AnyValue>[] entitys, String... names) {
            int len = 0;
            for (Entry en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        ++len;
                        break;
                    }
                }
            }
            if (len == 0) {
                return new AnyValue[len];
            }
            AnyValue[] rs = new AnyValue[len];
            int i = 0;
            for (Entry<AnyValue> en : entitys) {
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

    /**
     * 创建AnyValueWriter
     *
     * @return AnyValueWriter
     */
    public static AnyValueWriter create() {
        return new AnyValueWriter();
    }

    /**
     * yaml内容流转换成AnyValue对象
     *
     *
     * @param text 文本内容
     * @return AnyValue
     */
    public static AnyValue loadFromYaml(String text) {
        return new YamlReader(text).read();
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param text 文本内容
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            // 不会发生
        }
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param text 文本内容
     * @param nameName String
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(String text, String nameName) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            // 不会发生
        }
        return loadFromProperties(properties, nameName);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param in 内容流
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromProperties(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param in 内容流
     * @param charset 字符编码
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromProperties(InputStream in, Charset charset) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param properties Properties
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(Properties properties) {
        return loadFromProperties(properties, null);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔 <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入nameName属性
     *
     * @param properties Properties
     * @param nameName String
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(Properties properties, String nameName) {
        if (properties == null) {
            return null;
        }
        AnyValueWriter conf = new AnyValueWriter();
        final char splitChar = (char) 2;
        Map<String, AnyValueWriter> prefixArray = new TreeMap<>(); // 已处理的数组key，如:redkale.source[0].xx存redkale.source[0]
        properties.forEach((key, value) -> {
            StringBuilder temp = new StringBuilder();
            boolean flag = false;
            for (char ch : key.toString().toCharArray()) { // 替换redkale.properties[my.name]中括号里的'.'
                if (ch == '[') {
                    flag = true;
                    temp.append(ch);
                } else if (ch == ']') {
                    flag = false;
                    temp.append(ch);
                } else {
                    temp.append(flag && ch == '.' ? splitChar : ch);
                }
            }
            String[] keys = temp.toString().split("\\.");
            for (int i = 0; i < keys.length; i++) {
                keys[i] = keys[i].replace(splitChar, '.');
            }
            AnyValueWriter parent = conf;
            if (keys.length > 1) {
                for (int i = 0; i < keys.length - 1; i++) {
                    String item = keys[i];
                    int pos = item.indexOf('[');
                    if (pos < 0) {
                        AnyValueWriter child = (AnyValueWriter) parent.getAnyValue(item);
                        if (child == null) {
                            child = new AnyValueWriter();
                            parent.addValue(item, child);
                        }
                        parent = child;
                    } else { // 数组或Map结构, []中间是数字开头的视为数组，其他视为map
                        String itemField = item.substring(0, pos); // [前面一部分'sources[1]'中'sources'
                        String keyOrIndex = item.substring(pos + 1, item.indexOf(']')); // 'sources[1]'中'1'
                        int realIndex = -1;
                        if (!keyOrIndex.isEmpty() && keyOrIndex.charAt(0) >= '0' && keyOrIndex.charAt(0) <= '9') {
                            try {
                                realIndex = Integer.parseInt(keyOrIndex);
                            } catch (NumberFormatException e) {
                                // do nothing
                            }
                        }
                        if (realIndex >= 0) { // 数组结构
                            String prefixKey = "";
                            for (int j = 0; j < i; j++) {
                                prefixKey += keys[j] + ".";
                            }
                            AnyValueWriter array = prefixArray.get(prefixKey + item); // item: [1]
                            if (array == null) {
                                final int ii = i;
                                String findkey = prefixKey + itemField + "[";
                                Map<String, AnyValueWriter> keymap = new TreeMap<>();
                                Map<Integer, AnyValueWriter> sortmap = new TreeMap<>();
                                properties.keySet().stream()
                                        .filter(x -> x.toString().startsWith(findkey))
                                        .forEach(k -> {
                                            String[] ks = k.toString().split("\\.");
                                            String prefixKey2 = "";
                                            for (int j = 0; j < ii; j++) {
                                                prefixKey2 += ks[j] + ".";
                                            }
                                            prefixKey2 += ks[ii];
                                            if (!keymap.containsKey(prefixKey2)) {
                                                AnyValueWriter vv = new AnyValueWriter();
                                                keymap.put(prefixKey2, vv);
                                                int aindex = Integer.parseInt(ks[ii].substring(
                                                        ks[ii].indexOf('[') + 1, ks[ii].lastIndexOf(']')));
                                                vv.parentArrayIndex = aindex;
                                                sortmap.put(aindex, vv);
                                            }
                                        });
                                prefixArray.putAll(keymap);
                                AnyValueWriter pv = parent;
                                sortmap.values().forEach(v -> pv.addValue(itemField, v));
                                array = prefixArray.get(prefixKey + item);
                            }
                            parent = array;
                        } else { // Map结构
                            AnyValueWriter field = (AnyValueWriter) parent.getAnyValue(itemField);
                            if (field == null) {
                                field = new AnyValueWriter();
                                parent.addValue(itemField, field);
                            }
                            AnyValueWriter index = (AnyValueWriter) field.getAnyValue(keyOrIndex);
                            if (index == null) {
                                index = new AnyValueWriter();
                                if (nameName != null) {
                                    index.setValue(nameName, keyOrIndex);
                                }
                                field.addValue(keyOrIndex, index);
                            }
                            parent = index;
                        }
                    }
                }
            }

            String lastItem = keys[keys.length - 1];
            int pos = lastItem.indexOf('[');
            if (pos < 0) {
                parent.addValue(lastItem, value.toString());
            } else {
                String itemField = lastItem.substring(0, pos); // [前面一部分
                String itemIndex = lastItem.substring(pos + 1, lastItem.indexOf(']'));
                if (!itemIndex.isEmpty() && itemIndex.charAt(0) >= '0' && itemIndex.charAt(0) <= '9') { // 数组
                    // parent.addValue(itemField, value.toString());
                    String[] tss = parent.getValues(itemField);
                    if (tss == null || tss.length == 0) {
                        String prefixKey = "";
                        for (int j = 0; j < keys.length - 1; j++) {
                            prefixKey += keys[j] + ".";
                        }
                        final int ii = keys.length - 1;
                        String findkey = prefixKey + itemField + "[";
                        Map<String, String> keymap = new TreeMap<>();
                        Map<Integer, String> sortmap = new TreeMap<>();
                        properties.keySet().stream()
                                .filter(x -> x.toString().startsWith(findkey))
                                .forEach(k -> {
                                    String[] ks = k.toString().split("\\.");
                                    String prefixKey2 = "";
                                    for (int j = 0; j < ii; j++) {
                                        prefixKey2 += ks[j] + ".";
                                    }
                                    prefixKey2 += ks[ii];
                                    if (!keymap.containsKey(prefixKey2)) {
                                        String vv = properties.getProperty(k.toString());
                                        keymap.put(prefixKey2, vv);
                                        sortmap.put(
                                                Integer.parseInt(ks[ii].substring(
                                                        ks[ii].indexOf('[') + 1, ks[ii].lastIndexOf(']'))),
                                                vv);
                                    }
                                });
                        AnyValueWriter pv = parent;
                        sortmap.values().forEach(v -> pv.addValue(itemField, v));
                    }
                } else { // Map
                    AnyValueWriter child = (AnyValueWriter) parent.getAnyValue(itemField);
                    if (child == null) {
                        child = new AnyValueWriter();
                        parent.addValue(itemField, child);
                    }
                    child.addValue(itemIndex, value.toString());
                }
            }
        });
        return conf;
    }

    /**
     * xml文本内容转换成AnyValue对象
     *
     * @param text 文本内容
     * @return AnyValue
     */
    public static AnyValue loadFromXml(String text) {
        return new XmlReader(text).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in 内容流
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in) throws IOException {
        return loadFromXml(in, StandardCharsets.UTF_8);
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in 内容流
     * @param charset 字符编码
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, Charset charset) throws IOException {
        return new XmlReader(Utility.read(in, charset)).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param text 文本内容
     * @param attrFunc 字段回调函数
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(String text, BinaryOperator<String> attrFunc) throws IOException {
        return new XmlReader(text).attrFunc(attrFunc).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in 内容流
     * @param attrFunc 字段回调函数
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, BinaryOperator<String> attrFunc) throws IOException {
        return loadFromXml(in, StandardCharsets.UTF_8, attrFunc);
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in 内容流
     * @param charset 字符编码
     * @param attrFunc 字段回调函数
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, Charset charset, BinaryOperator<String> attrFunc)
            throws IOException {
        return new XmlReader(Utility.read(in, charset)).attrFunc(attrFunc).read();
    }

    /**
     * 当前AnyValue对象字符串化
     *
     * @param indent 缩进长度
     * @param prefixFunc 扩展函数
     * @return String
     */
    public String toString(int indent, BiFunction<AnyValue, String, CharSequence> prefixFunc) { // indent: 缩进长度
        if (indent < 0) {
            indent = 0;
        }
        final String space = " ".repeat(indent);
        StringBuilder sb = new StringBuilder();
        sb.append("{\r\n");
        if (prefixFunc != null) {
            CharSequence v = prefixFunc.apply(this, space);
            if (v != null) {
                sb.append(v);
            }
        }
        Entry<String>[] stringArray = getStringEntrys();
        Entry<AnyValue>[] anyArray = getAnyEntrys();
        int size = (stringArray == null ? 0 : stringArray.length) + (anyArray == null ? 0 : anyArray.length);
        int index = 0;
        if (stringArray != null) {
            for (Entry<String> en : stringArray) {
                if (en.value == null) {
                    sb.append(space).append("    '").append(en.name).append("': null");
                } else {
                    sb.append(space)
                            .append("    '")
                            .append(en.name)
                            .append("': '")
                            .append(en.value)
                            .append("'");
                }
                if (++index >= size) {
                    sb.append("\r\n");
                } else {
                    sb.append(",\r\n");
                }
            }
        }
        if (anyArray != null) {
            for (Entry<AnyValue> en : anyArray) {
                sb.append(space)
                        .append("    '")
                        .append(en.name)
                        .append("': ")
                        .append(en.value.toString(indent + 4, prefixFunc));
                if (++index >= size) {
                    sb.append("\r\n");
                } else {
                    sb.append(",\r\n");
                }
            }
        }
        sb.append(space).append('}');
        return sb.toString();
    }

    /**
     * 复制一份
     *
     * @return AnyValue
     */
    public abstract AnyValue copy();

    /**
     * 将另一个对象替换本对象
     *
     * @param node 替换的对象
     * @return AnyValue
     */
    public abstract AnyValue replace(AnyValue node);

    /**
     * 将另一个对象合并过来
     *
     * @param node 代合并对象
     * @param func 覆盖方式的函数
     * @return AnyValue
     */
    public abstract AnyValue merge(AnyValue node, MergeStrategy func);

    /**
     * 回调子节点
     *
     * @param stringConsumer 字符串字段的回调函数
     */
    public abstract void forEach(BiConsumer<String, String> stringConsumer);

    /**
     * 回调子节点
     *
     * @param stringConsumer 字符串字段的回调函数
     * @param anyConsumer 字符串对象的回调函数
     */
    public abstract void forEach(BiConsumer<String, String> stringConsumer, BiConsumer<String, AnyValue> anyConsumer);

    /**
     * 获取所有字符串子节点
     *
     * @return Entry[]
     */
    public abstract Entry<String>[] getStringEntrys();

    /**
     * 获取所有复合子节点
     *
     * @return Entry[]
     */
    public abstract Entry<AnyValue>[] getAnyEntrys();

    /**
     * 获取字段名集合
     *
     * @return String[]
     */
    @ConvertDisabled
    public abstract String[] getNames();

    /**
     * 获取同级下同一字段名下所有的String对象
     *
     * @param name 字段名
     * @return String[]
     */
    public abstract String[] getValues(String name);

    /**
     * 根据字段名集合获取String类型的字段值集合
     *
     * @param names 字段名集合
     * @return String[]
     */
    public abstract String[] getValues(String... names);

    /**
     * 获取同级下同一字段名下所有的AnyValue对象
     *
     * @param name 字段名
     * @return AnyValue[]
     */
    public abstract AnyValue[] getAnyValues(String name);

    /**
     * 根据字段名集合获取AnyValue类型的字段值集合
     *
     * @param names 字段名集合
     * @return AnyValue[]
     */
    public abstract AnyValue[] getAnyValues(String... names);

    /**
     * 根据字段名获取AnyValue类型的字段值
     *
     * @param name 字段名
     * @return AnyValue
     */
    public abstract AnyValue getAnyValue(String name);

    /**
     * 根据字段名获取AnyValue类型的字段值
     *
     * @param name 字段名
     * @param create 没有是否创建一个新的对象返回
     * @return AnyValue
     */
    public abstract AnyValue getAnyValue(String name, boolean create);

    /**
     * 根据字段名获取String类型的字段值
     *
     * @param name 字段名
     * @return String
     */
    public abstract String getValue(String name);

    /**
     * 根据字段名获取String类型的字段值
     *
     * @param name 字段名
     * @return String
     */
    public abstract String get(String name);

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public boolean getBoolValue(String name) {
        return Boolean.parseBoolean(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public boolean getBoolValue(String name, boolean defaultValue) {
        String value = getValue(name);
        return value == null || value.length() == 0 ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public byte getByteValue(String name) {
        return Byte.parseByte(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public byte getByteValue(String name, byte defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Byte.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix 进制，默认十进制
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public byte getByteValue(int radix, String name, byte defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Byte.decode(value) : Byte.parseByte(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public char getCharValue(String name) {
        return getValue(name).charAt(0);
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public char getCharValue(String name, char defaultValue) {
        String value = getValue(name);
        return value == null || value.length() == 0 ? defaultValue : value.charAt(0);
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return String
     */
    public short getShortValue(String name) {
        return Short.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public short getShortValue(String name, short defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix 进制，默认十进制
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public short getShortValue(int radix, String name, short defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Short.decode(value) : Short.parseShort(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public int getIntValue(String name) {
        return Integer.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return String
     */
    public int getIntValue(String name, int defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix 进制，默认十进制
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public int getIntValue(int radix, String name, int defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Integer.decode(value) : Integer.parseInt(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public long getLongValue(String name) {
        return Long.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public long getLongValue(String name, long defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix 进制，默认十进制
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public long getLongValue(int radix, String name, long defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return (radix == 10 ? Long.decode(value) : Long.parseLong(value, radix));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return String
     */
    public float getFloatValue(String name) {
        return Float.parseFloat(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public float getFloatValue(String name, float defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @return 字段值
     */
    public double getDoubleValue(String name) {
        return Double.parseDouble(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public double getDoubleValue(String name, double defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public String getValue(String name, String defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : value;
    }

    /**
     * 获取字段值
     *
     * @param name 字段名
     * @param defaultValue 默认值
     * @return 字段值
     */
    public String getOrDefault(String name, String defaultValue) {
        return getValue(name, defaultValue);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AnyValue)) {
            return false;
        }
        AnyValue conf = (AnyValue) other;
        if (!equals(this.getStringEntrys(), conf.getStringEntrys())) {
            return false;
        }
        return equals(this.getAnyEntrys(), conf.getAnyEntrys());
    }

    private static <T> boolean equals(Entry<? extends T>[] entry1, Entry<T>[] entry2) {
        if (isEmpty(entry1) && isEmpty(entry2)) {
            return true;
        }
        if (entry1.length != entry2.length) {
            return false;
        }
        for (int i = 0; i < entry1.length; i++) {
            if (!entry1[i].name.equals(entry2[i].name)) {
                return false;
            }
            if (!entry1[i].getValue().equals(entry2[i].getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 19 * hash + Arrays.deepHashCode(this.getStringEntrys());
        hash = 19 * hash + Arrays.deepHashCode(this.getAnyEntrys());
        return hash;
    }

    /**
     * xml化当前AnyValue对象
     *
     * @param rootName root名称
     * @return String
     */
    public String toXml(String rootName) {
        return toXmlString(new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n\r\n"), rootName, this, 0)
                .toString();
    }

    /**
     * xml化AnyValue对象
     *
     * @param sb StringBuilder
     * @param nodeName 字段名
     * @param conf AnyValue
     * @param indent 缩进长度
     * @return StringBuilder
     */
    protected static StringBuilder toXmlString(
            StringBuilder sb, String nodeName, AnyValue conf, int indent) { // indent: 缩进长度
        if (indent < 0) {
            indent = 0;
        }
        final String space = " ".repeat(indent);
        Entry<AnyValue>[] anys = conf.getAnyEntrys();
        sb.append(space).append('<').append(nodeName);
        for (Entry<String> en : conf.getStringEntrys()) {
            sb.append(' ').append(en.name).append("=\"").append(en.value).append("\"");
        }
        if (anys == null || anys.length == 0) {
            return sb.append("/>\r\n\r\n");
        }
        sb.append(">\r\n\r\n");
        for (Entry<AnyValue> en : conf.getAnyEntrys()) {
            toXmlString(sb, en.name, en.getValue(), indent + 4);
        }
        return sb.append(space).append("</").append(nodeName).append(">\r\n\r\n");
    }

    public String toJsonString() {
        Entry<String>[] stringArray = getStringEntrys();
        Entry<AnyValue>[] anyArray = getAnyEntrys();
        final StringBuilder sb = new StringBuilder();
        sb.append('{');
        int size = (stringArray == null ? 0 : stringArray.length) + (anyArray == null ? 0 : anyArray.length);
        int index = 0;
        if (stringArray != null) {
            for (Entry<String> en : stringArray) {
                if (en.value == null) {
                    sb.append('"').append(en.name.replace("\"", "\\\"")).append("\":null");
                } else {
                    sb.append('"')
                            .append(en.name.replace("\"", "\\\""))
                            .append("\":\"")
                            .append(en.value.replace("\"", "\\\""))
                            .append('"');
                }
                if (++index < size) {
                    sb.append(',');
                }
            }
        }
        if (anyArray != null) {
            for (Entry<AnyValue> en : anyArray) {
                sb.append('"')
                        .append(en.name.replace("\"", "\\\""))
                        .append("\":")
                        .append(en.value.toJsonString());
                if (++index < size) {
                    sb.append(',');
                }
            }
        }
        sb.append('}');
        return sb.toString();
    }

    public Properties toProperties() {
        Properties props = new Properties();
        toProperties(props, "", this);
        return props;
    }

    protected static void toProperties(Properties props, String parent, AnyValue conf) {
        Map<String, List> tmp = new HashMap<>();
        Entry<String>[] strs = conf.getStringEntrys();
        if (strs != null && strs.length > 0) {
            for (Entry<String> en : strs) {
                tmp.computeIfAbsent(en.getName(), k -> new ArrayList<>()).add(en.getValue());
            }
            tmp.forEach((k, list) -> {
                if (list.size() == 1) {
                    props.put(parent + k, list.get(0));
                } else {
                    int i = -1;
                    for (Object item : list) {
                        props.put(parent + k + "[" + (++i) + "]", item);
                    }
                }
            });
        }
        Entry<AnyValue>[] entrys = conf.getAnyEntrys();
        if (entrys != null && entrys.length > 0) {
            tmp.clear();
            for (Entry<AnyValue> en : entrys) {
                tmp.computeIfAbsent(en.getName(), k -> new ArrayList<>()).add(en.getValue());
            }
            tmp.forEach((k, list) -> {
                if (list.size() == 1) {
                    toProperties(props, parent + k + ".", (AnyValue) list.get(0));
                } else {
                    int i = -1;
                    for (Object item : list) {
                        toProperties(props, parent + k + "[" + (++i) + "].", (AnyValue) item);
                    }
                }
            });
        }
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();
        toJsonObject(json, this);
        return json;
    }

    protected static void toJsonObject(JsonObject json, AnyValue conf) {
        Map<String, JsonArray> tmp = new HashMap<>();
        Entry<String>[] strs = conf.getStringEntrys();
        if (strs != null && strs.length > 0) {
            for (Entry<String> en : strs) {
                tmp.computeIfAbsent(en.getName(), k -> new JsonArray()).add(en.getValue());
            }
            tmp.forEach((k, list) -> {
                if (list.size() == 1) {
                    json.put(k, list.get(0));
                } else {
                    json.put(k, list);
                }
            });
        }
        Entry<AnyValue>[] entrys = conf.getAnyEntrys();
        if (entrys != null && entrys.length > 0) {
            tmp.clear();
            for (Entry<AnyValue> en : entrys) {
                tmp.computeIfAbsent(en.getName(), k -> new JsonArray()).add(en.getValue());
            }
            tmp.forEach((k, list) -> {
                if (list.size() == 1) {
                    JsonObject val = new JsonObject();
                    toJsonObject(val, (AnyValue) list.get(0));
                    json.put(k, val);
                } else {
                    JsonArray array = new JsonArray();
                    for (Object item : list) {
                        JsonObject val = new JsonObject();
                        toJsonObject(val, (AnyValue) item);
                        array.add(val);
                    }
                    json.put(k, array);
                }
            });
        }
    }
}
