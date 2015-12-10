/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import org.redkale.source.EntityInfo;
import org.redkale.source.EntityCache;
import org.redkale.util.Attribute;
import org.redkale.source.DataSource.FuncEnum;
import java.util.*;
import javax.persistence.*;

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
        EntityCache<CacheTestBean> cache = new EntityCache(EntityInfo.load(CacheTestBean.class, 0, true, null));
        cache.fullLoad(list);

        System.out.println(cache.getMapResult("pkgid", FuncEnum.COUNT, "name", null));
        System.out.println(cache.getMapResult("pkgid", FuncEnum.DISTINCTCOUNT, "name", null));
        System.out.println(cache.getMapResult("pkgid", FuncEnum.AVG, "price", null));
        System.out.println(cache.getMapResult("pkgid", FuncEnum.SUM, "price", null));
        System.out.println(cache.getMapResult("pkgid", FuncEnum.MAX, "price", null));
        System.out.println(cache.getMapResult("pkgid", FuncEnum.MIN, "price", null));
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
