/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.net.*;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.*;
import org.redkale.net.http.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.Comment;
import org.redkale.watch.WatchService;

/**
 *
 * @author zhangjx
 */
@RestService(name = "transport", catalog = "watch", repair = false)
public class TransportWatchService implements WatchService {

    @Comment("不存在的Group节点")
    public static final int RET_NO_GROUP = 1605_0001;

    @Comment("非法的Node节点IP地址")
    public static final int RET_ADDR_ILLEGAL = 1605_0002;

    @Comment("Node节点IP地址已存在")
    public static final int RET_ADDR_EXISTS = 1605_0003;

    @Resource
    private Application application;

    @Resource
    private TransportFactory transportFactory;

    @RestMapping(name = "nodes", auth = false, comment = "获取所有Node节点")
    public RetResult<List<TransportGroupInfo>> addNode() {
        return new RetResult<>(transportFactory.getGroupInfos());
    }

    @RestMapping(name = "addnode", auth = false, comment = "动态增加指定Group的Node节点")
    public RetResult addNode(
        @RestParam(name = "group", comment = "Group节点名") final String group,
        @RestParam(name = "addr", comment = "节点IP") final String addr,
        @RestParam(name = "port", comment = "节点端口") final int port) {
        InetSocketAddress address;
        try {
            address = new InetSocketAddress(addr, port);
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
            channel.connect(address).get(2, TimeUnit.SECONDS);  //连接超时2秒
            channel.close();
        } catch (Exception e) {
            e.printStackTrace();
            return new RetResult(RET_ADDR_ILLEGAL, "InetSocketAddress(addr=" + addr + ", port=" + port + ") is illegal");
        }
        if (transportFactory.findGroupName(address) != null) return new RetResult(RET_ADDR_ILLEGAL, "InetSocketAddress(addr=" + addr + ", port=" + port + ") is exists");
        synchronized (this) {
            if (transportFactory.findGroupInfo(group) == null) {
                return new RetResult(RET_NO_GROUP, "not found group (" + group + ")");
            }
            transportFactory.addGroupInfo(group, address);
            for (Service service : transportFactory.getServices()) {
                if (!Sncp.isSncpDyn(service)) continue;
                SncpClient client = Sncp.getSncpClient(service);
                if (Sncp.isRemote(service)) {
                    if (client.getRemoteGroups() != null && client.getRemoteGroups().contains(group)) {
                        client.getRemoteGroupTransport().addRemoteAddresses(address);
                    }
                } else {
                    if (group.equals(client.getSameGroup())) {
                        client.getSameGroupTransport().addRemoteAddresses(address);
                    }
                    if (client.getDiffGroups() != null && client.getDiffGroups().contains(group)) {
                        for (Transport transport : client.getDiffGroupTransports()) {
                            transport.addRemoteAddresses(address);
                        }
                    }
                }
            }
        }
        return RetResult.success();
    }

    @RestMapping(name = "removenode", auth = false, comment = "动态删除指定Group的Node节点")
    public RetResult removeNode(
        @RestParam(name = "group", comment = "Group节点名") final String group,
        @RestParam(name = "addr", comment = "节点IP") final String addr,
        @RestParam(name = "port", comment = "节点端口") final int port) {
        if (group == null) return new RetResult(RET_NO_GROUP, "not found group (" + group + ")");
        final InetSocketAddress address = new InetSocketAddress(addr, port);
        if (!group.equals(transportFactory.findGroupName(address))) return new RetResult(RET_ADDR_ILLEGAL, "InetSocketAddress(addr=" + addr + ", port=" + port + ") not belong to group(" + group + ")");
        synchronized (this) {
            if (transportFactory.findGroupInfo(group) == null) {
                return new RetResult(RET_NO_GROUP, "not found group (" + group + ")");
            }
            transportFactory.removeGroupInfo(group, address);
            for (Service service : transportFactory.getServices()) {
                if (!Sncp.isSncpDyn(service)) continue;
                SncpClient client = Sncp.getSncpClient(service);
                if (Sncp.isRemote(service)) {
                    if (client.getRemoteGroups() != null && client.getRemoteGroups().contains(group)) {
                        client.getRemoteGroupTransport().removeRemoteAddresses(address);
                    }
                } else {
                    if (group.equals(client.getSameGroup())) {
                        client.getSameGroupTransport().removeRemoteAddresses(address);
                    }
                    if (client.getDiffGroups() != null && client.getDiffGroups().contains(group)) {
                        for (Transport transport : client.getDiffGroupTransports()) {
                            transport.removeRemoteAddresses(address);
                        }
                    }
                }
            }
        }
        return RetResult.success();
    }
}
