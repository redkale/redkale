/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.socks;

import com.wentch.redkale.net.*;
import com.wentch.redkale.net.http.*;
import com.wentch.redkale.util.*;
import java.util.concurrent.atomic.*;

/**
 *
 * @author zhangjx
 */
public class SocksResponse extends HttpResponse<SocksRequest> {

    protected SocksResponse(Context context, SocksRequest request) {
        super(context, request, (String[][]) null, (String[][]) null, null);
    }

    public static ObjectPool<Response> createPool(AtomicLong creatCounter, AtomicLong cycleCounter, int max, Creator<Response> creator) {
        return new ObjectPool<>(creatCounter, cycleCounter, max, creator, (x) -> ((SocksResponse) x).prepare(), (x) -> ((SocksResponse) x).recycle());
    }

    @Override
    public AsyncConnection removeChannel() {
        return super.removeChannel();
    }

}
