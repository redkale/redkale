/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert;

import java.lang.reflect.Type;
import org.redkale.util.AnyValue;

/**
 * AnyValue的Encoder实现
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <W> Writer输出的子类
 *
 * @since 2.5.0
 */
public class AnyValueEncoder<W extends Writer> implements Encodeable<W, AnyValue> {

    @Override
    public void convertTo(W out, AnyValue value) {
        //do nothing
    }

    @Override
    public Type getType() {
        return AnyValue.class;
    }

}
