/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.util.*;
import java.util.function.BiFunction;
import javax.persistence.Id;
import org.redkale.source.*;
import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 */
public class CacheTestBean {

    @Id
    private long pkgid;

    private String name;

    private long price;

    public static void main(String[] args) throws Exception {
        final List<CacheTestBean> list = new ArrayList<>();
        list.add(new CacheTestBean(1, "a", 12));
        list.add(new CacheTestBean(1, "a", 18));
        list.add(new CacheTestBean(2, "b", 20));
        list.add(new CacheTestBean(2, "bb", 60));
        Attribute idattr = Attribute.create(CacheTestBean.class, "pkgid");
        Attribute nameattr = Attribute.create(CacheTestBean.class, "name");
        Attribute priceattr = Attribute.create(CacheTestBean.class, "price");
        BiFunction<DataSource, Class, List> fullloader = (s, z) -> list;
        EntityCache<CacheTestBean> cache = new EntityCache(EntityInfo.load(CacheTestBean.class, 0, true, new Properties(), null, fullloader));
        cache.fullLoad();

        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.COUNT, "name", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.DISTINCTCOUNT, "name", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.AVG, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.SUM, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.MAX, "price", null));
        System.out.println(cache.queryColumnMap("pkgid", FilterFunc.MIN, "price", null));
    }

    public CacheTestBean() {
    }

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

}
