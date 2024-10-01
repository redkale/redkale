/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
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

    public ProtobufMapEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.keyMember = new EnMember(createAttribute("key", keyEncoder.getType()), keyEncoder);
        this.valueMember = new EnMember(createAttribute("value", valueEncoder.getType()), valueEncoder);
        setTag(keyMember, ProtobufFactory.getTag(1, ((ProtobufEncodeable) keyEncoder).typeEnum()));
        setTag(valueMember, ProtobufFactory.getTag(2, ((ProtobufEncodeable) valueEncoder).typeEnum()));
        setTagSize(keyMember, ProtobufFactory.computeSInt32SizeNoTag(keyMember.getTag()));
        setTagSize(valueMember, ProtobufFactory.computeSInt32SizeNoTag(valueMember.getTag()));
    }

    @Override
    public void convertTo(ProtobufWriter out, EnMember member, Map<K, V> value) {
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
        boolean keySimpled = kencoder instanceof SimpledCoder;
        boolean valSimpled = vencoder instanceof SimpledCoder;
        out.writeMapB(values.size(), kencoder, vencoder, value);
        values.forEach((key, val0) -> {
            if (ignoreColumns == null || !ignoreColumns.contains(key)) {
                V val = mapFieldFunc == null ? val0 : mapFieldFunc.apply(key, val0);
                if (val != null) {
                    out.writeField(member);
                    ProtobufWriter tmp = out.pollChild();
                    if (keySimpled) {
                        tmp.writeField(keyMember);
                        kencoder.convertTo(tmp, key);
                    } else {
                        kencoder.convertTo(tmp, keyMember, key);
                    }
                    if (valSimpled) {
                        tmp.writeField(valueMember);
                        vencoder.convertTo(tmp, val);
                    } else {
                        vencoder.convertTo(tmp, valueMember, val);
                    }
                    out.offerChild(tmp);
                }
            }
        });
        out.writeMapE();
    }

    @Override
    public int computeSize(ProtobufWriter out, int tagLen, Map<K, V> value) {
        if (Utility.isEmpty(value)) {
            return 0;
        }
        return 0;
    }

    @Override
    public final boolean requireSize() {
        return true;
    }

    @Override
    public final ProtobufTypeEnum typeEnum() {
        return ProtobufTypeEnum.BYTES;
    }

    static Attribute createAttribute(String field, Type type) {
        return Attribute.create(Map.class, field, TypeToken.typeToClass(type), type, null, null, null);
    }
}
