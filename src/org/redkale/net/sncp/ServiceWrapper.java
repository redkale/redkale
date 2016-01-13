/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import java.util.*;
import org.redkale.util.*;

/**
 * Service对象的封装类
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 * @param <T> Service的子类
 */
public final class ServiceWrapper<T extends Service> implements Comparable<ServiceWrapper> {

    private static volatile int maxClassNameLength = 0;

    private static volatile int maxNameLength = 0;

    private final Class<T> type;

    private final T service;

    private final AnyValue conf;

    private final String sncpGroup; //自身的组节点名 可能为null

    private final Set<String> groups; //所有的组节点，包含自身

    private final String name;

    private final boolean remote;

    private final Class[] resTypes;

    public ServiceWrapper(Class<T> type, T service, String name, String sncpGroup, Set<String> groups, AnyValue conf) {
        this.type = type == null ? (Class<T>) service.getClass() : type;
        this.service = service;
        this.conf = conf;
        this.sncpGroup = sncpGroup;
        this.groups = groups;
        this.name = name;
        this.remote = Sncp.isRemote(service);
        ResourceType rty = service.getClass().getAnnotation(ResourceType.class);
        this.resTypes = rty == null ? new Class[]{this.type} : rty.value();

        maxNameLength = Math.max(maxNameLength, name.length());
        maxClassNameLength = Math.max(maxClassNameLength, type.getName().length() + 1);
    }

    public String toSimpleString() {
        StringBuilder sb = new StringBuilder();
        sb.append(remote ? "RemoteService" : "LocalService ").append("(type=").append(type.getName());
        int len = maxClassNameLength - type.getName().length();
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        sb.append(", name='").append(name).append("'");
        for (int i = 0; i < maxNameLength - name.length(); i++) {
            sb.append(' ');
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (!(obj instanceof ServiceWrapper)) return false;
        ServiceWrapper other = (ServiceWrapper) obj;
        return (this.type.equals(other.type) && this.remote == other.remote && this.name.equals(other.name) && Objects.equals(this.sncpGroup, other.sncpGroup));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.type);
        hash = 67 * hash + Objects.hashCode(this.sncpGroup);
        hash = 67 * hash + Objects.hashCode(this.name);
        hash = 67 * hash + (this.remote ? 1 : 0);
        return hash;
    }

    @Override
    public int compareTo(ServiceWrapper o) {
        int rs = this.type.getName().compareTo(o.type.getName());
        if (rs == 0) rs = this.name.compareTo(o.name);
        return rs;
    }

    public Class<? extends Service> getType() {
        return type;
    }

    public Class[] getResTypes() {
        return resTypes;
    }

    public Service getService() {
        return service;
    }

    public AnyValue getConf() {
        return conf;
    }

    public String getName() {
        return name;
    }

    public boolean isRemote() {
        return remote;
    }

    public Set<String> getGroups() {
        return groups;
    }

}
