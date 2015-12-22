/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.ext;

import java.nio.channels.*;
import org.redkale.convert.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <R>
 * @param <W>
 */
public final class CompletionHandlerSimpledCoder<R extends Reader, W extends Writer> extends SimpledCoder<R, W, CompletionHandler> {

    public static final CompletionHandlerSimpledCoder instance = new CompletionHandlerSimpledCoder();

    @Override
    public void convertTo(W out, CompletionHandler value) {
        out.writeNull();
    }

    @Override
    public CompletionHandler convertFrom(R in) {
        in.readObjectB();
        return null;
    }

}
