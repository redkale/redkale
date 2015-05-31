/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.*;
import javax.persistence.*;
import javax.persistence.criteria.*;

/**
 * 不再完全实现JPA版的DataSource
 * <p>
 * @author zhangjx
 */
@Deprecated
final class DataJPASource implements DataSource {

    protected final EntityManagerFactory factory;

    private final AtomicBoolean debug = new AtomicBoolean(false);

    private final Logger logger = Logger.getLogger(DataJPASource.class.getSimpleName());

    @Override
    public <T> void updateColumnIncrement(Class<T> clazz, Serializable id, String column, long incvalue) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> void updateColumnIncrement(DataConnection conn, Class<T> clazz, Serializable id, String column, long incvalue) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> void refreshCache(Class<T> clazz) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> T find(Class<T> clazz, FilterBean bean) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Number getCountDistinctSingleResult(Class entityClass, String column) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Number getCountDistinctSingleResult(Class entityClass, String column, FilterBean bean) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T, V> Sheet<V> queryColumnSheet(String selectedColumn, Class<T> clazz, Flipper flipper, FilterBean bean) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T, V> Set<V> queryColumnSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> int[] queryColumnIntSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> long[] queryColumnLongSet(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> int[] queryColumnIntList(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> long[] queryColumnLongList(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> int[] queryColumnIntSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> long[] queryColumnLongSet(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> int[] queryColumnIntList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> long[] queryColumnLongList(String selectedColumn, Class<T> clazz, String column, FilterExpress express, Serializable key) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public <T> T find(Class<T> clazz, SelectColumn selects, Serializable pk) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static class DataJPAConnection extends DataConnection {

        private final EntityManager manager;

        private DataJPAConnection(EntityManager m) {
            super(m);
            this.manager = m;
        }

        @Override
        public void close() {
            manager.close();
        }

        @Override
        public boolean commit() {
            try {
                manager.getTransaction().commit();
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void rollback() {
            manager.getTransaction().rollback();
        }
    }

    public DataJPASource() {
        this("");
    }

    public DataJPASource(final String unitName) {
        factory = Persistence.createEntityManagerFactory(unitName);
        setDebug(System.getProperty("javax.persistence.debug") != null);
    }

    public static DataJPASource create() {
        return new DataJPASource("");
    }

    public static DataJPASource create(final String unitName) {
        return new DataJPASource(unitName);
    }

    private void setDebug(boolean flag) {
        this.debug.set(flag);
        if (flag) logger.setLevel(Level.FINEST);
    }

    @Override
    public DataConnection createReadConnection() {
        return new DataJPAConnection(factory.createEntityManager());
    }

    @Override
    public DataConnection createWriteConnection() {
        return new DataJPAConnection(factory.createEntityManager());
    }

    public void close() {
        this.factory.close();
    }
    //----------------------insert-----------------------------

    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void insert(T... values) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            for (T value : values) {
                manager.persist(value);
            }
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 新增对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void insert(final DataConnection conn, T... values) {
        final EntityManager manager = conn.getConnection();
        for (T value : values) {
            manager.persist(value);
        }
    }

    //-------------------------delete--------------------------
    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void delete(T... values) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            delete(manager, values);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 删除对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void delete(final DataConnection conn, T... values) {
        final EntityManager manager = conn.getConnection();
        delete(manager, values);
    }

    private <T> void delete(final EntityManager manager, T... values) {
        for (T value : values) {
            manager.remove(value);
        }
    }

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param ids 主键值
     */
    @Override
    public <T> void delete(Class<T> clazz, Serializable... ids) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            delete(manager, clazz, ids);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据主键值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param ids
     */
    @Override
    public <T> void delete(final DataConnection conn, Class<T> clazz, Serializable... ids) {
        final EntityManager manager = conn.getConnection();
        delete(manager, clazz, ids);
    }

    private <T> void delete(final EntityManager manager, Class<T> clazz, Serializable... ids) {
        for (Serializable id : ids) {
            Object value = manager.find(clazz, id);
            if (value != null) manager.remove(value);
        }
    }

    /**
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     */
    @Override
    public <T> void deleteByColumn(Class<T> clazz, String column, Serializable... keys) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            deleteByColumn(manager, clazz, column, keys);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column
     * @param keys
     */
    @Override
    public <T> void deleteByColumn(final DataConnection conn, Class<T> clazz, String column, Serializable... keys) {
        final EntityManager manager = conn.getConnection();
        deleteByColumn(manager, clazz, column, keys);
    }

    private <T> void deleteByColumn(final EntityManager manager, Class<T> clazz, String column, Serializable... keys) {
        final CriteriaBuilder builder = manager.getCriteriaBuilder();
        CriteriaDelete<T> cd = builder.createCriteriaDelete(clazz);
        cd.where(cd.getRoot().get(column).in((Object[]) keys));
        manager.createQuery(cd).executeUpdate();
    }

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    @Override
    public <T> void deleteByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            deleteByTwoColumn(manager, clazz, column1, key1, column2, key2);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据两个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     */
    @Override
    public <T> void deleteByTwoColumn(final DataConnection conn, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        deleteByTwoColumn((EntityManager) conn.getConnection(), clazz, column1, key1, column2, key2);
    }

    private <T> void deleteByTwoColumn(final EntityManager manager, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        final CriteriaBuilder builder = manager.getCriteriaBuilder();
        CriteriaDelete<T> cd = builder.createCriteriaDelete(clazz);
        final Root root = cd.getRoot();
        cd.where(builder.and(builder.equal(root.get(column1), key1), builder.equal(root.get(column2), key2)));
        manager.createQuery(cd).executeUpdate();
    }

    /**
     * 根据三个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @param column3
     * @param key3
     */
    public <T> void deleteByThreeColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2, String column3, Serializable key3) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            deleteByThreeColumn(manager, clazz, column1, key1, column2, key2, column3, key3);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据三个column字段的值删除对象， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @param column3
     * @param key3
     */
    public <T> void deleteByThreeColumn(final DataConnection conn, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2, String column3, Serializable key3) {
        deleteByThreeColumn((EntityManager) conn.getConnection(), clazz, column1, key1, column2, key2, column3, key3);
    }

    private <T> void deleteByThreeColumn(final EntityManager manager, Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2, String column3, Serializable key3) {
        final CriteriaBuilder builder = manager.getCriteriaBuilder();
        CriteriaDelete<T> cd = builder.createCriteriaDelete(clazz);
        final Root root = cd.getRoot();
        cd.where(builder.and(builder.equal(root.get(column1), key1), builder.equal(root.get(column2), key2), builder.equal(root.get(column3), key3)));
        manager.createQuery(cd).executeUpdate();
    }
    //------------------------update---------------------------

    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param values
     */
    @Override
    public <T> void update(T... values) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            update(manager, values);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 更新对象， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param values
     */
    @Override
    public <T> void update(final DataConnection conn, T... values) {
        final EntityManager manager = conn.getConnection();
        update(manager, values);
    }

    private <T> void update(final EntityManager manager, T... values) {
        for (T value : values) {
            manager.merge(value);
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    @Override
    public <T> void updateColumn(Class<T> clazz, Serializable id, String column, Serializable value) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            updateColumn(manager, clazz, id, column, value);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据主键值更新对象的column对应的值， 必须是Entity Class
     *
     * @param <T>
     * @param conn
     * @param clazz
     * @param id
     * @param column
     * @param value
     */
    @Override
    public <T> void updateColumn(final DataConnection conn, Class<T> clazz, Serializable id, String column, Serializable value) {
        final EntityManager manager = conn.getConnection();
        updateColumn(manager, clazz, id, column, value);
    }

    private <T> void updateColumn(final EntityManager manager, Class<T> clazz, Serializable id, String column, Serializable value) {
        final CriteriaBuilder builder = manager.getCriteriaBuilder();
        CriteriaUpdate<T> cd = builder.createCriteriaUpdate(clazz);
        cd.set(column, value);
        cd.where(builder.equal(cd.from(clazz).get(EntityInfo.load(clazz, 0, null).getPrimary().field()), id));
        manager.createQuery(cd).executeUpdate();
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param value
     * @param columns
     */
    @Override
    public <T> void updateColumns(final T value, final String... columns) {
        final EntityManager manager = factory.createEntityManager();
        try {
            manager.getTransaction().begin();
            updateColumns(manager, value, columns);
            manager.getTransaction().commit();
        } finally {
            manager.close();
        }
    }

    /**
     * 更新对象指定的一些字段， 必须是Entity对象
     *
     * @param <T>
     * @param conn
     * @param value
     * @param columns
     */
    @Override
    public <T> void updateColumns(final DataConnection conn, final T value, final String... columns) {
        final EntityManager manager = conn.getConnection();
        updateColumns(manager, value, columns);
    }

    private <T> void updateColumns(final EntityManager manager, final T value, final String... columns) {
        final Class clazz = value.getClass();
        final EntityInfo info = EntityInfo.load(clazz, 0, null);
        final Attribute idattr = info.getPrimary();
        final CriteriaBuilder builder = manager.getCriteriaBuilder();
        final CriteriaUpdate<T> cd = builder.createCriteriaUpdate(clazz);
        for (String column : columns) {
            cd.set(column, info.getAttribute(column).get(value));
        }
        cd.where(builder.equal(cd.from(clazz).get(info.getPrimary().field()), idattr.get(value)));
        manager.createQuery(cd).executeUpdate();
    }

    //-----------------------getSingleResult-----------------------------
    //-----------------------------MAX-----------------------------
    @Override
    public Number getMaxSingleResult(final Class entityClass, final String column) {
        return getMaxSingleResult(entityClass, column, null);
    }

    @Override
    public Number getMaxSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.MAX, entityClass, column, bean);
    }

    //-----------------------------MIN-----------------------------
    @Override
    public Number getMinSingleResult(final Class entityClass, final String column) {
        return getMinSingleResult(entityClass, column, null);
    }

    @Override
    public Number getMinSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.MIN, entityClass, column, bean);
    }

    //-----------------------------SUM-----------------------------
    @Override
    public Number getSumSingleResult(final Class entityClass, final String column) {
        return getSumSingleResult(entityClass, column, null);
    }

    @Override
    public Number getSumSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.SUM, entityClass, column, bean);
    }

    //----------------------------COUNT----------------------------
    @Override
    public Number getCountSingleResult(final Class entityClass) {
        return getCountSingleResult(entityClass, null);
    }

    @Override
    public Number getCountSingleResult(final Class entityClass, FilterBean bean) {
        return getSingleResult(ReckonType.COUNT, entityClass, null, bean);
    }

    //-----------------------------AVG-----------------------------
    @Override
    public Number getAvgSingleResult(final Class entityClass, final String column) {
        return getAvgSingleResult(entityClass, column, null);
    }

    @Override
    public Number getAvgSingleResult(final Class entityClass, final String column, FilterBean bean) {
        return getSingleResult(ReckonType.AVG, entityClass, column, bean);
    }

    private Number getSingleResult(final ReckonType type, final Class entityClass, final String column, FilterBean bean) {
        final EntityManager manager = factory.createEntityManager();
        try {
            String sql = "SELECT " + type.name() + "(a." + column + ") FROM " + entityClass.getSimpleName() + " a";
            if (debug.get()) logger.finer(entityClass.getSimpleName() + " single sql=" + sql);
            return manager.createQuery(sql, Number.class).getSingleResult();
        } finally {
            manager.close();
        }
    }

    private Number getSingleResultCriteria(final ReckonType type, final Class entityClass, final String column, FilterBean bean) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            final CriteriaQuery<Number> cry = builder.createQuery(Number.class);
            final Root root = cry.from(entityClass);
            Expression<Boolean> where = bean == null ? null : createWhereExpression(builder, root, bean);
            if (where != null) cry.where(where);
            switch (type) {
                case MAX: cry.select(builder.max(root.get(column)));
                    break;
                case MIN: cry.select(builder.min(root.get(column)));
                    break;
                case SUM: cry.select(builder.sum(root.get(column)));
                    break;
                case AVG: cry.select(builder.avg(root.get(column)));
                    break;
                case COUNT: cry.select(builder.count(column == null ? root : root.get(column)));
                    break;
                default: throw new RuntimeException("error ReckonType");
            }
            //max count
            return manager.createQuery(cry).getSingleResult();
        } finally {
            manager.close();
        }
    }

