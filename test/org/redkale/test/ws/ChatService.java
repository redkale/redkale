/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.service.Service;

/**
 *
 * @author zhangjx
 */
public class ChatService implements Service {

    private final Map<Integer, Integer> rooms = new ConcurrentHashMap<>();

    public boolean joinRoom(int userid, int roomid) {
        rooms.put(userid, roomid);
        return true;
    }
}
