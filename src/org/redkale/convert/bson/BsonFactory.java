/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.Serializable;
import org.redkale.convert.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public final class BsonFactory extends Factory<BsonReader, BsonWriter> {

    private static final BsonFactory instance = new BsonFactory(null, Boolean.getBoolean("convert.bson.tiny"));

    static final Decodeable objectDecoder = instance.loadDecoder(Object.class);

    static final Encodeable objectEncoder = instance.loadEncoder(Object.class);

    static {
        instance.register(Serializable.class, objectDecoder);
        instance.register(Serializable.class, objectEncoder);
    }

    private BsonFactory(BsonFactory parent, boolean tiny) {
        super(parent, tiny);
    }

    public static BsonFactory root() {
        return instance;
    }

    @Override
    public final BsonConvert getConvert() {
        if (convert == null) convert = new BsonConvert(this, tiny);
        return (BsonConvert) convert;
    }

    @Override
    public BsonFactory createChild() {
        return new BsonFactory(this, this.tiny);
    }

    @Override
    public BsonFactory createChild(boolean tiny) {
        return new BsonFactory(this, tiny);
    }

    @Override
    public ConvertType getConvertType() {
        return ConvertType.BSON;
    }

    @Override
    public boolean isReversible() {
        return true;
    }

}
