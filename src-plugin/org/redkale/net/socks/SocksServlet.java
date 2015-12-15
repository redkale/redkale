/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.util.AnyValue;
import org.redkale.net.Servlet;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public abstract class SocksServlet implements Servlet<SocksRequest, SocksResponse> {

    AnyValue conf; //当前Servlet的配置

    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public final int hashCode() {
        return this.getClass().hashCode();
    }
}
