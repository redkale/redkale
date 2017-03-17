/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.lang.reflect.Method;
import java.util.*;
import org.redkale.source.VirtualEntity;
import org.redkale.source.FilterNodeBean;
import org.redkale.source.FilterExpress;
import org.redkale.source.FilterColumn;
import org.redkale.util.Sheet;
import org.redkale.source.FilterBean;
import org.redkale.source.Flipper;
import org.redkale.source.EntityInfo;
import org.redkale.source.FilterNode;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import javax.persistence.*;
import org.redkale.convert.json.*;
import org.redkale.source.*;

/**
 *
 * @author zhangjx
 */
public class TestSourceCache {

    public static class TestEntityBean implements FilterBean {

        @FilterColumn(express = FilterExpress.GREATERTHAN)
        public int userid;

        @FilterColumn(express = FilterExpress.LIKE)
        public String username;

        public TestEntityBean(int userid, String username) {
            this.userid = userid;
            this.username = username;
        }
    }

    public static void main(String[] args) throws Exception {
        final BiFunction<DataSource, Class, List> fullloader = (DataSource t, Class u) -> null;
        Method method = EntityInfo.class.getDeclaredMethod("load", Class.class, boolean.class, Properties.class,
            DataSource.class, BiFunction.class);
        method.setAccessible(true);
        final EntityInfo<TestEntity> info = (EntityInfo<TestEntity>) method.invoke(null, TestEntity.class, false, new Properties(), null, fullloader);
        TestEntity[] entitys = new TestEntity[10_0000];
        for (int i = 0; i < entitys.length; i++) {
            entitys[i] = new TestEntity(i + 1, "用户_" + (i + 1));
        }
        long s = System.currentTimeMillis();
        for (TestEntity en : entitys) {
            info.getCache().insert(en);
        }
        long e = System.currentTimeMillis() - s;
        System.out.println("插入十万条记录耗时： " + e / 1000.0 + " 秒");

        s = System.currentTimeMillis();
        TestEntity one = info.getCache().find(9999);
        e = System.currentTimeMillis() - s;
        System.out.println("十万条数据中查询一条记录耗时： " + e / 1000.0 + " 秒 " + one);

        final Flipper flipper = new Flipper(2);
        flipper.setSort("userid DESC, createtime DESC");
        final FilterNode node = FilterNode.create("userid", FilterExpress.GREATERTHAN, 1000).and("username", FilterExpress.LIKE, "用户");
        System.out.println("node = " + node);
        Sheet<TestEntity> sheet = info.getCache().querySheet(null, flipper, node);
        System.out.println(sheet);
        System.out.println(info.getCache().querySheet(null, flipper, FilterNodeBean.createFilterNode(new TestEntityBean(1000, "用户"))));
        final CountDownLatch cdl = new CountDownLatch(100);
        s = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            new Thread() {
                @Override
                public void run() {
                    for (int k = 0; k < 10; k++) {
                        info.getCache().querySheet(true, null, flipper, node);
                    }
                    cdl.countDown();
                }
            }.start();
        }
        cdl.await();
        e = System.currentTimeMillis() - s;
        System.out.println("十万条数据中100并发查询一页循环10次记录耗时： " + e / 1000.0 + " 秒 " + sheet);  // CopyOnWriteArrayList   0.798    ConcurrentLinkedQueue 1.063
    }

    @VirtualEntity
    @Cacheable
    public static class TestEntity {

        @Id
        private int userid;

        private String username;

        private long createtime = System.currentTimeMillis();

        public TestEntity() {

        }

        public TestEntity(int userid, String username) {
            this.userid = userid;
            this.username = username;
        }

        public int getUserid() {
            return userid;
        }

        public void setUserid(int userid) {
            this.userid = userid;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public long getCreatetime() {
            return createtime;
        }

        public void setCreatetime(long createtime) {
            this.createtime = createtime;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
