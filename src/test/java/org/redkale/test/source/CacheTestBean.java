/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.*;
import org.redkale.persistence.VirtualEntity;
import org.redkale.source.*;

/** @author zhangjx */
@VirtualEntity(loader = CacheTestBean.DefaultBeanLoader.class)
public class CacheTestBean {

    @Id
    private long pkgid;

    private String name;

    private long price;

    public static void main(String[] args) throws Exception {
        Method method = EntityInfo.class.getDeclaredMethod(
                "load", Class.class, boolean.class, Properties.class, DataSource.class, BiFunction.class);
        method.setAccessible(true);
        final EntityInfo<CacheTestBean> info = (EntityInfo<CacheTestBean>) method.invoke(
                null, CacheTestBean.class, true, new Properties(), null, new CacheTestBean.DefaultBeanLoader());
        EntityCache<CacheTestBean> cache = new EntityCache(info, null);
        cache.fullLoadAsync();

        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.COUNT, "name", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.DISTINCTCOUNT, "name", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.AVG, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.SUM, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.MAX, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.MIN, "price", null));

        System.out.println(cache.find(null, FilterNodes.eq("name", "BB")));
        System.out.println(cache.find(null, FilterNodes.igEq("name", "BB")));
        System.out.println(cache.querySheet(null, null, FilterNodes.igNotLike("name", "B")));
        System.out.println(cache.find(null, FilterNodes.eq(CacheTestBean::getName, "BB")));
        System.out.println(cache.find(null, FilterNodes.igEq(CacheTestBean::getName, "BB")));
        System.out.println(cache.querySheet(null, null, FilterNodes.igNotLike(CacheTestBean::getName, "B")));
    }

    public CacheTestBean() {}

    public CacheTestBean(long pkgid, String name, long price) {
        this.pkgid = pkgid;
        this.name = name;
        this.price = price;
    }

    public long getPkgid() {
        return pkgid;
    }

    public void setPkgid(long pkgid) {
        this.pkgid = pkgid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }

    public static class DefaultBeanLoader implements BiFunction<DataSource, EntityInfo, CompletableFuture<List>> {

        @Override
        public CompletableFuture<List> apply(DataSource t, EntityInfo u) {
            final List<CacheTestBean> list = new ArrayList<>();
            list.add(new CacheTestBean(1, "a", 12));
            list.add(new CacheTestBean(1, "a", 18));
            list.add(new CacheTestBean(2, "b", 20));
            list.add(new CacheTestBean(2, "bb", 60));
            return CompletableFuture.completedFuture(list);
        }
    }
}
