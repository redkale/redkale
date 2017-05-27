/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.service.*;
import org.redkale.util.Comment;

/**
 *
 * @author zhangjx
 */
public class ChatService implements Service {

    private final Map<Integer, Integer> rooms = new ConcurrentHashMap<>();

    protected final AtomicInteger idcreator = new AtomicInteger(10000);

    public int createGroupid() {
        int v = idcreator.incrementAndGet();
        setIdcreator(v);
        return v;
    }

    @Comment("同步到其他服务的idcreator")
    @RpcMultiRun
    public void setIdcreator(int v) {
        idcreator.set(v);
    }

    public boolean joinRoom(int userid, int roomid) {
        rooms.put(userid, roomid);
        return true;
    }
}
