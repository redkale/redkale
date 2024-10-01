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
import org.redkale.util.Utility;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapEncoder<K, V> extends MapEncoder<ProtobufWriter, K, V>
        implements ProtobufEncodeable<ProtobufWriter, Map<K, V>> {

    private final int keyTag;
    private final int valTag;

    public ProtobufMapEncoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.keyTag = ProtobufFactory.getTag(1, ((ProtobufEncodeable) keyEncoder).typeEnum());
        this.valTag = ProtobufFactory.getTag(2, ((ProtobufEncodeable) valueEncoder).typeEnum());
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
        Encodeable kencoder = this.keyEncoder;
        Encodeable vencoder = this.valueEncoder;
        out.writeMapB(values.size(), kencoder, vencoder, value);
        values.forEach((key, val) -> {
            if (ignoreColumns == null || !ignoreColumns.contains(key)) {
                V v = mapFieldFunc == null ? val : mapFieldFunc.apply(key, val);
                if (v != null) {
                    out.writeField(member);

                    ProtobufWriter tmp = out.pollChild();
                    tmp.writeTag(keyTag);
                    kencoder.convertTo(tmp, key);
                    tmp.writeTag(valTag);
                    vencoder.convertTo(tmp, v);

                    out.writeTuple(tmp);
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
}