    //-----------------------find----------------------------
    /**
     * 根据主键获取对象
     *
     * @param <T>
     * @param clazz
     * @param pk
     * @return
     */
    @Override
    public <T> T find(Class<T> clazz, Serializable pk) {
        final EntityManager manager = factory.createEntityManager();
        try {
            return manager.find(clazz, pk);
        } finally {
            manager.close();
        }
    }

    /**
     * 根据主键值集合获取对象集合
     *
     * @param <T>
     * @param clazz
     * @param ids
     * @return
     */
    @Override
    public <T> T[] find(Class<T> clazz, Serializable... ids) {
        final EntityManager manager = factory.createEntityManager();
        try {
            T[] result = (T[]) Array.newInstance(clazz, ids.length);
            int i = 0;
            for (Serializable id : ids) {
                result[i++] = manager.find(clazz, id);
            }
            return result;
        } finally {
            manager.close();
        }
    }

    /**
     * 根据唯一索引获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> T findByColumn(Class<T> clazz, String column, Serializable key) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            CriteriaQuery<T> cd = builder.createQuery(clazz);
            cd.where(builder.equal(cd.from(clazz).get(column), key));
            List<T> list = manager.createQuery(cd).getResultList();
            return list == null || list.isEmpty() ? null : list.get(0);
        } finally {
            manager.close();
        }
    }

    /**
     * 根据两个字段的值获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @return
     */
    @Override
    public <T> T findByTwoColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            final CriteriaQuery<T> cd = builder.createQuery(clazz);
            final Root root = cd.from(clazz);
            cd.where(builder.and(builder.equal(root.get(column1), key1), builder.equal(root.get(column2), key2)));
            return manager.createQuery(cd).getSingleResult();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据三个字段的值获取单个对象
     *
     * @param <T>
     * @param clazz
     * @param column1
     * @param key1
     * @param column2
     * @param key2
     * @param column3
     * @param key3
     * @return
     */
    public <T> T findByThreeColumn(Class<T> clazz, String column1, Serializable key1, String column2, Serializable key2, String column3, Serializable key3) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            final CriteriaQuery<T> cd = builder.createQuery(clazz);
            final Root root = cd.from(clazz);
            cd.where(builder.and(builder.equal(root.get(column1), key1), builder.equal(root.get(column2), key2), builder.equal(root.get(column3), key3)));
            return manager.createQuery(cd).getSingleResult();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据唯一索引获取对象
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param keys
     * @return
     */
    @Override
    public <T> T[] findByColumn(Class<T> clazz, String column, Serializable... keys) {
        return findByColumn(clazz, null, column, keys);
    }

