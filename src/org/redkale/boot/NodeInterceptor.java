/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.util.Objects;
import org.redkale.service.Service;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class NodeInterceptor {

    public void preStart(NodeServer server) {

    }

    public void preShutdown(NodeServer server) {

    }

    public static class InterceptorServiceWrapper<T extends Service> {

        private String name;

        private Class<T> type;

        private T service;

        public InterceptorServiceWrapper() {
        }

        public InterceptorServiceWrapper(String name, Class<T> type, T service) {
            this.name = name;
            this.type = type;
            this.service = service;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Class<T> getType() {
            return type;
        }

        public void setType(Class<T> type) {
            this.type = type;
        }

        public T getService() {
            return service;
        }

        public void setService(T service) {
            this.service = service;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.name);
            hash = 97 * hash + Objects.hashCode(this.type);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final InterceptorServiceWrapper<?> other = (InterceptorServiceWrapper<?>) obj;
            return Objects.equals(this.name, other.name) && Objects.equals(this.type, other.type);
        }

    }
}
