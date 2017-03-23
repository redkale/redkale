/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import static org.redkale.net.http.WebSocket.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.redkale.net.http.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@AutoLoad(false)
@ResourceType({WebSocketNodeService.class, WebSocketNode.class})
public class WebSocketNodeService extends WebSocketNode implements Service {

    @Override
    public void init(AnyValue conf) {
        super.init(conf);
    }

    @Override
    public void destroy(AnyValue conf) {
        super.destroy(conf);
    }

    @Override
    public List<String> getOnlineRemoteAddresses(@RpcTargetAddress InetSocketAddress targetAddress, Serializable groupid) {
        if (localSncpAddress == null || !localSncpAddress.equals(targetAddress)) return remoteOnlineRemoteAddresses(targetAddress, groupid);
        final Set<String> engineids = localNodes.get(groupid);
        if (engineids == null || engineids.isEmpty()) return null;
        final List<String> rs = new ArrayList<>();
        for (String engineid : engineids) {
            final WebSocketEngine engine = engines.get(engineid);
            if (engine == null) continue;
            final WebSocketGroup group = engine.getWebSocketGroup(groupid);
            group.getWebSockets().forEach(x -> rs.add("ws" + Objects.hashCode(x) + '@' + x.getRemoteAddr()));
        }
        return rs;
    }

    @Override
    public int sendMessage(@RpcTargetAddress InetSocketAddress addr, Serializable groupid, boolean recent, Serializable message, boolean last) {
        final Set<String> engineids = localNodes.get(groupid);
        if (engineids == null || engineids.isEmpty()) return RETCODE_GROUP_EMPTY;
        int code = RETCODE_GROUP_EMPTY;
        for (String engineid : engineids) {
            final WebSocketEngine engine = engines.get(engineid);
            if (engine != null) { //在本地
                final WebSocketGroup group = engine.getWebSocketGroup(groupid);
                if (group == null || group.isEmpty()) {
                    if (finest) logger.finest("receive websocket message {engineid:'" + engineid + "', groupid:" + groupid + ", content:'" + message + "'} from " + addr + " but send result is " + RETCODE_GROUP_EMPTY);
                    return RETCODE_GROUP_EMPTY;
                }
                code = group.send(recent, message, last);
                if (finest) logger.finest("websocket node send message (" + message + ") from " + addr + " result is " + code);
            }
        }
        return code;
    }

    @Override
    public void connect(Serializable groupid, InetSocketAddress addr) {
        sncpNodes.appendSetItem(groupid, addr);
        if (finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + groupid + " connect from " + addr);
    }

    @Override
    public void disconnect(Serializable groupid, InetSocketAddress addr) {
        sncpNodes.removeSetItem(groupid, addr);
        if (finest) logger.finest(WebSocketNodeService.class.getSimpleName() + ".event: " + groupid + " disconnect from " + addr);
    }
}
