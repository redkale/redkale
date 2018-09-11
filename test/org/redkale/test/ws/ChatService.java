/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Resource;
import org.redkale.net.http.WebSocketNode;
import org.redkale.service.*;
import org.redkale.util.Comment;

/**
 *
 * @author zhangjx
 */
public class ChatService implements Service {

    @Comment("key=用户ID，value=房间ID")
    private final Map<Integer, Integer> userToRooms = new ConcurrentHashMap<>();

    @Comment("key=房间ID，value=用户ID列表")
    private final Map<Integer, List<Integer>> roomToUsers = new ConcurrentHashMap<>();

    protected final AtomicInteger idcreator = new AtomicInteger(10000);

    @Resource(name = "chat")
    protected WebSocketNode wsnode;

    @Comment("创建一个用户ID")
    public int createUserid() {
        return idcreator.incrementAndGet();
    }

    @Comment("用户加入指定房间")
    public boolean joinRoom(int userid, int roomid) {
        userToRooms.put(userid, roomid);
        roomToUsers.computeIfAbsent(roomid, (id) -> new CopyOnWriteArrayList()).add(userid);
        System.out.println("加入房间: roomid: " + roomid);
        return true;
    }

    public void chatMessage(ChatMessage message) {
        wsnode.broadcastMessage(message);
    }
}
