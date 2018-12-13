/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.redkale.boot.*;
import org.redkale.net.Server;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.util.Comment;

/**
 *
 * @author zhangjx
 */
@RestService(name = "server", catalog = "watch", repair = false)
public class ServerWatchService extends AbstractWatchService {

    @Comment("不存在的Server节点")
    public static final int RET_SERVER_NOT_EXISTS = 1602_0001;

    @Comment("更改Server监听地址端口失败")
    public static final int RET_SERVER_CHANGEPORT_ERROR = 1602_0002;

    @Resource
    protected Application application;

    @RestMapping(name = "info", comment = "单个Server信息查询")
    public RetResult info(@RestParam(name = "#port:") final int port) {
        Stream<NodeServer> stream = application.getNodeServers().stream();
        NodeServer node = stream.filter(ns -> ns.getServer().getSocketAddress().getPort() == port).findFirst().orElse(null);
        if (node == null) return new RetResult(RET_SERVER_NOT_EXISTS, "Server(port=" + port + ") not found");
        return new RetResult(formatToMap(node));
    }

    @RestMapping(name = "infos", comment = "Server信息查询")
    public RetResult infos() {
        Map<String, Object> rs = new LinkedHashMap<>();
        for (NodeServer ns : application.getNodeServers()) {
            Server server = ns.getServer();
            rs.put("" + server.getSocketAddress().getPort(), formatToMap(ns));
        }
        return new RetResult(rs);
    }

    @RestMapping(name = "changeaddress", comment = "更改Server的监听地址和端口")
    public RetResult changeAddress(@RestParam(name = "#port:") final int oldport,
        @RestParam(name = "#newhost:") final String newhost, @RestParam(name = "#newport:") final int newport) {
        if (oldport < 1) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `oldport`");
        if (newport < 1) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `newport`");
        Stream<NodeServer> stream = application.getNodeServers().stream();
        NodeServer node = stream.filter(ns -> ns.getServer().getSocketAddress().getPort() == oldport).findFirst().orElse(null);
        if (node == null) return new RetResult(RET_SERVER_NOT_EXISTS, "Server(port=" + oldport + ") not found");
        final Server server = node.getServer();
        InetSocketAddress newAddr = new InetSocketAddress(newhost == null || newhost.isEmpty() ? server.getSocketAddress().getHostString() : newhost, newport);
        try {
            server.changeAddress(newAddr);
        } catch (IOException e) {
            e.printStackTrace();
            return new RetResult(RET_SERVER_CHANGEPORT_ERROR, "changeaddress error");
        }
        return RetResult.success();
    }

    private Map<String, Object> formatToMap(NodeServer node) {
        Server server = node.getServer();
        Map<String, Object> rs = new LinkedHashMap<>();
        String protocol = server.getProtocol();
        if (node instanceof NodeSncpServer) {
            protocol += "/SNCP";
        } else if (node instanceof NodeWatchServer) {
            protocol += "/WATCH";
        } else if (node instanceof NodeHttpServer) {
            protocol += "/HTTP";
        } else {
            NodeProtocol np = node.getClass().getAnnotation(NodeProtocol.class);
            if (np != null && np.value().length > 0) protocol += "/" + np.value()[0];
        }
        rs.put("name", server.getName());
        rs.put("protocol", protocol);
        rs.put("address", server.getSocketAddress());
        rs.put("threads", server.getThreads());
        rs.put("backlog", server.getBacklog());
        rs.put("bufferCapacity", server.getBufferCapacity());
        rs.put("bufferPoolSize", server.getBufferPoolSize());
        rs.put("charset", server.getCharset() == null ? "UTF-8" : server.getCharset().name());
        rs.put("maxbody", server.getMaxbody());
        rs.put("maxconns", server.getMaxconns());
        rs.put("serverStartTime", server.getServerStartTime());
        rs.put("responsePoolSize", server.getResponsePoolSize());
        rs.put("readTimeoutSeconds", server.getReadTimeoutSeconds());
        rs.put("writeTimeoutSeconds", server.getWriteTimeoutSeconds());
        rs.put("createConnectionCount", server.getCreateConnectionCount());
        rs.put("livingConnectionCount", server.getLivingConnectionCount());
        rs.put("closedConnectionCount", server.getClosedConnectionCount());
        return rs;
    }
}
