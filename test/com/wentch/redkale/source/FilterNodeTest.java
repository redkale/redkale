/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.wentch.redkale.convert.json.*;
import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.util.*;
import java.util.function.*;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 */
public class FilterNodeTest {

    public static void main(String[] args) throws Exception {
        final Function<Class, List> fullloader = (Class t) -> new ArrayList();
        final Function<Class, EntityInfo> func = (Class t) -> EntityInfo.load(t, 0, false, fullloader);
        final EntityInfo<CarTestTable> carEntity = EntityInfo.load(CarTestTable.class, 0, false, (t) -> CarTestTable.createList());
        final EntityInfo<UserTestTable> userEntity = EntityInfo.load(UserTestTable.class, 0, false, (t) -> UserTestTable.createList());
        final EntityInfo<CarTypeTestTable> typeEntity = EntityInfo.load(CarTypeTestTable.class, 0, false, (t) -> CarTypeTestTable.createList());

        final CarTestBean bean = new CarTestBean();
        bean.carid = 1;
        bean.username = "用户1";
        bean.createtime = 500;
        bean.typename = "法拉利";
        FilterNode joinNode1 = FilterJoinNode.create(UserTestTable.class, new String[]{"userid", "username"}, "username", LIKE, bean.username)
                .or(FilterJoinNode.create(UserTestTable.class, new String[]{"userid", "username"}, "createtime", GREATERTHAN, bean.createtime));
        FilterNode joinNode2 = FilterJoinNode.create(CarTypeTestTable.class, "cartype", "typename", LIKE, bean.typename);
        FilterNode node = FilterNode.create("carid", GREATERTHAN, bean.carid).and(joinNode1).or(joinNode2);
        FilterNode beanNode = FilterNodeBean.createFilterNode(bean);
        System.out.println("bean = " + beanNode);
        System.out.println("node = " + node);
        Map<Class, String> joinTabalis = node.getJoinTabalis();
        CharSequence joinsql = node.createSQLJoin(func, joinTabalis, carEntity);
        CharSequence where = node.createSQLExpress(carEntity, joinTabalis);
        System.out.println("SELECT a.* FROM " + CarTestTable.class.getSimpleName().toLowerCase() + " a" + (joinsql == null ? "" : joinsql) + " WHERE " + where);
        assert node.isCacheUseable(func) : "isCacheUseable 应该是true";
       assert  beanNode.isCacheUseable(func): "isCacheUseable 应该是true";
        System.out.println("isCacheUseable = " + node.isCacheUseable(func));  //应该是true
        System.out.println(node.createPredicate(carEntity.getCache()));
        System.out.println(beanNode.createPredicate(carEntity.getCache()));
        System.out.println(carEntity.getCache().querySheet(null, new Flipper(), node));
        System.out.println(carEntity.getCache().querySheet(null, new Flipper(), beanNode));
    }

    public static class CarTestBean implements FilterBean {

        @FilterGroup("[OR].[AND]a")
        @FilterColumn(express = GREATERTHAN)
        //@Transient
        public long carid;

        @FilterGroup("[OR].[AND]a.[OR]c")
        @FilterColumn(express = LIKE)
        @FilterJoinColumn(table = UserTestTable.class, columns = {"userid", "username"})
        public String username;

        @FilterGroup("[OR].[AND]a.[OR]c")
        @FilterColumn(express = GREATERTHAN)
        @FilterJoinColumn(table = UserTestTable.class, columns = {"userid", "username"})
        public long createtime;

