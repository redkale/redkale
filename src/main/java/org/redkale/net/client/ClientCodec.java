/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.nio.ByteBuffer;
import java.util.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.ByteArray;

/**
 * 每个ClientConnection绑定一个独立的ClientCodec实例
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 * @param <R> ClientRequest
 * @param <P> 响应对象
 */
public abstract class ClientCodec<R extends ClientRequest, P> {

    protected final List<ClientResult<P>> results = new ArrayList<>();

    public ClientCodec() {
    }

    //返回true: array会clear, 返回false: buffer会clear
    public abstract boolean codecResult(ClientConnection conn, ByteBuffer buffer, ByteArray array);

    protected Queue<ClientFuture> responseQueue(ClientConnection conn) {
        return conn.responseQueue;
    }

    public List<ClientResult<P>> removeResults() {
        if (results.isEmpty()) return null;
        List<ClientResult<P>> rs = new ArrayList<>(results);
        this.results.clear();
        return rs;
    }

    public void addResult(P result) {
        this.results.add(new ClientResult<>(result));
    }

    public void addResult(Throwable exc) {
        this.results.add(new ClientResult<>(exc));
    }

    public void reset() {
        this.results.clear();
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

}
