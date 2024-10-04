/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.redkale.annotation.Nonnull;
import org.redkale.convert.*;
import org.redkale.util.Attribute;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapEncoder<K, V> extends MapEncoder<ProtobufWriter, K, V>
        implements ProtobufEncodeable<ProtobufWriter, Map<K, V>> {

    private final EnMember keyMember;

    private final EnMember valueMember;
    private final boolean keySimpled;
    private final boolean valueSimpled;

    public ProtobufMapEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.keyMember = new EnMember(createAttribute("key", keyEncoder.getType()), keyEncoder);
        this.valueMember = new EnMember(createAttribute("value", valueEncoder.getType()), valueEncoder);
        boolean enumtostring = ((ProtobufFactory) factory).enumtostring;
        setTag(keyMember, ProtobufFactory.getTag("key", keyEncoder.getType(), 1, enumtostring));
        setTag(valueMember, ProtobufFactory.getTag("value", valueEncoder.getType(), 2, enumtostring));
        setTagSize(keyMember, ProtobufFactory.computeSInt32SizeNoTag(keyMember.getTag()));
        setTagSize(valueMember, ProtobufFactory.computeSInt32SizeNoTag(valueMember.getTag()));
        this.keySimpled = keyEncoder instanceof SimpledCoder;
        this.valueSimpled = valueEncoder instanceof SimpledCoder;
    }

    @Override
    public void convertTo(final ProtobufWriter out, @Nonnull EnMember member, Map<K, V> value) {
        this.checkInited();
        final Map<K, V> values = value;
        if (Utility.isEmpty(value)) {
            out.writeNull();
            return;
        }
        Set<String> ignoreColumns = this.ignoreMapColumns;
        BiFunction<K, V, V> mapFieldFunc = out.mapFieldFunc();
        ProtobufEncodeable kencoder = (ProtobufEncodeable) this.keyEncoder;
        ProtobufEncodeable vencoder = (ProtobufEncodeable) this.valueEncoder;
        out.writeMapB(values.size(), kencoder, vencoder, value);
        AtomicBoolean first = new AtomicBoolean(true);
        values.forEach((key, val0) -> {
            if (ignoreColumns == null || !ignoreColumns.contains(key.toString())) {
                V val = mapFieldFunc == null ? val0 : mapFieldFunc.apply(key, val0);
                if (!first.get()) {
                    out.writeField(member);
                }
                boolean poll = true;
                ProtobufWriter subout = poll ? out.pollChild() : out;
                if (!poll) {
                    subout.writeLength(computeSize(out, key, val));
                }
                subout.writeTag(keyMember.getTag());
                if (key == null) {
                    subout.writeLength(0);
                } else {
                    kencoder.convertTo(subout, keyMember, key);
                }
                subout.writeTag(valueMember.getTag());
                if (val == null) {
                    subout.writeLength(0);
                } else {
                    vencoder.convertTo(subout, valueMember, val);
                }
                if (poll) {
                    out.offerChild(subout);
                }
                first.set(false);
            }
        });
        out.writeMapE();
    }

    public int computeSize(ProtobufWriter out, K key, V val) {
        ProtobufEncodeable kencoder = (ProtobufEncodeable) this.keyEncoder;
        ProtobufEncodeable vencoder = (ProtobufEncodeable) this.valueEncoder;
        int keySize = kencoder.computeSize(out, keyMember.getTagSize(), key);
        int valSize = vencoder.computeSize(out, valueMember.getTagSize(), val);
        return (keyMember.getTagSize() + keySize) + (valueMember.getTagSize() + valSize);
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagSize, Map<K, V> value) {
        if (Utility.isEmpty(value)) {
            return 0;
        }
        Set<String> ignoreColumns = this.ignoreMapColumns;
        BiFunction<K, V, V> mapFieldFunc = out.mapFieldFunc();
        AtomicBoolean first = new AtomicBoolean();
        AtomicInteger size = new AtomicInteger();
        value.forEach((key, val0) -> {
            if (ignoreColumns == null || !ignoreColumns.contains(key.toString())) {
                V val = mapFieldFunc == null ? val0 : mapFieldFunc.apply(key, val0);
                if (val != null) {
                    if (!first.get()) {
                        size.addAndGet(tagSize);
                    }
                    size.addAndGet(computeSize(out, key, val));
                    first.set(false);
                }
            }
        });
        return size.get();
    }

    static Attribute createAttribute(String field, Type type) {
        return Attribute.create(Map.class, field, TypeToken.typeToClass(type), type, null, null, null);
    }
}
