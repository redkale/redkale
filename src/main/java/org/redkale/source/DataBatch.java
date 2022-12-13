/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.*;
import javax.persistence.Entity;
import org.redkale.util.*;

/**
 * DataSource批量操作对象，操作类型只能是增删改   <br>
 * 非线程安全类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class DataBatch {

    //-------------------- 新增操作 --------------------
    @Comment("新增对象")
    protected List insertEntitys;

    //-------------------- 删除操作 --------------------
    @Comment("删除对象")
    protected List deleteEntitys;

    @Comment("删除对象")
    protected List<DeleteBatchAction1> deleteActions1;

    @Comment("删除对象")
    protected List<DeleteBatchAction2> deleteActions2;

    //-------------------- 修改操作 --------------------
    @Comment("修改对象")
    protected List updateEntitys;

    @Comment("修改对象")
    protected List<UpdateBatchAction1> updateActions1;

    @Comment("修改对象")
    protected List<UpdateBatchAction2> updateActions2;

    @Comment("修改对象")
    protected List<UpdateBatchAction3> updateActions3;

    @Comment("修改对象")
    protected List<UpdateBatchAction4> updateActions4;

    protected DataBatch() {
    }

    public static DataBatch create() {
        return new DataBatch();
    }

    public <T> DataBatch insert(T... entitys) {
        if (this.insertEntitys == null) {
            this.insertEntitys = new ArrayList();
        }
        for (T t : entitys) {
            Objects.requireNonNull(t);
            if (t.getClass().getAnnotation(Entity.class) == null) {
                throw new RuntimeException("Entity Class " + t.getClass() + " must be on Annotation @Entity");
            }
            this.insertEntitys.add(t);
        }
        return this;
    }

    public <T> DataBatch delete(T... entitys) {
        if (this.deleteEntitys == null) {
            this.deleteEntitys = new ArrayList();
        }
        for (T t : entitys) {
            Objects.requireNonNull(t);
            if (t.getClass().getAnnotation(Entity.class) == null) {
                throw new RuntimeException("Entity Class " + t.getClass() + " must be on Annotation @Entity");
            }
            this.deleteEntitys.add(t);
        }
        return this;
    }

    public <T> DataBatch delete(Class<T> clazz, Serializable... pks) {
        Objects.requireNonNull(clazz);
        if (clazz.getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + clazz + " must be on Annotation @Entity");
        }
        if (pks.length < 1) {
            throw new RuntimeException("delete pk length is zero ");
        }
        for (Serializable pk : pks) {
            Objects.requireNonNull(pk);
        }
        if (this.deleteActions1 == null) {
            this.deleteActions1 = new ArrayList();
        }
        this.deleteActions1.add(new DeleteBatchAction1(clazz, pks));
        return this;
    }

    public <T> DataBatch delete(Class<T> clazz, FilterNode node) {
        return delete(clazz, node, (Flipper) null);
    }

    public <T> DataBatch delete(Class<T> clazz, FilterNode node, Flipper flipper) {
        Objects.requireNonNull(clazz);
        if (clazz.getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + clazz + " must be on Annotation @Entity");
        }
        if (this.deleteActions2 == null) {
            this.deleteActions2 = new ArrayList();
        }
        this.deleteActions2.add(new DeleteBatchAction2(clazz, node, flipper));
        return this;
    }

    public <T> DataBatch update(T... entitys) {
        if (this.updateEntitys == null) {
            this.updateEntitys = new ArrayList();
        }
        for (T t : entitys) {
            Objects.requireNonNull(t);
            if (t.getClass().getAnnotation(Entity.class) == null) {
                throw new RuntimeException("Entity Class " + t.getClass() + " must be on Annotation @Entity");
            }
            this.updateEntitys.add(t);
        }
        return this;
    }

    public <T> DataBatch update(Class<T> clazz, Serializable pk, String column, Serializable value) {
        return update(clazz, pk, ColumnValue.mov(column, value));
    }

    public <T> DataBatch update(Class<T> clazz, Serializable pk, ColumnValue... values) {
        Objects.requireNonNull(clazz);
        if (clazz.getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + clazz + " must be on Annotation @Entity");
        }
        Objects.requireNonNull(pk);
        if (values.length < 1) {
            throw new RuntimeException("update column-value length is zero ");
        }
        for (ColumnValue val : values) {
            Objects.requireNonNull(val);
        }
        if (this.updateActions1 == null) {
            this.updateActions1 = new ArrayList();
        }
        this.updateActions1.add(new UpdateBatchAction1(clazz, pk, values));
        return this;
    }

    public <T> DataBatch update(Class<T> clazz, FilterNode node, String column, Serializable value) {
        return update(clazz, node, (Flipper) null, ColumnValue.mov(column, value));
    }

    public <T> DataBatch update(Class<T> clazz, FilterNode node, ColumnValue... values) {
        return update(clazz, node, (Flipper) null, values);
    }

    public <T> DataBatch update(Class<T> clazz, FilterNode node, Flipper flipper, ColumnValue... values) {
        Objects.requireNonNull(clazz);
        if (clazz.getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + clazz + " must be on Annotation @Entity");
        }
        if (values.length < 1) {
            throw new RuntimeException("update column-value length is zero ");
        }
        for (ColumnValue val : values) {
            Objects.requireNonNull(val);
        }
        if (this.updateActions2 == null) {
            this.updateActions2 = new ArrayList();
        }
        this.updateActions2.add(new UpdateBatchAction2(clazz, node, flipper, values));
        return this;
    }

    public <T> DataBatch updateColumn(T entity, final String... columns) {
        return updateColumn(entity, (FilterNode) null, columns);
    }

    public <T> DataBatch updateColumn(T entity, final FilterNode node, final String... columns) {
        Objects.requireNonNull(entity);
        if (entity.getClass().getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + entity.getClass() + " must be on Annotation @Entity");
        }
        if (columns.length < 1) {
            throw new RuntimeException("update column length is zero ");
        }
        for (String val : columns) {
            Objects.requireNonNull(val);
        }
        if (this.updateActions3 == null) {
            this.updateActions3 = new ArrayList();
        }
        this.updateActions3.add(new UpdateBatchAction3(entity, node, columns));
        return this;
    }

    public <T> DataBatch updateColumn(T entity, SelectColumn selects) {
        return updateColumn(entity, (FilterNode) null, selects);
    }

    public <T> DataBatch updateColumn(T entity, final FilterNode node, SelectColumn selects) {
        Objects.requireNonNull(entity);
        if (entity.getClass().getAnnotation(Entity.class) == null) {
            throw new RuntimeException("Entity Class " + entity.getClass() + " must be on Annotation @Entity");
        }
        Objects.requireNonNull(selects);
        if (this.updateActions4 == null) {
            this.updateActions4 = new ArrayList();
        }
        this.updateActions4.add(new UpdateBatchAction4(entity, node, selects));
        return this;
    }

    static class DeleteBatchAction1 {

        public Class clazz;

        public Serializable[] pks;

        public DeleteBatchAction1(Class clazz, Serializable... pks) {
            this.clazz = clazz;
            this.pks = pks;
        }
    }

    static class DeleteBatchAction2 {

        public Class clazz;

        public FilterNode node;

        public Flipper flipper;

        public DeleteBatchAction2(Class clazz, FilterNode node) {
            this.clazz = clazz;
            this.node = node;
        }

        public DeleteBatchAction2(Class clazz, FilterNode node, Flipper flipper) {
            this.clazz = clazz;
            this.node = node;
            this.flipper = flipper;
        }
    }

    static class UpdateBatchAction1 {

        public Class clazz;

        public Serializable pk;

        public ColumnValue[] values;

        public UpdateBatchAction1(Class clazz, Serializable pk, ColumnValue... values) {
            this.clazz = clazz;
            this.pk = pk;
            this.values = values;
        }
    }

    static class UpdateBatchAction2 {

        public Class clazz;

        public FilterNode node;

        public Flipper flipper;

        public ColumnValue[] values;

        public UpdateBatchAction2(Class clazz, FilterNode node, ColumnValue... values) {
            this.clazz = clazz;
            this.node = node;
            this.values = values;
        }

        public UpdateBatchAction2(Class clazz, FilterNode node, Flipper flipper, ColumnValue... values) {
            this.clazz = clazz;
            this.node = node;
            this.flipper = flipper;
            this.values = values;
        }
    }

    static class UpdateBatchAction3 {

        public Object entity;

        public FilterNode node;

        public String[] columns;

        public UpdateBatchAction3(Object entity, String... columns) {
            this.entity = entity;
            this.columns = columns;
        }

        public UpdateBatchAction3(Object entity, FilterNode node, String... columns) {
            this.entity = entity;
            this.node = node;
            this.columns = columns;
        }
    }

    static class UpdateBatchAction4 {

        public Object entity;

        public FilterNode node;

        public SelectColumn selects;

        public UpdateBatchAction4(Object entity, SelectColumn selects) {
            this.entity = entity;
            this.selects = selects;
        }

        public UpdateBatchAction4(Object entity, FilterNode node, SelectColumn selects) {
            this.entity = entity;
            this.node = node;
            this.selects = selects;
        }
    }
}
