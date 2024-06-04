/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.source;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.redkale.annotation.AutoLoad;
import org.redkale.convert.json.*;
import org.redkale.persistence.Entity;
import org.redkale.persistence.Id;
import org.redkale.persistence.Transient;
import org.redkale.source.*;
import static org.redkale.source.FilterExpress.*;

/** @author zhangjx */
public class FilterNodeTest {

    private static Function<Class, EntityInfo> func;

    private static EntityInfo<CarTestTable> carEntity;

    public static void main(String[] args) throws Throwable {
        FilterNodeTest test = new FilterNodeTest();
        test.init();
        test.run();
    }

    @BeforeAll
    public static void init() throws Exception {
        final Properties props = new Properties();
        final BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader =
                (s, t) -> CompletableFuture.completedFuture(new ArrayList());
        func = (Class t) -> loadEntityInfo(t, false, props, null, fullloader);
        carEntity = loadEntityInfo(
                CarTestTable.class,
                false,
                props,
                null,
                (s, t) -> CompletableFuture.completedFuture(CarTestTable.createList()));
        final EntityInfo<UserTestTable> userEntity = loadEntityInfo(
                UserTestTable.class,
                false,
                props,
                null,
                (s, t) -> CompletableFuture.completedFuture(UserTestTable.createList()));
        final EntityInfo<CarTypeTable> typeEntity = loadEntityInfo(
                CarTypeTable.class,
                false,
                props,
                null,
                (s, t) -> CompletableFuture.completedFuture(CarTypeTable.createList()));
    }

