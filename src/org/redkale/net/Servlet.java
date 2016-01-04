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
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 * @param <R> Request的子类型
 * @param <P> Response的子类型
 */
public abstract class Servlet<R extends Request, P extends Response<R>> {

    public void init(Context context, AnyValue config) {
    }

    public abstract void execute(R request, P response) throws IOException;

    public void destroy(Context context, AnyValue config) {
    }

}