    /**
     * 根据字段值拉去对象， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects 只拉起指定字段名或者排除指定字段名的值
     * @param column
     * @param keys
     * @return
     */
    @Override
    public <T> T[] findByColumn(Class<T> clazz, final SelectColumn selects, String column, Serializable... keys) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            CriteriaQuery<T> cd = builder.createQuery(clazz);
            cd.where(cd.from(clazz).get(column).in((Object[]) keys));
            List<T> list = manager.createQuery(cd).getResultList();
            list = selectList(clazz, selects, list);
            return list.toArray((T[]) Array.newInstance(clazz, list.size()));
        } finally {
            manager.close();
        }
    }

    //-----------------------list----------------------------
    /**
     * 根据指定字段值查询对象某个字段的集合
     *
     * @param <T>
     * @param <V>
     * @param selectedColumn
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T, V> List<V> queryColumnList(String selectedColumn, Class<T> clazz, String column, Serializable key) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            final CriteriaQuery query = builder.createQuery();
            final Root root = query.from(clazz);
            query.select(root.get(selectedColumn));
            query.where(builder.equal(root.get(column), key));
            return manager.createQuery(query).getResultList();
        } finally {
            manager.close();
        }
    }

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, String column, Serializable key) {
        return queryList(clazz, (SelectColumn) null, column, key);
    }

    /**
     * 根据指定字段值查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param column
     * @param express
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, String column, FilterExpress express, Serializable key) {
        return queryList(clazz, (SelectColumn) null, column, express, key);
    }

    /**
     * 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, Serializable key) {
        return queryList(clazz, selects, column, FilterExpress.EQUAL, key);
    }

    /**
     * 注意: 尚未实现识别express功能 根据指定字段值查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param column
     * @param express
     * @param key
     * @return
     */
    @Override
    public <T> List<T> queryList(Class<T> clazz, final SelectColumn selects, String column, FilterExpress express, Serializable key) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            CriteriaQuery<T> cd = builder.createQuery(clazz);
            cd.where(builder.equal(cd.from(clazz).get(column), key));
            List<T> list = manager.createQuery(cd).getResultList();
            return selectList(clazz, selects, list);
        } finally {
            manager.close();
        }
    }

    /**
     * 根据过滤对象FilterBean查询对象集合
     *
     * @param <T>
     * @param clazz
     * @param bean
     * @return
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final FilterBean bean) {
        return queryList(clazz, null, bean);
    }

    /**
     * 根据过滤对象FilterBean查询对象集合， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param bean
     * @return
     */
    @Override
    public <T> List<T> queryList(final Class<T> clazz, final SelectColumn selects, final FilterBean bean) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder builder = manager.getCriteriaBuilder();
            CriteriaQuery<T> cd = builder.createQuery(clazz);
            final Expression<Boolean> where = createWhereExpression(builder, cd.from(clazz), bean);
            if (where != null) cd.where(where);
            List<T> list = manager.createQuery(cd).getResultList();
            return selectList(clazz, selects, list);
        } finally {
            manager.close();
        }
    }

    //-----------------------sheet----------------------------
    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据
     *
     * @param <T>
     * @param clazz
     * @param flipper
     * @param bean
     * @return
     */
    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, final Flipper flipper, final FilterBean bean) {
        return querySheet(clazz, null, flipper, bean);
    }

    /**
     * 根据过滤对象FilterBean和翻页对象Flipper查询一页的数据， 对象只填充或排除SelectColumn指定的字段
     *
     * @param <T>
     * @param clazz
     * @param selects
     * @param flipper
     * @param bean
     * @return
     */
    @Override
    public <T> Sheet<T> querySheet(Class<T> clazz, final SelectColumn selects, final Flipper flipper, final FilterBean bean) {
        final EntityManager manager = factory.createEntityManager();
        try {
            final CriteriaBuilder totalbd = manager.getCriteriaBuilder();
            final CriteriaQuery<Long> totalcry = totalbd.createQuery(Long.class);
            final Root totalroot = totalcry.from(clazz);
            totalcry.select(totalbd.count(totalroot));
            Expression<Boolean> totalwhere = createWhereExpression(totalbd, totalroot, bean);
            if (totalwhere != null) totalcry.where(totalwhere);
            long total = manager.createQuery(totalcry).getSingleResult();
            if (total < 1) return new Sheet<>();
            //------------------------------------------------
            final CriteriaBuilder listbd = manager.getCriteriaBuilder();
            final CriteriaQuery<T> listcry = listbd.createQuery(clazz);
            final Root<T> listroot = listcry.from(clazz);
            Expression<Boolean> listwhere = createWhereExpression(listbd, listroot, bean);
            if (listwhere != null) listcry.where(listwhere);
            final String sort = flipper.getSort();
            if (flipper != null && sort != null && !sort.isEmpty()) {
                if (sort.indexOf(',') > 0) {
                    List<Order> orders = new ArrayList<>();
                    for (String item : sort.split(",")) {
                        if (item.isEmpty()) continue;
                        String[] sub = item.split("\\s+");
                        if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                            orders.add(listbd.asc(listroot.get(sub[0])));
                        } else {
                            orders.add(listbd.desc(listroot.get(sub[0])));
                        }
                    }
                    listcry.orderBy(orders);
                } else {
                    for (String item : sort.split(",")) {
                        if (item.isEmpty()) continue;
                        String[] sub = item.split("\\s+");
                        if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                            listcry.orderBy(listbd.asc(listroot.get(sub[0])));
                        } else {
                            listcry.orderBy(listbd.desc(listroot.get(sub[0])));
                        }
                    }
                }
            }
            final TypedQuery<T> listqry = manager.createQuery(listcry);
            if (flipper != null) {
                listqry.setFirstResult(flipper.index());
                listqry.setMaxResults(flipper.getSize());
            }
            List<T> list = selectList(clazz, selects, listqry.getResultList());
            return new Sheet<>(total, list);
        } finally {
            manager.close();
        }
    }

    private <T> List<T> selectList(final Class<T> clazz, final SelectColumn selects, final List<T> list) {
        if (selects == null || selects.isEmpty() || list.isEmpty()) return list;
        final EntityInfo info = EntityInfo.load(clazz, 0, null);
        final Object dftValue = info.getCreator().create();
        final Map<String, Attribute> map = info.getAttributes();
        final List<Attribute> attrs = new ArrayList<>();
        if (selects.isExcludable()) {
            for (String col : selects.getColumns()) {
                Attribute attr = map.get(col);
                if (attr != null) attrs.add(attr);
            }
        } else {
            map.entrySet().forEach(x -> {
                if (!selects.validate(x.getKey())) attrs.add(x.getValue());
            });
        }
        for (Object obj : list) {
            for (Attribute attr : attrs) {
                attr.set(obj, attr.get(dftValue));
            }
        }
        return list;
    }

    private <T extends FilterBean> Expression<Boolean> createWhereExpression(final CriteriaBuilder builder, final Root root, final FilterBean bean) {
        if (bean == null) return null;
        final FilterInfo<T> filter = FilterInfo.load(bean.getClass(), this);
        final List<Predicate> list = new ArrayList<>();
        for (final FilterInfo.FilterItem item : filter.getFilters()) {
            final FilterExpress express = item.express;
            Object attrval = item.attribute.get(bean);
            if (express == FilterExpress.ISNULL) {
                list.add(builder.isNull(root.get(item.attribute.field())));
                continue;
            } else if (express == FilterExpress.ISNOTNULL) {
                list.add(builder.isNotNull(root.get(item.attribute.field())));
                continue;
            }
            if (attrval == null) continue;
            if (item.number && ((Number) attrval).longValue() < item.least) continue;
            switch (express) {
                case EQUAL:
                    list.add(builder.equal(root.get(item.attribute.field()), attrval));
                    break;
                case NOTEQUAL:
                    list.add(builder.notEqual(root.get(item.attribute.field()), attrval));
                    break;
                case GREATERTHAN:
                    list.add(builder.greaterThan(root.get(item.attribute.field()), (Comparable) attrval));
                    break;
                case LESSTHAN:
                    list.add(builder.lessThan(root.get(item.attribute.field()), (Comparable) attrval));
                    break;
                case GREATERTHANOREQUALTO:
                    list.add(builder.greaterThanOrEqualTo(root.get(item.attribute.field()), (Comparable) attrval));
                    break;
                case LESSTHANOREQUALTO:
                    list.add(builder.lessThanOrEqualTo(root.get(item.attribute.field()), (Comparable) attrval));
                    break;
                case LIKE:
                    list.add(builder.like(root.get(item.attribute.field()), (String) (item.likefit ? ("%" + attrval + "%") : attrval)));
                    break;
                case NOTLIKE:
                    list.add(builder.notLike(root.get(item.attribute.field()), (String) (item.likefit ? ("%" + attrval + "%") : attrval)));
                    break;
                case BETWEEN:
                case NOTBETWEEN:
                    Range range = (Range) attrval;
                    Predicate p = builder.between(root.get(item.attribute.field()), (Comparable) range.getMin(), (Comparable) range.getMax());
                    if (NOTBETWEEN == express) {
                        p = builder.not(p);
                    }
                    list.add(p);
                    break;
                case AND:
                case OR:
                    Range[] ranges = (Range[]) attrval;
                    Predicate[] ps = new Predicate[ranges.length];
                    int oi = 0;
                    for (Range r : ranges) {
                        ps[oi++] = builder.between(root.get(item.attribute.field()), (Comparable) r.getMin(), (Comparable) r.getMax());
                    }
                    if (OR == express) {
                        list.add(builder.or(ps));
                    } else {
                        list.add(builder.and(ps));
                    }
                    break;
                case IN:
                case NOTIN:
                    Predicate pd = null;
                    if (attrval instanceof Collection) {
                        Collection c = (Collection) attrval;
                        if (!c.isEmpty()) {
                            pd = builder.in(root.get(item.attribute.field()).in(c));
                        }
                    } else {
                        final int len = Array.getLength(attrval);
                        if (len > 0) {
                            Class comp = attrval.getClass().getComponentType();
                            if (comp.isPrimitive()) {
                                Object[] os = new Object[len];
                                for (int i = 0; i < len; i++) {
                                    os[i] = Array.get(attrval, i);
                                }
                                pd = builder.in(root.get(item.attribute.field()).in(os));
                            } else {
                                pd = builder.in(root.get(item.attribute.field()).in((Object[]) attrval));
                            }
                            if (NOTIN == express) {
                                pd = builder.not(pd);
                            }
                            list.add(pd);
                        }
                    }
                    if (pd != null) {
                        if (NOTIN == express) {
                            pd = builder.not(pd);
                        }
                        list.add(pd);
                    }
                    break;
                default:
                    throw new RuntimeException(bean.getClass() + "'s field (" + item.aliasfield + ") have a illegal express (" + express + ")");
            }
        }
        if (list.isEmpty()) return null;
        return builder.and(list.toArray(new Predicate[list.size()]));
    }

    private static enum ReckonType {

        MAX, MIN, SUM, COUNT, AVG;
    }
}
