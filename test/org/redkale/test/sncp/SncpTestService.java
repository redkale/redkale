/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import java.lang.reflect.*;
import java.net.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.Attribute;
import org.redkale.source.DataCallArrayAttribute;

/**
 *
 * @author zhangjx
 */
public class SncpTestService implements SncpTestIService {

    public static class CallAttribute implements Attribute<SncpTestBean, Long> {

        @Override
        public Class<? extends Long> type() {
            return long.class;
        }

        @Override
        public Class<SncpTestBean> declaringClass() {
            return SncpTestBean.class;
        }

        @Override
        public String field() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Long get(SncpTestBean obj) {
            System.out.println("返回ID: " + obj.getId());
            return obj.getId();
        }

        @Override
        public void set(SncpTestBean obj, Long value) {
            System.out.println("设置ID: " + value);
            obj.setId(value);
        }

    }

    public void insert(@DynCall(DataCallArrayAttribute.class) SncpTestBean... beans) {
        for (SncpTestBean bean : beans) {
            bean.setId(System.currentTimeMillis());
        }
    }

    public String queryResult(SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " 运行了queryResult方法");
        return "result: " + bean;
    }

    @MultiRun
    public String updateBean(@DynCall(CallAttribute.class) SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        System.out.println(Thread.currentThread().getName() + " 运行了updateBean方法");
        return "result: " + bean;
    }

    public static void main(String[] args) throws Exception {
        Service service = Sncp.createLocalService("", null, SncpTestService.class, new InetSocketAddress("127.0.0.1", 7070), null, null);
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createRemoteService("", null, SncpTestService.class, new InetSocketAddress("127.0.0.1", 7070), null);
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        service = Sncp.createRemoteService("", null, SncpTestIService.class, new InetSocketAddress("127.0.0.1", 7070), null);
        for (Method method : service.getClass().getDeclaredMethods()) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
        for (Method method : SncpClient.parseMethod(service.getClass())) {
            System.out.println(method);
        }
        System.out.println("-----------------------------------");
    }
}
