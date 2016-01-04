/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import org.redkale.convert.Reader;
import org.redkale.convert.SimpledCoder;
import org.redkale.convert.Writer;

/**
 *
 * <p> 详情见: http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class DoubleSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, Double> {

    public static final DoubleSimpledCoder instance = new DoubleSimpledCoder();

    @Override
    public void convertTo(W out, Double value) {
        out.writeDouble(value);
    }

    @Override
    public Double convertFrom(R in) {
        return in.readDouble();
    }

}
