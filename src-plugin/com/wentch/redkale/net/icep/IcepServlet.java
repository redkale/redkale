/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public abstract class IcepServlet implements Servlet<IcepRequest, IcepResponse> {

    AnyValue conf; //当前Servlet的配置

    public abstract short getRequestid();

    @Override
    public final boolean equals(Object obj) {
        return obj != null && obj.getClass() == this.getClass();
    }

    @Override
    public final int hashCode() {
        return this.getClass().hashCode();
    }
}
