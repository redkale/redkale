/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.sncp;

import com.wentch.redkale.net.sncp.*;
import com.wentch.redkale.service.*;
import com.wentch.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class SncpTestService implements Service {

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
            return obj.getId();
        }

        @Override
        public void set(SncpTestBean obj, Long value) {
            obj.setId(value);
        }

    }

    public String queryResult(SncpTestBean bean) {
        System.out.println(Thread.currentThread().getName() + " 运行了queryResult方法");
        return "result: " + bean;
    }

    @MultiRun
    public String updateBean(@SncpCall(CallAttribute.class) SncpTestBean bean) {
        bean.setId(System.currentTimeMillis());
        System.out.println(Thread.currentThread().getName() + " 运行了updateBean方法");
        return "result: " + bean;
    }
}
