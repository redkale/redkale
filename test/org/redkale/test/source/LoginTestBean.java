/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import org.redkale.source.*;

/**
 *
 * @author zhangjx
 */
public class LoginTestBean implements FilterBean {

    private String sessionid;
    
    private int sid;
    public static void main(String[] args) throws Throwable {
        LoginTestBean bean = new LoginTestBean();
        bean.setSessionid("xxx"); 
        bean.setSid(23333); 
        BiFunction<DataSource, Class, List> fullloader = (s, z) -> new ArrayList();
        Method method = EntityInfo.class.getDeclaredMethod("load", Class.class, boolean.class, Properties.class,
            DataSource.class, BiFunction.class);
        method.setAccessible(true);
        final EntityInfo<CacheTestBean> info = (EntityInfo<CacheTestBean>) method.invoke(null, CacheTestBean.class, true, new Properties(), null, fullloader);
        EntityCache<CacheTestBean> cache = new EntityCache(info, null);
        FilterNode node = FilterNodeBean.createFilterNode(bean);
        System.out.println("cache = " + cache + ", node = " + node);
        Method pre = FilterNode.class.getDeclaredMethod("createPredicate", EntityCache.class);
        pre.setAccessible(true); 
        //为null是因为CacheTestBean 没有sid和sessionid这两个字段
        System.out.println(pre.invoke(node,cache));
    }
    public String getSessionid() {
        return sessionid;
    }

    public void setSessionid(String sessionid) {
        this.sessionid = sessionid;
    }

    public int getSid() {
        return sid;
    }

    public void setSid(int sid) {
        this.sid = sid;
    }

}
