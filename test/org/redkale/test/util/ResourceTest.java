/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import javax.annotation.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ResourceTest {

    public static void main(String[] args) throws Exception {
        ResourceFactory factory = ResourceFactory.root();
        factory.register("property.id", "2345");
        TestService service = new TestService();
        TwinService twinService = new TwinService("eeeee");

        factory.register(service);
        factory.register(twinService);

        factory.inject(service);
        factory.inject(twinService);
        System.out.println(service);
        System.out.println(twinService);
        factory.register("property.id", "6789");
        twinService = new TwinService("ffff");
        factory.inject(twinService);
        factory.register(twinService);
        System.out.println(service);
        System.out.println(twinService);
    }

    public static class TwinService {

        @Resource(name = "property.id")
        private String id;

        @Resource
        private TestService service;

        private String name = "";
        
        @java.beans.ConstructorProperties({"name"})
        public TwinService(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public TestService getService() {
            return service;
        }

        public void setService(TestService service) {
            this.service = service;
        }

        @Override
        public String toString() {
            return "{name:\""+name+"\", id: "+id+", serivce:"+service+"}";
        }
    }

    public static class TestService {

        @Resource(name = "property.id")
        private String id;

        @Resource(name = "property.id")
        private int intid;

        @Resource
        private TwinService service;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public int getIntid() {
            return intid;
        }

        public void setIntid(int intid) {
            this.intid = intid;
        }
        @ConvertColumn(ignore = true)
        public TwinService getService() {
            return service;
        }

        public void setService(TwinService service) {
            this.service = service;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
