/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.websocket.dyn;

import org.redkale.convert.ConvertColumn;

/**
 *
 * @author zhangjx
 */
public class GameRoomBean {
    @ConvertColumn(index = 1)
    public int roomid;

    @ConvertColumn(index = 2)
    public String roomName;

    @Override
    public String toString() {
        return "RoomBean{" + "roomid=" + roomid + ", roomName=" + roomName + '}';
    }
}
