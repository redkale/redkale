/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.pb;

import java.lang.reflect.Type;
import org.redkale.convert.*;

/**
 * @author zhangjx
 * @param <K> K
 * @param <V> V
 */
public class ProtobufMapDecoder<K, V> extends MapDecoder<ProtobufReader, K, V> {

    protected final boolean enumtostring;

    public ProtobufMapDecoder(ConvertFactory factory, Type type) {
        super(factory, type);
        this.enumtostring = ((ProtobufFactory) factory).enumtostring;
    }

    @Override
    protected ProtobufReader getEntryReader(ProtobufReader in, DeMember member, boolean first) {
        if (!first && member != null) {
            int tag = in.readTag();
            if (tag != member.getTag()) {
                in.backTag(tag);
                return null;
            }
        }
        byte[] bs = in.readByteArray();
        return new ProtobufReader(bs);
    }

    @Override
    protected K readKeyMember(
            ProtobufReader in, DeMember member, Decodeable<ProtobufReader, K> decoder, boolean first) {
        in.readTag();
        return decoder.convertFrom(in);
    }

    @Override
    protected V readValueMember(
            ProtobufReader in, DeMember member, Decodeable<ProtobufReader, V> decoder, boolean first) {
        in.readTag();
        return decoder.convertFrom(in);
    }
}
