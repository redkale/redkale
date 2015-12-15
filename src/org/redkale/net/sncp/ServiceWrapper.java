/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import java.util.*;
import org.redkale.boot.*;

/**
 *  Service对象的封装类
 *
 * @see http://www.redkale.org
 * @author zhangjx
 * @param <T>
 */
public final class ServiceWrapper<T extends Service> {

    private final Class<T> type;

    private final T service;

    private final AnyValue conf;

    private final String group;

    private final Set<String> groups;

    private final String name;

    private final boolean remote;

    public ServiceWrapper(Class<T> type, T service, String group, ClassFilter.FilterEntry<Service> entry) {
        this(type, service, entry.getName(), group, entry.getGroups(), entry.getProperty());
    }

    public ServiceWrapper(Class<T> type, T service, String name, String group, Set<String> groups, AnyValue conf) {
        this.type = type == null ? (Class<T>) service.getClass() : type;
        this.service = service;
        this.conf = conf;
        this.group = group;
        this.groups = groups;
        this.name = name;
        this.remote = Sncp.isRemote(service);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (!(obj instanceof ServiceWrapper)) return false;
        ServiceWrapper other = (ServiceWrapper) obj;
        return (this.type.equals(other.type) && this.remote == other.remote && this.name.equals(other.name) && this.group.equals(other.group));
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 67 * hash + Objects.hashCode(this.type);
        hash = 67 * hash + Objects.hashCode(this.group);
        hash = 67 * hash + Objects.hashCode(this.name);
        hash = 67 * hash + (this.remote ? 1 : 0);
        return hash;
    }

    public Class<? extends Service> getType() {
        return type;
    }

    public Service getService() {
        return service;
    }

    public AnyValue getConf() {
        return conf;
    }

    public String getName() {
        return service.name();
    }

    public boolean isRemote() {
        return remote;
    }

    public Set<String> getGroups() {
        return groups;
    }

}
