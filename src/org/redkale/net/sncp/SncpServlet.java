/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.net.Servlet;
import org.redkale.util.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public abstract class SncpServlet extends Servlet<SncpRequest, SncpResponse> {

    AnyValue conf;

    public abstract DLong getNameid();

    public abstract DLong getServiceid();

    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public final int hashCode() {
        return this.getClass().hashCode();
    }
}
