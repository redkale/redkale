/*
 *
 */
package org.redkale.util;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.ConvertDisabled;

/**
 * AnyValue的可写版
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
@SuppressWarnings("unchecked")
public class AnyValueWriter extends AnyValue {

    /** 区分name大小写的比较策略 */
    public static final BiPredicate<String, String> EQUALS_PREDICATE = (name1, name2) -> name1.equals(name2);

    /** 不区分name大小写的比较策略 */
    public static final BiPredicate<String, String> EQUALS_IGNORE = (name1, name2) -> name1.equalsIgnoreCase(name2);

    @ConvertColumn(index = 1)
    private boolean ignoreCase;

    private BiPredicate<String, String> predicate;

    @ConvertColumn(index = 2)
    private Entry<String>[] stringEntrys = new Entry[0];

    @ConvertColumn(index = 3)
    private Entry<AnyValue>[] anyEntrys = new Entry[0];

    int parentArrayIndex = -1; // 只可能被loadFromProperties方法赋值

    /**
     * 创建含name-value值的AnyValueWriter
     *
     * @param name name
     * @param value value值
     * @return AnyValueWriter
     */
    public static final AnyValueWriter create(String name, Number value) {
        AnyValueWriter conf = new AnyValueWriter();
        conf.addValue(name, value);
        return conf;
    }

    /**
     * 创建含name-value值的AnyValueWriter对象
     *
     * @param name name
     * @param value value值
     * @return AnyValueWriter对象
     */
    public static final AnyValueWriter create(String name, String value) {
        AnyValueWriter conf = new AnyValueWriter();
        conf.addValue(name, value);
        return conf;
    }

    /**
     * 创建含name-value值的AnyValueWriter对象
     *
     * @param name name
     * @param value value值
     * @return AnyValueWriter对象
     */
    public static final AnyValueWriter create(String name, AnyValue value) {
        AnyValueWriter conf = new AnyValueWriter();
        conf.addValue(name, value);
        return conf;
    }

    /** 创建一个区分大小写比较策略的AnyValueWriter对象 */
    public AnyValueWriter() {
        this(false);
    }

    /**
     * 创建AnyValueWriter对象
     *
     * @param ignoreCase name是否不区分大小写
     */
    public AnyValueWriter(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        this.predicate = ignoreCase ? EQUALS_IGNORE : EQUALS_PREDICATE;
    }

    /**
     * 创建共享此内容的AnyValueWriter对象
     *
     * @return AnyValueWriter对象
     */
    public AnyValueWriter duplicate() {
        AnyValueWriter rs = new AnyValueWriter(this.ignoreCase);
        rs.stringEntrys = this.stringEntrys;
        rs.anyEntrys = this.anyEntrys;
        return rs;
    }

    /**
     * 复制一份对象
     *
     * @return AnyValueWriter对象
     */
    @Override
    public AnyValueWriter copy() {
        AnyValueWriter rs = new AnyValueWriter(this.ignoreCase);
        rs.predicate = this.predicate;
        rs.parentArrayIndex = this.parentArrayIndex;
        if (this.stringEntrys != null) {
            rs.stringEntrys = new Entry[this.stringEntrys.length];
            for (int i = 0; i < rs.stringEntrys.length; i++) {
                Entry<String> en = this.stringEntrys[i];
                if (en == null) {
                    continue;
                }
                rs.stringEntrys[i] = new Entry(en.name, en.value);
            }
        }
        if (this.anyEntrys != null) {
            rs.anyEntrys = new Entry[this.anyEntrys.length];
            for (int i = 0; i < rs.anyEntrys.length; i++) {
                Entry<AnyValue> en = this.anyEntrys[i];
                if (en == null) {
                    continue;
                }
                rs.anyEntrys[i] = new Entry(en.name, en.value == null ? null : en.value.copy());
            }
        }
        return rs;
    }

    /**
     * 将另一个对象替换本对象
     *
     * @param node 替换的对象
     * @return AnyValue
     */
    @Override
    public AnyValueWriter replace(AnyValue node) {
        if (node != null) {
            AnyValueWriter rs = (AnyValueWriter) node;
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
     * @return AnyValue
     */
    @Override
    public AnyValueWriter merge(AnyValue node, MergeStrategy func) {
        return merge(node, "", func);
    }

    protected AnyValueWriter merge(AnyValue node0, String path, MergeStrategy func) {
        if (node0 == null) {
            return this;
        }
        if (node0 == this) {
            throw new IllegalArgumentException();
        }
        AnyValueWriter node = (AnyValueWriter) node0;
        if (node.stringEntrys != null) {
            for (Entry<String> en : node.stringEntrys) {
                if (en == null) {
                    continue;
                }
                setValue(en.name, en.value);
            }
        }
        if (node.anyEntrys != null) {
            for (Entry<AnyValue> en : node.anyEntrys) {
                if (en == null || en.value == null) {
                    continue;
                }
                Entry<AnyValue>[] ns = getAnyValueEntrys(en.name);
                if (Utility.isEmpty(ns)) {
                    addValue(en.name, en.value);
                } else {
                    boolean ok = false;
                    for (Entry<AnyValue> item : ns) {
                        if (item == null) {
                            continue;
                        }
                        if (item.value != null
                                && ((AnyValueWriter) en.value).parentArrayIndex
                                        == ((AnyValueWriter) item.value).parentArrayIndex) {
                            if (func == null) {
                                item.value.merge(en.value, func);
                                ok = true;
                                break;
                            } else {
                                MergeEnum funcVal = func.apply(path, en.name, en.value, item.value);
                                if (funcVal == MergeEnum.MERGE) {
                                    String subPath = path.isEmpty() ? en.name : (path + "." + en.name);
                                    ((AnyValueWriter) item.value).merge(en.value, subPath, func);
                                    ok = true;
                                    break;
                                } else if (funcVal == MergeEnum.REPLACE) {
                                    item.value = en.value.copy();
                                    ok = true;
                                    break;
                                } else if (funcVal == MergeEnum.IGNORE) {
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
     * @return AnyValueWriter
     */
    public AnyValueWriter addAllStringSet(final AnyValue av) {
        if (av == null) {
            return this;
        }
        final Entry<String>[] strings = av.getStringEntrys();
        if (strings == null) {
            return this;
        }
        for (Entry<String> en : strings) {
            if (!existsValue(en.name)) {
                this.addValue(en.name, en.value);
            }
        }
        return this;
    }

    /**
     * 合并两个AnyValue对象 不去重
     *
     * @param av AnyValue
     * @return AnyValueWriter
     */
    public AnyValueWriter addAll(final AnyValue av) {
        if (av == null) {
            return this;
        }
        if (av instanceof AnyValueWriter) {
            final AnyValueWriter adv = (AnyValueWriter) av;
            if (adv.stringEntrys != null) {
                for (Entry<String> en : adv.stringEntrys) {
                    this.addValue(en.name, en.value);
                }
            }
            if (adv.anyEntrys != null) {
                for (Entry<AnyValue> en : adv.anyEntrys) {
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
     * @return AnyValueWriter
     */
    @ConvertDisabled
    public AnyValueWriter setAll(final AnyValue av) {
        if (av == null) {
            return this;
        }
        if (av instanceof AnyValueWriter) {
            final AnyValueWriter adv = (AnyValueWriter) av;
            if (adv.stringEntrys != null) {
                for (Entry<String> en : adv.stringEntrys) {
                    this.setValue(en.name, en.value);
                }
            }
            if (adv.anyEntrys != null) {
                for (Entry<AnyValue> en : adv.anyEntrys) {
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

    public void clearStringEntrys() {
        this.stringEntrys = new Entry[0];
    }

    @Override
    public Entry<AnyValue>[] getAnyEntrys() {
        return (Entry<AnyValue>[]) (Entry[]) anyEntrys;
    }

    public void setAnyEntrys(Entry<AnyValue>[] anyEntrys) {
        this.anyEntrys = anyEntrys;
    }

    public void clearAnyEntrys() {
        this.anyEntrys = new Entry[0];
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        if (this.predicate == null) {
            this.predicate = ignoreCase ? EQUALS_IGNORE : EQUALS_PREDICATE;
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
            int index = ((AnyValueWriter) any).parentArrayIndex;
            if (index < 0) {
                return null; // 不能用"null"
            }
            return new StringBuilder()
                    .append(space)
                    .append("    '$index': ")
                    .append(index)
                    .append(",\r\n");
        });
    }

    public AnyValueWriter clear() {
        if (this.stringEntrys != null && this.stringEntrys.length > 0) {
            this.stringEntrys = new Entry[0];
        }
        if (this.anyEntrys != null && this.anyEntrys.length > 0) {
            this.anyEntrys = new Entry[0];
        }
        return this;
    }

    public AnyValueWriter setValue(String name, String value) {
        Objects.requireNonNull(name);
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

    public AnyValueWriter setValue(String name, AnyValue value) {
        Objects.requireNonNull(name);
        if (!existsValue(name)) {
            this.addValue(name, value);
        } else {
            for (Entry<AnyValue> en : this.anyEntrys) {
                if (predicate.test(en.name, name)) {
                    en.value = (AnyValueWriter) value;
                    return this;
                }
            }
        }
        return this;
    }

    public AnyValueWriter put(String name, boolean value) {
        return addValue(name, String.valueOf(value));
    }

    public AnyValueWriter put(String name, Number value) {
        return addValue(name, String.valueOf(value));
    }

    public AnyValueWriter put(String name, String value) {
        this.stringEntrys = Utility.append(this.stringEntrys, new Entry(name, value));
        return this;
    }

    public AnyValueWriter addValue(String name, boolean value) {
        return addValue(name, String.valueOf(value));
    }

    public AnyValueWriter addValue(String name, Number value) {
        return addValue(name, String.valueOf(value));
    }

    public AnyValueWriter addValue(String name, String value) {
        Objects.requireNonNull(name);
        this.stringEntrys = Utility.append(this.stringEntrys, new Entry(name, value));
        return this;
    }

    public AnyValueWriter addValue(String name, AnyValue value) {
        Objects.requireNonNull(name);
        this.anyEntrys = Utility.append(this.anyEntrys, new Entry(name, value));
        return this;
    }

    public void clearParentArrayIndex(String name) {
        for (Entry<AnyValue> item : getAnyValueEntrys(name)) {
            if (item.value != null) {
                ((AnyValueWriter) item.value).parentArrayIndex = -1;
            }
        }
    }

    public AnyValueWriter removeAnyValues(String name) {
        Objects.requireNonNull(name);
        if (this.anyEntrys == null) {
            return this;
        }
        this.anyEntrys = Utility.remove(this.anyEntrys, t -> name.equals(((Entry) t).name));
        return this;
    }

    public AnyValueWriter removeValue(String name, AnyValue value) {
        Objects.requireNonNull(name);
        if (value == null || this.anyEntrys == null) {
            return this;
        }
        this.anyEntrys = Utility.remove(
                this.anyEntrys,
                t -> name.equals(((Entry) t).name) && ((Entry) t).getValue().equals(value));
        return this;
    }

    public AnyValueWriter removeStringValues(String name) {
        Objects.requireNonNull(name);
        if (this.stringEntrys == null) {
            return this;
        }
        this.stringEntrys = Utility.remove(this.stringEntrys, t -> name.equals(((Entry) t).name));
        return this;
    }

    public AnyValueWriter removeValue(String name, String value) {
        Objects.requireNonNull(name);
        if (value == null || this.stringEntrys == null) {
            return this;
        }
        this.stringEntrys = Utility.remove(
                this.stringEntrys,
                t -> name.equals(((Entry) t).name) && ((Entry) t).getValue().equals(value));
        return this;
    }

    @Override
    public AnyValue getAnyValue(String name) {
        return getAnyValue(name, false);
    }

    @Override
    public AnyValue getAnyValue(String name, boolean create) {
        for (Entry<AnyValue> en : this.anyEntrys) {
            if (predicate.test(en.name, name)) {
                return en.value;
            }
        }
        return create ? new AnyValueWriter() : null;
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
