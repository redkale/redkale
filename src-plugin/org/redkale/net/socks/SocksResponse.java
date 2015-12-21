/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.socks;

import org.redkale.net.AsyncConnection;
import org.redkale.util.ObjectPool;
import org.redkale.net.http.HttpResponse;
import org.redkale.util.Creator;
import org.redkale.net.Response;
import java.util.concurrent.atomic.*;

/**
 *
 * @see http://www.redkale.org
 * @author zhangjx
 */
public class SocksResponse extends HttpResponse<SocksRequest> {

    protected SocksResponse(SocksContext context, SocksRequest request) {
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