        @FilterGroup("[OR]")
        @FilterColumn(express = LIKE)
        @FilterJoinColumn(table = CarTypeTestTable.class, columns = {"cartype"})
        public String typename;

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }
    }

    @AutoLoad
    @Cacheable
    public static class CarTestTable {

        public static List<CarTestTable> createList() {
            List<CarTestTable> list = new ArrayList<>();

            list.add(new CarTestTable(70001, 101, 1000011, "我的车"));
            list.add(new CarTestTable(70002, 102, 1000012, "我的车"));
            list.add(new CarTestTable(70003, 103, 1000013, "我的车"));
            list.add(new CarTestTable(70004, 104, 1000014, "我的车"));
            list.add(new CarTestTable(70005, 105, 1000015, "我的车"));

            list.add(new CarTestTable(70201, 201, 1000031, "我的车"));
            list.add(new CarTestTable(70202, 202, 1000032, "我的车"));
            list.add(new CarTestTable(70203, 203, 1000033, "我的车"));
            list.add(new CarTestTable(70204, 204, 1000034, "我的车"));
            list.add(new CarTestTable(70205, 205, 1000035, "我的车"));
            list.add(new CarTestTable(70505, 301, 1008000, "我的车"));

            return list;
        }

        @Id
        private long carid;

        private int cartype;

        private int userid;

        private String username;

        private String cartitle;

        public CarTestTable() {

        }

        public CarTestTable(long carid, int cartype, int userid, String cartitle) {
            this.carid = carid;
            this.cartype = cartype;
            this.userid = userid;
            this.username = "用户" + userid % 1000;
            this.cartitle = cartitle;
        }

        public long getCarid() {
            return carid;
        }

        public void setCarid(long carid) {
            this.carid = carid;
        }

        public int getUserid() {
            return userid;
        }

        public void setUserid(int userid) {
            this.userid = userid;
        }

        public String getCartitle() {
            return cartitle;
        }

        public void setCartitle(String cartitle) {
            this.cartitle = cartitle;
        }

        public int getCartype() {
            return cartype;
        }

        public void setCartype(int cartype) {
            this.cartype = cartype;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

    }

    @AutoLoad
    @Cacheable
    public static class CarTypeTestTable {

        public static List<CarTypeTestTable> createList() {
            List<CarTypeTestTable> list = new ArrayList<>();
            list.add(new CarTypeTestTable(101, "奥迪A1"));
            list.add(new CarTypeTestTable(102, "奥迪A2"));
            list.add(new CarTypeTestTable(103, "奥迪A3"));
            list.add(new CarTypeTestTable(104, "奥迪A4"));
            list.add(new CarTypeTestTable(105, "奥迪A5"));
            list.add(new CarTypeTestTable(201, "奔驰S1"));
            list.add(new CarTypeTestTable(202, "奔驰S2"));
            list.add(new CarTypeTestTable(203, "奔驰S3"));
            list.add(new CarTypeTestTable(204, "奔驰S4"));
            list.add(new CarTypeTestTable(205, "奔驰S5"));
            list.add(new CarTypeTestTable(301, "法拉利"));
            return list;
        }

        @Id
        private int cartype;

        private String typename;

        public CarTypeTestTable() {

        }

        public CarTypeTestTable(int cartype, String typename) {
            this.cartype = cartype;
            this.typename = typename;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
        }

        public int getCartype() {
            return cartype;
        }

        public void setCartype(int cartype) {
            this.cartype = cartype;
        }

        public String getTypename() {
            return typename;
        }

        public void setTypename(String typename) {
            this.typename = typename;
        }

    }

    @AutoLoad
    @Cacheable
    public static class UserTestTable {

        public static List<UserTestTable> createList() {
            List<UserTestTable> list = new ArrayList<>();
            for (int i = 11; i <= 50; i++) {
                list.add(new UserTestTable(1000000 + i, "用户" + i, i * 20));
            }
            list.add(new UserTestTable(1008000, "车主A", 20));
            return list;
        }

        @Id
        private int userid;

        private String username;

        private long createtime;

        public UserTestTable() {
        }

        public UserTestTable(int userid, String username, long createtime) {
            this.userid = userid;
            this.username = username;
            this.createtime = createtime;
        }

        @Override
        public String toString() {
            return JsonFactory.root().getConvert().convertTo(this);
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

    }
}
