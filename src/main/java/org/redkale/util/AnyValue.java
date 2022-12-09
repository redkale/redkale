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
import org.redkale.convert.ConvertDisabled;

/**
 * 该类提供类似JSONObject的数据结构，主要用于读取xml配置文件和http-header存储
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public abstract class AnyValue {

    /**
     * xml的文本节点name
     */
    public static final String XML_TEXT_NODE_NAME = "";

    /**
     * merge两节点是否覆盖的判断函数
     *
     */
    public static interface MergeFunction {

        public static final int NONE = 0;

        public static final int REPLACE = 1;

        public static final int MERGE = 2;

        public static final int SKIP = 3;

        public int apply(String path, String name, AnyValue val1, AnyValue val2);
    }

    /**
     * 可读写的AnyValue默认实现类
     *
     * @author zhangjx
     */
    @SuppressWarnings("unchecked")
    public static final class DefaultAnyValue extends AnyValue {

        /**
         * 区分name大小写的比较策略
         *
         */
        public static final BiPredicate<String, String> EQUALS = (name1, name2) -> name1.equals(name2);

        /**
         * 不区分name大小写的比较策略
         */
        public static final BiPredicate<String, String> EQUALSIGNORE = (name1, name2) -> name1.equalsIgnoreCase(name2);

        private boolean ignoreCase;

        private BiPredicate<String, String> predicate;

        private Entry<String>[] stringEntrys = new Entry[0];

        private Entry<DefaultAnyValue>[] anyEntrys = new Entry[0];

        private int parentArrayIndex = -1; //只可能被loadFromProperties方法赋值

        /**
         * 创建空的DefaultAnyValue对象
         *
         * @return DefaultAnyValue对象
         */
        public static final DefaultAnyValue create() {
            return new DefaultAnyValue();
        }

        /**
         * 创建含name-value值的DefaultAnyValue对象
         *
         * @param name  name
         * @param value value值
         *
         * @return DefaultAnyValue对象
         */
        public static final DefaultAnyValue create(String name, Number value) {
            DefaultAnyValue conf = new DefaultAnyValue();
            conf.addValue(name, value);
            return conf;
        }

        /**
         * 创建含name-value值的DefaultAnyValue对象
         *
         * @param name  name
         * @param value value值
         *
         * @return DefaultAnyValue对象
         */
        public static final DefaultAnyValue create(String name, String value) {
            DefaultAnyValue conf = new DefaultAnyValue();
            conf.addValue(name, value);
            return conf;
        }

        /**
         * 创建含name-value值的DefaultAnyValue对象
         *
         * @param name  name
         * @param value value值
         *
         * @return DefaultAnyValue对象
         */
        public static final DefaultAnyValue create(String name, AnyValue value) {
            DefaultAnyValue conf = new DefaultAnyValue();
            conf.addValue(name, value);
            return conf;
        }

        /**
         * 创建一个区分大小写比较策略的DefaultAnyValue对象
         *
         */
        public DefaultAnyValue() {
            this(false);
        }

        /**
         * 创建DefaultAnyValue对象
         *
         * @param ignoreCase name是否不区分大小写
         */
        public DefaultAnyValue(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            this.predicate = ignoreCase ? EQUALSIGNORE : EQUALS;
        }

        /**
         * 创建共享此内容的DefaultAnyValue对象
         *
         * @return DefaultAnyValue对象
         */
        public DefaultAnyValue duplicate() {
            DefaultAnyValue rs = new DefaultAnyValue(this.ignoreCase);
            rs.stringEntrys = this.stringEntrys;
            rs.anyEntrys = this.anyEntrys;
            return rs;
        }

        /**
         * 复制一份对象
         *
         * @return DefaultAnyValue对象
         */
        @Override
        public DefaultAnyValue copy() {
            DefaultAnyValue rs = new DefaultAnyValue(this.ignoreCase);
            rs.predicate = this.predicate;
            rs.parentArrayIndex = this.parentArrayIndex;
            if (this.stringEntrys != null) {
                rs.stringEntrys = new Entry[this.stringEntrys.length];
                for (int i = 0; i < rs.stringEntrys.length; i++) {
                    Entry<String> en = this.stringEntrys[i];
                    if (en == null) continue;
                    rs.stringEntrys[i] = new Entry(en.name, en.value);
                }
            }
            if (this.anyEntrys != null) {
                rs.anyEntrys = new Entry[this.anyEntrys.length];
                for (int i = 0; i < rs.anyEntrys.length; i++) {
                    Entry<DefaultAnyValue> en = this.anyEntrys[i];
                    if (en == null) continue;
                    rs.anyEntrys[i] = new Entry(en.name, en.value == null ? null : en.value.copy());
                }
            }
            return rs;
        }

        /**
         * 将另一个对象替换本对象
         *
         * @param node 替换的对象
         *
         * @return AnyValue
         */
        @Override
        public DefaultAnyValue replace(AnyValue node) {
            if (node != null) {
                DefaultAnyValue rs = (DefaultAnyValue) node;
                this.ignoreCase = rs.ignoreCase;
                this.predicate = rs.predicate;
                this.parentArrayIndex = rs.parentArrayIndex;
                this.stringEntrys = rs.stringEntrys;
                this.anyEntrys = rs.anyEntrys;
            }
            return this;
        }

        /**
         * 将另一个对象合并过来
         *
         * @param node 代合并对象
         * @param func 判断覆盖方式的函数
         *
         * @return AnyValue
         */
        @Override
        public DefaultAnyValue merge(AnyValue node, MergeFunction func) {
            return merge(node, "", func);
        }

        protected DefaultAnyValue merge(AnyValue node0, String path, MergeFunction func) {
            if (node0 == null) return this;
            if (node0 == this) throw new IllegalArgumentException();
            DefaultAnyValue node = (DefaultAnyValue) node0;
            if (node.stringEntrys != null) {
                for (Entry<String> en : node.stringEntrys) {
                    if (en == null) continue;
                    setValue(en.name, en.value);
                }
            }
            if (node.anyEntrys != null) {
                for (Entry<DefaultAnyValue> en : node.anyEntrys) {
                    if (en == null || en.value == null) continue;
                    Entry<AnyValue>[] ns = getAnyValueEntrys(en.name);
                    if (ns == null || ns.length < 1) {
                        addValue(en.name, en.value);
                    } else {
                        boolean ok = false;
                        for (Entry<AnyValue> item : ns) {
                            if (item == null) continue;
                            if (item.value != null && en.value.parentArrayIndex == ((DefaultAnyValue) item.value).parentArrayIndex) {
                                if (func == null) {
                                    item.value.merge(en.value, func);
                                    ok = true;
                                    break;
                                } else {
                                    int funcVal = func.apply(path, en.name, en.value, item.value);
                                    if (funcVal == MergeFunction.MERGE) {
                                        String subPath = path.isEmpty() ? en.name : (path + "." + en.name);
                                        ((DefaultAnyValue) item.value).merge(en.value, subPath, func);
                                        ok = true;
                                        break;
                                    } else if (funcVal == MergeFunction.REPLACE) {
                                        item.value = en.value.copy();
                                        ok = true;
                                        break;
                                    } else if (funcVal == MergeFunction.SKIP) {
                                        ok = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!ok) {
                            addValue(en.name, en.value);
                        }
                    }
                }
            }
            return this;
        }

        /**
         * 合并两个AnyValue对象， 会去重， 没有的才增加
         *
         * @param av AnyValue
         *
         * @return DefaultAnyValue
         */
        public DefaultAnyValue addAllStringSet(final AnyValue av) {
            if (av == null) return this;
            final Entry<String>[] strings = av.getStringEntrys();
            if (strings == null) return this;
            for (Entry<String> en : strings) {
                if (!existsValue(en.name)) this.addValue(en.name, en.value);
            }
            return this;
        }

        /**
         * 合并两个AnyValue对象 不去重
         *
         * @param av AnyValue
         *
         * @return DefaultAnyValue
         */
        public DefaultAnyValue addAll(final AnyValue av) {
            if (av == null) return this;
            if (av instanceof DefaultAnyValue) {
                final DefaultAnyValue adv = (DefaultAnyValue) av;
                if (adv.stringEntrys != null) {
                    for (Entry<String> en : adv.stringEntrys) {
                        this.addValue(en.name, en.value);
                    }
                }
                if (adv.anyEntrys != null) {
                    for (Entry<DefaultAnyValue> en : adv.anyEntrys) {
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

        /**
         * 合并两个AnyValue对象 会去重
         *
         * @param av AnyValue
         *
         * @return DefaultAnyValue
         */
        public DefaultAnyValue setAll(final AnyValue av) {
            if (av == null) return this;
            if (av instanceof DefaultAnyValue) {
                final DefaultAnyValue adv = (DefaultAnyValue) av;
                if (adv.stringEntrys != null) {
                    for (Entry<String> en : adv.stringEntrys) {
                        this.setValue(en.name, en.value);
                    }
                }
                if (adv.anyEntrys != null) {
                    for (Entry<DefaultAnyValue> en : adv.anyEntrys) {
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
        public void forEach(BiConsumer<String, String> stringConsumer) {
            forEach(stringConsumer, null);
        }

        @Override
        public void forEach(BiConsumer<String, String> stringConsumer, BiConsumer<String, AnyValue> anyConsumer) {
            if (stringConsumer != null) {
                for (Entry<String> en : stringEntrys) {
                    stringConsumer.accept(en.name, en.value);
                }
            }
            if (anyConsumer != null) {
                for (Entry<AnyValue> en : (Entry[]) anyEntrys) {
                    anyConsumer.accept(en.name, en.value);
                }
            }
        }

        @Override
        public Entry<String>[] getStringEntrys() {
            return stringEntrys;
        }

        public void setStringEntrys(Entry<String>[] stringEntrys) {
            this.stringEntrys = stringEntrys;
        }

        @Override
        public Entry<AnyValue>[] getAnyEntrys() {
            return (Entry<AnyValue>[]) (Entry[]) anyEntrys;
        }

        public void setAnyEntrys(Entry<DefaultAnyValue>[] anyEntrys) {
            this.anyEntrys = anyEntrys;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        public void setIgnoreCase(boolean ignoreCase) {
            this.ignoreCase = ignoreCase;
            if (this.predicate == null) {
                this.predicate = ignoreCase ? EQUALSIGNORE : EQUALS;
            }
        }

        @Override
        @ConvertDisabled
        public String[] getNames() {
            Set<String> set = new LinkedHashSet<>();
            for (Entry en : this.stringEntrys) {
                set.add(en.name);
            }
            for (Entry en : this.anyEntrys) {
                set.add(en.name);
            }
            return set.toArray(new String[set.size()]);
        }

        @Override
        public String[] getValues(String... names) {
            return Entry.getStringArray(this.predicate, this.stringEntrys, names);
        }

        @Override
        public AnyValue[] getAnyValues(String... names) {
            return Entry.getAnyValueArray(this.predicate, this.anyEntrys, names);
        }

        @Override
        public String[] getValues(String name) {
            return Entry.getStringArray(this.predicate, this.stringEntrys, name);
        }

        @Override
        public AnyValue[] getAnyValues(String name) {
            return Entry.getAnyValueArray(this.predicate, this.anyEntrys, name);
        }

        protected Entry<AnyValue>[] getAnyValueEntrys(String name) {
            return Entry.getEntryAnyValueArray(this.predicate, this.anyEntrys, name);
        }

        @Override
        public String toString() {
            return toString(0, (any, space) -> {
                int index = ((DefaultAnyValue) any).parentArrayIndex;
                if (index < 0) return null;
                return new StringBuilder().append(space).append("    '$index': ").append(index).append(",\r\n");
            });
        }

        public DefaultAnyValue clear() {
            if (this.stringEntrys != null && this.stringEntrys.length > 0) this.stringEntrys = new Entry[0];
            if (this.anyEntrys != null && this.anyEntrys.length > 0) this.anyEntrys = new Entry[0];
            return this;
        }

        public DefaultAnyValue setValue(String name, String value) {
            if (name == null) return this;
            if (!existsValue(name)) {
                this.addValue(name, value);
            } else {
                for (Entry<String> en : this.stringEntrys) {
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
            if (!existsValue(name)) {
                this.addValue(name, value);
            } else {
                for (Entry<DefaultAnyValue> en : this.anyEntrys) {
                    if (predicate.test(en.name, name)) {
                        en.value = (DefaultAnyValue) value;
                        return this;
                    }
                }
            }
            return this;
        }

        public DefaultAnyValue put(String name, boolean value) {
            return addValue(name, String.valueOf(value));
        }

        public DefaultAnyValue put(String name, Number value) {
            return addValue(name, String.valueOf(value));
        }

        public DefaultAnyValue put(String name, String value) {
            this.stringEntrys = Utility.append(this.stringEntrys, new Entry(name, value));
            return this;
        }

        public DefaultAnyValue addValue(String name, boolean value) {
            return addValue(name, String.valueOf(value));
        }

        public DefaultAnyValue addValue(String name, Number value) {
            return addValue(name, String.valueOf(value));
        }

        public DefaultAnyValue addValue(String name, String value) {
            this.stringEntrys = Utility.append(this.stringEntrys, new Entry(name, value));
            return this;
        }

        public DefaultAnyValue addValue(String name, AnyValue value) {
            if (name == null || value == null) return this;
            this.anyEntrys = Utility.append(this.anyEntrys, new Entry(name, value));
            return this;
        }

        public void clearParentArrayIndex(String name) {
            for (Entry<AnyValue> item : getAnyValueEntrys(name)) {
                if (item.value != null) {
                    ((DefaultAnyValue) item.value).parentArrayIndex = -1;
                }
            }
        }

        public DefaultAnyValue removeAnyValues(String name) {
            if (name == null || this.anyEntrys == null) return this;
            this.anyEntrys = Utility.remove(this.anyEntrys, (t) -> name.equals(((Entry) t).name));
            return this;
        }

        public DefaultAnyValue removeValue(String name, AnyValue value) {
            if (name == null || value == null || this.anyEntrys == null) return this;
            this.anyEntrys = Utility.remove(this.anyEntrys, (t) -> name.equals(((Entry) t).name) && ((Entry) t).getValue().equals(value));
            return this;
        }

        public DefaultAnyValue removeStringValues(String name) {
            if (name == null || this.stringEntrys == null) return this;
            this.stringEntrys = Utility.remove(this.stringEntrys, (t) -> name.equals(((Entry) t).name));
            return this;
        }

        public DefaultAnyValue removeValue(String name, String value) {
            if (name == null || value == null || this.stringEntrys == null) return this;
            this.stringEntrys = Utility.remove(this.stringEntrys, (t) -> name.equals(((Entry) t).name) && ((Entry) t).getValue().equals(value));
            return this;
        }

        @Override
        public AnyValue getAnyValue(String name) {
            for (Entry<DefaultAnyValue> en : this.anyEntrys) {
                if (predicate.test(en.name, name)) {
                    return en.value;
                }
            }
            return null;
        }

        @Override
        public String get(String name) {
            return getValue(name);
        }

        @Override
        public String getValue(String name) {
            for (Entry<String> en : this.stringEntrys) {
                if (predicate.test(en.name, name)) {
                    return en.value;
                }
            }
            return null;
        }

        public boolean existsValue(String name) {
            for (Entry<String> en : this.stringEntrys) {
                if (predicate.test(en.name, name)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 字段名和值的组合对象
     *
     * @param <T> 泛型
     */
    public static final class Entry<T> {

        /**
         * 字段名
         */
        public final String name;

        T value;

        @ConstructorParameters({"name", "value"})
        public Entry(String name0, T value0) {
            this.name = name0;
            this.value = value0;
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

        static Entry<AnyValue>[] getEntryAnyValueArray(BiPredicate<String, String> comparison, Entry<DefaultAnyValue>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) return new Entry[len];
            Entry[] rs = new Entry[len];
            int i = 0;
            for (Entry<DefaultAnyValue> en : entitys) {
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
            if (len == 0) return new String[len];
            String[] rs = new String[len];
            int i = 0;
            for (Entry<String> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en.value;
                }
            }
            return rs;
        }

        static AnyValue[] getAnyValueArray(BiPredicate<String, String> comparison, Entry<DefaultAnyValue>[] entitys, String name) {
            int len = 0;
            for (Entry en : entitys) {
                if (comparison.test(en.name, name)) {
                    ++len;
                }
            }
            if (len == 0) return new AnyValue[len];
            AnyValue[] rs = new AnyValue[len];
            int i = 0;
            for (Entry<DefaultAnyValue> en : entitys) {
                if (comparison.test(en.name, name)) {
                    rs[i++] = en.value;
                }
            }
            return rs;
        }

        static String[] getStringArray(BiPredicate<String, String> comparison, Entry<String>[] entitys, String... names) {
            int len = 0;
            for (Entry en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        ++len;
                        break;
                    }
                }
            }
            if (len == 0) return new String[len];
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

        static AnyValue[] getAnyValueArray(BiPredicate<String, String> comparison, Entry<DefaultAnyValue>[] entitys, String... names) {
            int len = 0;
            for (Entry en : entitys) {
                for (String name : names) {
                    if (comparison.test(en.name, name)) {
                        ++len;
                        break;
                    }
                }
            }
            if (len == 0) return new AnyValue[len];
            AnyValue[] rs = new AnyValue[len];
            int i = 0;
            for (Entry<DefaultAnyValue> en : entitys) {
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
     * 创建DefaultAnyValue
     *
     * @return DefaultAnyValue
     */
    public static DefaultAnyValue create() {
        return new DefaultAnyValue();
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param text 文本内容
     *
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            //不会发生
        }
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param text     文本内容
     * @param nameName String
     *
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(String text, String nameName) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException e) {
            //不会发生
        }
        return loadFromProperties(properties, nameName);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param in 内容流
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromProperties(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param in      内容流
     * @param charset 字符编码
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromProperties(InputStream in, Charset charset) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return loadFromProperties(properties);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入name属性
     *
     * @param properties Properties
     *
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(Properties properties) {
        return loadFromProperties(properties, null);
    }

    /**
     * Properties内容转换成AnyValue对象， 层级采用key的.分隔  <br>
     * key中包含[xx]且xx不是数字且不是位于最后的视为name，会在对应的节点对象中加入nameName属性
     *
     * @param properties Properties
     * @param nameName   String
     *
     * @return AnyValue
     */
    public static AnyValue loadFromProperties(Properties properties, String nameName) {
        if (properties == null) return null;
        DefaultAnyValue conf = new DefaultAnyValue();
        Map<String, DefaultAnyValue> prefixArray = new TreeMap<>(); //已处理的数组key，如 redkale.source[0].xx  存redkale.source[0]
        properties.forEach((key, value) -> {
            String[] keys = key.toString().split("\\.");
            DefaultAnyValue parent = conf;
            if (keys.length > 1) {
                for (int i = 0; i < keys.length - 1; i++) {
                    String item = keys[i];
                    int pos = item.indexOf('[');
                    if (pos < 0) {
                        DefaultAnyValue child = (DefaultAnyValue) parent.getAnyValue(item);
                        if (child == null) {
                            child = new DefaultAnyValue();
                            parent.addValue(item, child);
                        }
                        parent = child;
                    } else { //数组或Map结构, []中间是数字开头的视为数组，其他视为map
                        String itemField = item.substring(0, pos);  //[前面一部分'sources[1]'中'sources'
                        String keyOrIndex = item.substring(pos + 1, item.indexOf(']'));
                        int realIndex = -1;
                        if (!keyOrIndex.isEmpty() && keyOrIndex.charAt(0) >= '0' && keyOrIndex.charAt(0) <= '9') {
                            try {
                                realIndex = Integer.parseInt(keyOrIndex);
                            } catch (NumberFormatException e) {
                            }
                        }
                        if (realIndex >= 0) { //数组
                            String prefixKey = "";
                            for (int j = 0; j < i; j++) {
                                prefixKey += keys[j] + ".";
                            }
                            DefaultAnyValue array = prefixArray.get(prefixKey + item); //item: [1]
                            if (array == null) {
                                final int ii = i;
                                String findkey = prefixKey + itemField + "[";
                                Map<String, DefaultAnyValue> keymap = new TreeMap<>();
                                Map<Integer, DefaultAnyValue> sortmap = new TreeMap<>();
                                properties.keySet().stream().filter(x -> x.toString().startsWith(findkey)).forEach(k -> {
                                    String[] ks = k.toString().split("\\.");
                                    String prefixKey2 = "";
                                    for (int j = 0; j < ii; j++) {
                                        prefixKey2 += ks[j] + ".";
                                    }
                                    prefixKey2 += ks[ii];
                                    if (!keymap.containsKey(prefixKey2)) {
                                        DefaultAnyValue vv = new DefaultAnyValue();
                                        keymap.put(prefixKey2, vv);
                                        int aindex = Integer.parseInt(ks[ii].substring(ks[ii].indexOf('[') + 1, ks[ii].lastIndexOf(']')));
                                        vv.parentArrayIndex = aindex;
                                        sortmap.put(aindex, vv);
                                    }
                                });
                                prefixArray.putAll(keymap);
                                DefaultAnyValue pv = parent;
                                sortmap.values().forEach(v -> pv.addValue(itemField, v));
                                array = prefixArray.get(prefixKey + item);
                            }
                            parent = array;
                        } else { //Map
                            DefaultAnyValue field = (DefaultAnyValue) parent.getAnyValue(itemField);
                            if (field == null) {
                                field = new DefaultAnyValue();
                                parent.addValue(itemField, field);
                            }
                            DefaultAnyValue index = (DefaultAnyValue) field.getAnyValue(keyOrIndex);
                            if (index == null) {
                                index = new DefaultAnyValue();
                                if (nameName != null) index.setValue(nameName, keyOrIndex);
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
                String itemField = lastItem.substring(0, pos);  //[前面一部分
                String itemIndex = lastItem.substring(pos + 1, lastItem.indexOf(']'));
                if (!itemIndex.isEmpty() && itemIndex.charAt(0) >= '0' && itemIndex.charAt(0) <= '9') { //数组
                    //parent.addValue(itemField, value.toString());
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
                        properties.keySet().stream().filter(x -> x.toString().startsWith(findkey)).forEach(k -> {
                            String[] ks = k.toString().split("\\.");
                            String prefixKey2 = "";
                            for (int j = 0; j < ii; j++) {
                                prefixKey2 += ks[j] + ".";
                            }
                            prefixKey2 += ks[ii];
                            if (!keymap.containsKey(prefixKey2)) {
                                String vv = properties.getProperty(k.toString());
                                keymap.put(prefixKey2, vv);
                                sortmap.put(Integer.parseInt(ks[ii].substring(ks[ii].indexOf('[') + 1, ks[ii].lastIndexOf(']'))), vv);
                            }
                        });
                        DefaultAnyValue pv = parent;
                        sortmap.values().forEach(v -> pv.addValue(itemField, v));
                    }
                } else { //Map
                    DefaultAnyValue child = (DefaultAnyValue) parent.getAnyValue(itemField);
                    if (child == null) {
                        child = new DefaultAnyValue();
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
     *
     * @return AnyValue
     */
    public static AnyValue loadFromXml(String text) {
        return new XmlReader(text).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in 内容流
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in) throws IOException {
        return loadFromXml(in, StandardCharsets.UTF_8);
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in      内容流
     * @param charset 字符编码
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, Charset charset) throws IOException {
        return new XmlReader(Utility.read(in, charset)).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param text     文本内容
     * @param attrFunc 字段回调函数
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(String text, BiFunction<String, String, String> attrFunc) throws IOException {
        return new XmlReader(text).attrFunc(attrFunc).read();
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in       内容流
     * @param attrFunc 字段回调函数
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, BiFunction<String, String, String> attrFunc) throws IOException {
        return loadFromXml(in, StandardCharsets.UTF_8, attrFunc);
    }

    /**
     * xml内容流转换成AnyValue对象
     *
     * @param in       内容流
     * @param charset  字符编码
     * @param attrFunc 字段回调函数
     *
     * @return AnyValue
     * @throws IOException 异常
     */
    public static AnyValue loadFromXml(InputStream in, Charset charset, BiFunction<String, String, String> attrFunc) throws IOException {
        return new XmlReader(Utility.read(in, charset)).attrFunc(attrFunc).read();
    }

    /**
     * 当前AnyValue对象字符串化
     *
     * @param indent     缩进长度
     * @param prefixFunc 扩展函数
     *
     * @return String
     */
    public String toString(int indent, BiFunction<AnyValue, String, CharSequence> prefixFunc) { //indent: 缩进长度
        if (indent < 0) indent = 0;
        final String space = " ".repeat(indent);
        StringBuilder sb = new StringBuilder();
        sb.append("{\r\n");
        if (prefixFunc != null) {
            CharSequence v = prefixFunc.apply(this, space);
            if (v != null) sb.append(v);
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
                    sb.append(space).append("    '").append(en.name).append("': '").append(en.value).append("'");
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
                sb.append(space).append("    '").append(en.name).append("': ").append(en.value.toString(indent + 4, prefixFunc));
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
     *
     * @return AnyValue
     */
    public abstract AnyValue replace(AnyValue node);

    /**
     * 将另一个对象合并过来
     *
     * @param node 代合并对象
     * @param func 覆盖方式的函数
     *
     * @return AnyValue
     */
    public abstract AnyValue merge(AnyValue node, MergeFunction func);

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
     * @param anyConsumer    字符串对象的回调函数
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
    public abstract String[] getNames();

    /**
     * 获取同级下同一字段名下所有的String对象
     *
     * @param name 字段名
     *
     * @return String[]
     */
    public abstract String[] getValues(String name);

    /**
     * 根据字段名集合获取String类型的字段值集合
     *
     * @param names 字段名集合
     *
     * @return String[]
     */
    public abstract String[] getValues(String... names);

    /**
     * 获取同级下同一字段名下所有的AnyValue对象
     *
     * @param name 字段名
     *
     * @return AnyValue[]
     */
    public abstract AnyValue[] getAnyValues(String name);

    /**
     * 根据字段名集合获取AnyValue类型的字段值集合
     *
     * @param names 字段名集合
     *
     * @return AnyValue[]
     */
    public abstract AnyValue[] getAnyValues(String... names);

    /**
     * 根据字段名获取AnyValue类型的字段值
     *
     * @param name 字段名
     *
     * @return AnyValue
     */
    public abstract AnyValue getAnyValue(String name);

    /**
     * 根据字段名获取String类型的字段值
     *
     * @param name 字段名
     *
     * @return String
     */
    public abstract String getValue(String name);

    /**
     * 根据字段名获取String类型的字段值
     *
     * @param name 字段名
     *
     * @return String
     */
    public abstract String get(String name);

    /**
     * 获取字段值
     *
     * @param name 字段名
     *
     * @return 字段值
     */
    public boolean getBoolValue(String name) {
        return Boolean.parseBoolean(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
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
     *
     * @return 字段值
     */
    public byte getByteValue(String name) {
        return Byte.parseByte(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public byte getByteValue(String name, byte defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Byte.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix        进制，默认十进制
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public byte getByteValue(int radix, String name, byte defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
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
     *
     * @return 字段值
     */
    public char getCharValue(String name) {
        return getValue(name).charAt(0);
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
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
     *
     * @return String
     */
    public short getShortValue(String name) {
        return Short.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public short getShortValue(String name, short defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Short.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix        进制，默认十进制
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public short getShortValue(int radix, String name, short defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
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
     *
     * @return 字段值
     */
    public int getIntValue(String name) {
        return Integer.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return String
     */
    public int getIntValue(String name, int defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Integer.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix        进制，默认十进制
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public int getIntValue(int radix, String name, int defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
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
     *
     * @return 字段值
     */
    public long getLongValue(String name) {
        return Long.decode(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public long getLongValue(String name, long defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Long.decode(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param radix        进制，默认十进制
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public long getLongValue(int radix, String name, long defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
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
     *
     * @return String
     */
    public float getFloatValue(String name) {
        return Float.parseFloat(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public float getFloatValue(String name, float defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
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
     *
     * @return 字段值
     */
    public double getDoubleValue(String name) {
        return Double.parseDouble(getValue(name));
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public double getDoubleValue(String name, double defaultValue) {
        String value = getValue(name);
        if (value == null || value.length() == 0) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public String getValue(String name, String defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : value;
    }

    /**
     * 获取字段值
     *
     * @param name         字段名
     * @param defaultValue 默认值
     *
     * @return 字段值
     */
    public String getOrDefault(String name, String defaultValue) {
        String value = getValue(name);
        return value == null ? defaultValue : value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof AnyValue)) return false;
        AnyValue conf = (AnyValue) other;
        if (!equals(this.getStringEntrys(), conf.getStringEntrys())) return false;
        return equals(this.getAnyEntrys(), conf.getAnyEntrys());
    }

    private static <T> boolean equals(Entry<? extends T>[] entry1, Entry<T>[] entry2) {
        if ((entry1 == null || entry1.length == 0) && (entry2 == null || entry2.length == 0)) return true;
        if (entry1.length != entry2.length) return false;
        for (int i = 0; i < entry1.length; i++) {
            if (!entry1[i].name.equals(entry2[i].name)) return false;
            if (!entry1[i].getValue().equals(entry2[i].getValue())) return false;
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
     *
     * @return String
     */
    public String toXML(String rootName) {
        return toXMLString(new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n\r\n"), rootName, this, 0).toString();
    }

    /**
     * xml化AnyValue对象
     *
     * @param sb       StringBuilder
     * @param nodeName 字段名
     * @param conf     AnyValue
     * @param indent   缩进长度
     *
     * @return StringBuilder
     */
    protected static StringBuilder toXMLString(StringBuilder sb, String nodeName, AnyValue conf, int indent) { //indent: 缩进长度
        if (indent < 0) indent = 0;
        final String space = " ".repeat(indent);
        Entry<AnyValue>[] anys = conf.getAnyEntrys();
        sb.append(space).append('<').append(nodeName);
        for (Entry<String> en : conf.getStringEntrys()) {
            sb.append(' ').append(en.name).append("=\"").append(en.value).append("\"");
        }
        if (anys == null || anys.length == 0) return sb.append("/>\r\n\r\n");
        sb.append(">\r\n\r\n");
        for (Entry<AnyValue> en : conf.getAnyEntrys()) {
            toXMLString(sb, en.name, en.getValue(), indent + 4);
        }
        return sb.append(space).append("</").append(nodeName).append(">\r\n\r\n");
    }

}