    private static <T> EntityInfo<T> loadEntityInfo(
            Class<T> clazz,
            final boolean cacheForbidden,
            final Properties conf,
            DataSource source,
            BiFunction<DataSource, EntityInfo, CompletableFuture<List>> fullloader) {
        try {
            Method loadMethod = EntityInfo.class.getDeclaredMethod(
                    "load", Class.class, boolean.class, Properties.class, DataSource.class, BiFunction.class);
            loadMethod.setAccessible(true);
            return (EntityInfo) loadMethod.invoke(null, clazz, cacheForbidden, conf, source, fullloader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Class, String> getJoinTabalis(FilterNode node) {
        try {
            Method method = FilterNode.class.getDeclaredMethod("getJoinTabalis");
            method.setAccessible(true);
            return (Map) method.invoke(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> CharSequence createSQLJoin(
            FilterNode node,
            Function<Class, EntityInfo> func,
            final boolean update,
            final Map<Class, String> joinTabalis,
            final Set<String> haset,
            final EntityInfo<T> info) {
        try {
            Method method = FilterNode.class.getDeclaredMethod(
                    "createSQLJoin", Function.class, boolean.class, Map.class, Set.class, EntityInfo.class);
            method.setAccessible(true);
            return (CharSequence) method.invoke(node, func, update, joinTabalis, haset, info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> CharSequence createSQLExpress(
            FilterNode node,
            AbstractDataSqlSource source,
            final EntityInfo<T> info,
            final Map<Class, String> joinTabalis) {
        try {
            Method method = FilterNode.class.getDeclaredMethod(
                    "createSQLExpress", AbstractDataSqlSource.class, EntityInfo.class, Map.class);
            method.setAccessible(true);
            return (CharSequence) method.invoke(node, source, info, joinTabalis);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> boolean isCacheUseable(FilterNode node, final Function<Class, EntityInfo> entityApplyer) {
        try {
            Method method = FilterNode.class.getDeclaredMethod("isCacheUseable", Function.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(node, entityApplyer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> Predicate<T> createPredicate(FilterNode node, final EntityCache<T> cache) {
        try {
            Method method = FilterNode.class.getDeclaredMethod("createPredicate", EntityCache.class);
            method.setAccessible(true);
            return (Predicate) method.invoke(node, cache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void run() throws Exception {
        final CarTestBean bean = CarTestBean.create();
        FilterNode joinNode1 = FilterNodes.joinInner(
                        UserTestTable.class, new String[] {"userid", "username"}, "username", LIKE, bean.username)
                .or(FilterNodes.joinInner(
                        UserTestTable.class, new String[] {"userid", "username"}, "createtime", GT, bean.createtime));
        FilterNode joinNode2 = FilterNodes.joinInner(CarTypeTable.class, "cartype", "typename", LIKE, bean.typename);
        final FilterNode node = CarTestBean.caridTransient()
                ? (joinNode2.or(joinNode1))
                : FilterNodes.gt("carid", bean.carid).and(joinNode1).or(joinNode2);
        final FilterNode beanNode = FilterNodeBean.createFilterNode(bean);
        System.out.println("node.string = " + node);
        System.out.println("bean.string = " + beanNode);
        Assertions.assertEquals(
                "(CarTypeTable.typename LIKE '%法拉利%' OR (UserTestTable.username LIKE '%用户1%' OR UserTestTable.createtime > 500))",
                node.toString());
        Assertions.assertEquals(node.toString(), beanNode.toString());
        Map<Class, String> nodeJoinTabalis = getJoinTabalis(node);
        Map<Class, String> beanJoinTabalis = getJoinTabalis(beanNode);
        System.out.println("nodeJoinTabalis: " + nodeJoinTabalis);
        System.out.println("beanJoinTabalis: " + beanJoinTabalis);
        CharSequence nodeJoinsql = createSQLJoin(node, func, false, nodeJoinTabalis, new HashSet<>(), carEntity);
        CharSequence beanJoinsql = createSQLJoin(beanNode, func, false, beanJoinTabalis, new HashSet<>(), carEntity);
        CharSequence nodeWhere = createSQLExpress(node, null, carEntity, nodeJoinTabalis);
        CharSequence beanWhere = createSQLExpress(beanNode, null, carEntity, beanJoinTabalis);
        String expect =
                "SELECT a.* FROM cartesttable a INNER JOIN cartypetable ctt ON a.cartype = ctt.cartype INNER JOIN usertesttable utt ON a.userid = utt.userid AND a.username = utt.username WHERE (ctt.typename LIKE '%法拉利%' OR (utt.username LIKE '%用户1%' OR utt.createtime > 500))";
        System.out.println("node.sql = SELECT a.* FROM "
                + CarTestTable.class.getSimpleName().toLowerCase() + " a" + (nodeJoinsql == null ? "" : nodeJoinsql)
                + " WHERE " + nodeWhere);
        System.out.println("bean.sql = SELECT a.* FROM "
                + CarTestTable.class.getSimpleName().toLowerCase() + " a" + (beanJoinsql == null ? "" : beanJoinsql)
                + " WHERE " + beanWhere);
        boolean r1 = isCacheUseable(node, func);
        Assertions.assertTrue(r1);
        if (!r1) {
            System.err.println("node.isCacheUseable 应该是true");
        }
        boolean r2 = isCacheUseable(beanNode, func);
        Assertions.assertTrue(r2);

        System.out.println("node.Predicate = " + createPredicate(node, carEntity.getCache()));
        System.out.println("bean.Predicate = " + createPredicate(beanNode, carEntity.getCache()));
        System.out.println("node.sheet = " + carEntity.getCache().querySheet(null, new Flipper(), node));
        System.out.println("bean.sheet = " + carEntity.getCache().querySheet(null, new Flipper(), beanNode));
    }

    @FilterOrs({"r", "c"})
    public static class CarTestBean implements FilterBean {

        @FilterGroup("r.a")
        @FilterColumn(express = GT)
        @Transient
        public long carid;

        @FilterGroup("r.a.c")
        @FilterColumn(express = LIKE)
        @FilterJoinColumn(
                table = UserTestTable.class,
                columns = {"userid", "username"})
        public String username;

        @FilterGroup("r.a.c")
        @FilterColumn(express = GT)
        @FilterJoinColumn(
                table = UserTestTable.class,
                columns = {"userid", "username"})
        public long createtime;

        @FilterGroup("r")
        @FilterColumn(express = LIKE)
        @FilterJoinColumn(
                table = CarTypeTable.class,
                columns = {"cartype"})
        public String typename;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }

        public static boolean caridTransient() {
            try {
                return CarTestBean.class.getDeclaredField("carid").getAnnotation(Transient.class) != null;
            } catch (Exception e) {
                e.printStackTrace();
                return true;
            }
        }

        public static CarTestBean create() {
            final CarTestBean bean = new CarTestBean();
            bean.carid = 70002;
            bean.username = "用户1";
            bean.createtime = 500;
            bean.typename = "法拉利";
            return bean;
        }
    }

    @AutoLoad
    @Entity(cacheable = true)
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

        public CarTestTable() {}

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
            return JsonConvert.root().convertTo(this);
        }
    }

    @AutoLoad
    @Entity(cacheable = true)
    public static class CarTypeTable {

        public static List<CarTypeTable> createList() {
            List<CarTypeTable> list = new ArrayList<>();
            list.add(new CarTypeTable(101, "奥迪A1"));
            list.add(new CarTypeTable(102, "奥迪A2"));
            list.add(new CarTypeTable(103, "奥迪A3"));
            list.add(new CarTypeTable(104, "奥迪A4"));
            list.add(new CarTypeTable(105, "奥迪A5"));
            list.add(new CarTypeTable(201, "奔驰S1"));
            list.add(new CarTypeTable(202, "奔驰S2"));
            list.add(new CarTypeTable(203, "奔驰S3"));
            list.add(new CarTypeTable(204, "奔驰S4"));
            list.add(new CarTypeTable(205, "奔驰S5"));
            list.add(new CarTypeTable(301, "法拉利"));
            return list;
        }

        @Id
        private int cartype;

        private String typename;

        public CarTypeTable() {}

        public CarTypeTable(int cartype, String typename) {
            this.cartype = cartype;
            this.typename = typename;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
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
    @Entity(cacheable = true)
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

        public UserTestTable() {}

        public UserTestTable(int userid, String username, long createtime) {
            this.userid = userid;
            this.username = username;
            this.createtime = createtime;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
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
