/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.util.*;
import java.util.stream.Collectors;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * Service对象的封装类
 * <p>
 * <p>
 * 详情见: http://redkale.org
 *
 * @author zhangjx
 * @param <T> Service的子类
 */
public final class ServiceWrapper<T extends Service> implements Comparable<ServiceWrapper> {

    private static volatile int maxClassNameLength = 0;

    private static volatile int maxNameLength = 0;

    private final T service;

    private final AnyValue conf;

    private final String sncpGroup; //自身的组节点名 可能为null

    private final Set<String> groups; //所有的组节点，包含自身

    private final String name;

    private final boolean remote;

    private final Class[] types;

    public ServiceWrapper(T service, String name, String sncpGroup, Set<String> groups, AnyValue conf) {
        this(null, service, name, sncpGroup, groups, conf);
    }

    @SuppressWarnings("unchecked")
    public ServiceWrapper(Class<T> type, T service, String name, String sncpGroup, Set<String> groups, AnyValue conf) {
        this.service = service;
        this.conf = conf;
        this.sncpGroup = sncpGroup;
        this.groups = groups;
        this.name = name;
        this.remote = Sncp.isRemote(service);
        ResourceType rty = service.getClass().getAnnotation(ResourceType.class);
        this.types = rty == null ? new Class[]{type == null ? (Class<T>) service.getClass() : type} : rty.value();

        maxNameLength = Math.max(maxNameLength, name.length());
        StringBuilder s = new StringBuilder();
        if (this.types.length == 1) {
            s.append(types[0].getName());
        } else {
            s.append('[');
            s.append(Arrays.asList(this.types).stream().map((Class t) -> t.getName()).collect(Collectors.joining(",")));
            s.append(']');
        }
        maxClassNameLength = Math.max(maxClassNameLength, s.length() + 1);
    }

    public static Class[] parseTypes(final Class<? extends Service> servicetype) {
        ResourceType rty = servicetype.getAnnotation(ResourceType.class);
        return rty == null ? new Class[]{servicetype} : rty.value();
    }

    public String toSimpleString() {
        StringBuilder sb = new StringBuilder();
        sb.append(remote ? "RemoteService" : "LocalService ");
        int len;
        if (types.length == 1) {
            sb.append("(type= ").append(types[0].getName());
            len = maxClassNameLength - types[0].getName().length();
        } else {
            StringBuilder s = new StringBuilder();
            s.append('[');
            s.append(Arrays.asList(this.types).stream().map((Class t) -> t.getName()).collect(Collectors.joining(",")));
            s.append(']');
            sb.append("(types=").append(s);
            len = maxClassNameLength - s.length();
        }

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
        return (this.types[0].equals(other.types[0]) && this.remote == other.remote && this.name.equals(other.name) && Objects.equals(this.sncpGroup, other.sncpGroup));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.types[0]);
        hash = 67 * hash + Objects.hashCode(this.sncpGroup);
        hash = 67 * hash + Objects.hashCode(this.name);
        hash = 67 * hash + (this.remote ? 1 : 0);
        return hash;
    }

    @Override
    public int compareTo(ServiceWrapper o) {
        int rs = this.types[0].getName().compareTo(o.types[0].getName());
        if (rs == 0) rs = this.name.compareTo(o.name);
        return rs;
    }

    public Class[] getTypes() {
        return types;
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
