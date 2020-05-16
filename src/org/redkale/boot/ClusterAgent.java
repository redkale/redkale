/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.net.InetSocketAddress;
import java.util.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * 第三方服务发现管理接口cluster
 *
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class ClusterAgent {

    protected int nodeid;

    protected String name;

    protected String[] protocols; //必须全大写

    protected int[] ports;

    protected AnyValue config;

    public void init(AnyValue config) {
        this.config = config;
        this.name = config.getValue("name", "");
        {
            String ps = config.getValue("protocols", "").toUpperCase();
            if (ps == null || ps.isEmpty()) ps = "SNCP";
            this.protocols = ps.split(";");
        }
        String ts = config.getValue("ports", "");
        if (ts != null && !ts.isEmpty()) {
            String[] its = ts.split(";");
            List<Integer> list = new ArrayList<>();
            for (String str : its) {
                if (str.trim().isEmpty()) continue;
                list.add(Integer.getInteger(str.trim()));
            }
            if (!list.isEmpty()) this.ports = list.stream().mapToInt(x -> x).toArray();
        }
    }

    public void destroy(AnyValue config) {
    }

    public boolean containsProtocol(String protocol) {
        if (protocol == null || protocol.isEmpty()) return false;
        return protocols == null || Utility.contains(protocols, protocol.toUpperCase());
    }

    public boolean containsPort(int port) {
        if (ports == null || ports.length == 0) return true;
        return Utility.contains(ports, port);
    }

    //注册服务
    public void register(NodeServer ns, Set<Service> localServices, Set<Service> remoteServices) {
        if (localServices.isEmpty()) return;
        for (Service service : localServices) {  //注册本地模式
            register(ns, service);
        }
        //远程模式不注册
    }

    //注销服务
    public void deregister(NodeServer ns, Set<Service> localServices, Set<Service> remoteServices) {
        for (Service service : localServices) {//注销本地模式
            deregister(ns, service);
        }
        //远程模式不注册
    }

    //获取远程服务的可用ip列表
    public abstract List<InetSocketAddress> queryAddress(NodeServer server, Service service);

    //注册服务
    public abstract void register(NodeServer server, Service service);

    //注销服务
    public abstract void deregister(NodeServer server, Service service);

    //格式: protocol:classtype-resourcename
    public String generateServiceType(NodeServer ns, Service service) {
        if (!Sncp.isSncpDyn(service)) return ns.server.getProtocol().toLowerCase() + ":" + service.getClass().getName();
        return ns.server.getProtocol().toLowerCase() + ":" + Sncp.getResourceType(service).getName() + "-" + Sncp.getResourceName(service);
    }

    //格式: protocol:classtype-resourcename:nodeid
    public String generateServiceId(NodeServer ns, Service service) {
        return generateServiceType(ns, service) + ":" + this.nodeid;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public int getNodeid() {
        return nodeid;
    }

    public void setNodeid(int nodeid) {
        this.nodeid = nodeid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public void setProtocols(String[] protocols) {
        this.protocols = protocols;
    }

    public int[] getPorts() {
        return ports;
    }

    public void setPorts(int[] ports) {
        this.ports = ports;
    }

    public AnyValue getConfig() {
        return config;
    }

    public void setConfig(AnyValue config) {
        this.config = config;
    }

}
