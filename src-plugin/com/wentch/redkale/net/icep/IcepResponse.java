/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.icep;

import com.wentch.redkale.net.*;
import com.wentch.redkale.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public class IcepResponse extends Response<IcepRequest> {

    protected IcepResponse(Context context, IcepRequest request) {
        super(context, request);
    }
    
    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((IcepResponse) x).prepare(), (x) -> ((IcepResponse) x).recycle());
    }
}
