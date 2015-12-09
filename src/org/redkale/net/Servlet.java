/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import org.redkale.util.AnyValue;
import java.io.IOException;

/**
 *
 * @author zhangjx
 * @param <R>
 * @param <P>
 */
public interface Servlet<R extends Request, P extends Response<R>> {

    default void init(Context context, AnyValue config) {
    }

    public void execute(R request, P response) throws IOException;

    default void destroy(Context context, AnyValue config) {
    }

}
