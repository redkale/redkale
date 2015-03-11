/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.bson;

import com.wentch.redkale.convert.ConvertType;
import com.wentch.redkale.convert.Factory;
import java.io.Serializable;

/**
 *
 * @author zhangjx
 */
public final class BsonFactory extends Factory<BsonReader, BsonWriter> {

    private static final BsonFactory instance = new BsonFactory(null);

    static {
        instance.register(Serializable.class, instance.loadDecoder(Object.class));
        instance.register(Serializable.class, instance.loadEncoder(Object.class));
    }

    private BsonFactory(BsonFactory parent) {
        super(parent);
        this.convert = new BsonConvert(this);
    }

    public static BsonFactory root() {
        return instance;
    }

    @Override
    public final BsonConvert getConvert() {
        return (BsonConvert) convert;
    }

    @Override
    public BsonFactory createChild() {
        return new BsonFactory(this);
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
