/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.net.sncp;

import com.wentch.redkale.service.Service;
import com.wentch.redkale.util.AnyValue;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public final class ServiceEntry {

    private final Class<? extends Service> serviceClass;

    private final Service service;

    private final AnyValue conf;

    private final List<String> names = new ArrayList<>();

    public ServiceEntry(Class<? extends Service> serviceClass, Service service, AnyValue conf, String name) {
        this.serviceClass = serviceClass == null ? service.getClass() : serviceClass;
        this.service = service;
        this.conf = conf;
        this.names.add(name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null) return false;
        if (!(obj instanceof ServiceEntry)) return false;
        ServiceEntry other = (ServiceEntry) obj;
        if (this.serviceClass != other.serviceClass) return false;
        return (this.service == other.service);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 97 * hash + Objects.hashCode(this.serviceClass);
        hash = 97 * hash + Objects.hashCode(this.service);
        return hash;
    }

    public Class<? extends Service> getServiceClass() {
        return serviceClass;
    }

    public Service getService() {
        return service;
    }

    public AnyValue getServiceConf() {
        return conf;
    }

    public void initService() {
        service.init(conf);
    }

    public void destroyService() {
        service.destroy(conf);
    }

    public List<String> getNames() {
        return names;
    }

    public void addName(String name) {
        this.names.add(name);
    }

    public boolean containsName(String name) {
        return names.contains(name);
    }

}
