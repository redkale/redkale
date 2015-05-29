/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import com.sun.istack.internal.logging.Logger;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.persistence.*;

/**
 *
 * @author zhangjx
 * @param <T>
 */
@SuppressWarnings("unchecked")
public final class EntityInfo<T> {

    private static final ConcurrentHashMap<Class, EntityInfo> entityInfos = new ConcurrentHashMap<>();

    private static final Logger logger = Logger.getLogger(EntityInfo.class);

    private final Class<T> type;

    private final String table;

    private final Creator<T> creator;

    private final Class primaryType;

    private final Attribute<T, Object> primary;

    private final String primaryFieldName;

    private final T defaultTypeInstance;

    private final EntityCache<T> cache;

    private final Map<String, Attribute<T, ?>> attributes = new HashMap<>();

    public static <T> EntityInfo<T> load(Class<T> clazz, final DataSource source) {
        EntityInfo rs = entityInfos.get(clazz);
        if (rs != null) return rs;
        synchronized (entityInfos) {
            rs = entityInfos.get(clazz);
            if (rs == null) {
                final List<Class> cacheClasses = source instanceof DataJDBCSource ? ((DataJDBCSource) source).cacheClasses : null;
                rs = new EntityInfo(clazz, cacheClasses);
                entityInfos.put(clazz, rs);
                if (rs.cache != null && source != null) {
                    AutoLoad auto = clazz.getAnnotation(AutoLoad.class);
                    if (auto != null && auto.value()) {
                        long s = System.currentTimeMillis();
                        rs.cache.fullLoad(source.queryList(clazz, null));
                        long e = System.currentTimeMillis() - s;
                        if (logger.isLoggable(Level.FINEST)) logger.finest(clazz.getName() + " full auto loaded for cache in " + e + " ms");
                    }
                }
            }
            return rs;
        }
    }

    private EntityInfo(Class<T> type, final List<Class> cacheClasses) {
        this.type = type;
        Table t = type.getAnnotation(Table.class);
        this.table = (t == null) ? type.getSimpleName().toLowerCase() : (t.catalog().isEmpty()) ? t.name() : (t.catalog() + '.' + t.name());
        this.creator = Creator.create(type);
        this.defaultTypeInstance = this.creator.create();
        Attribute idAttr0 = null;
        Class primaryType0 = null;
        String primaryName = null;
        Class cltmp = type;
        Set<String> fields = new HashSet<>();
        do {
            for (Field field : cltmp.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (Modifier.isFinal(field.getModifiers())) continue;
                if (field.getAnnotation(Transient.class) != null) continue;
                if (fields.contains(field.getName())) continue;
                final Column col = field.getAnnotation(Column.class);
                final String sqlfield = col == null || col.name().isEmpty() ? field.getName() : col.name();
                Attribute attr;
                try {
                    attr = Attribute.create(cltmp, sqlfield, field);
                } catch (RuntimeException e) {
                    continue;
                }
                if (field.getAnnotation(javax.persistence.Id.class) != null) {
                    if (idAttr0 == null) {
                        idAttr0 = attr;
                        primaryType0 = field.getType();
                        primaryName = field.getName();
                    }
                }
                fields.add(field.getName());
                attributes.put(field.getName(), attr);
            }
        } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        this.primary = idAttr0;
        this.primaryType = primaryType0;
        this.primaryFieldName = primaryName;
        //----------------cache--------------
        Cacheable c = type.getAnnotation(Cacheable.class);
        boolean cf = (c == null) ? (cacheClasses != null && cacheClasses.contains(type)) : false;
        if ((c != null && c.value()) || cf) {
            this.cache = new EntityCache<>(type, creator, primary, attributes);
        } else {
            this.cache = null;
        }
    }

    EntityCache<T> getCache() {
        return cache;
    }

    public Creator<T> getCreator() {
        return creator;
    }

    public Class<T> getType() {
        return type;
    }

    public String getTable() {
        return table;
    }

    public Class getPrimaryType() {
        return this.primaryType;
    }

    public Attribute<T, Object> getPrimary() {
        return this.primary;
    }

    public String getPrimaryField() {
        return this.primaryFieldName;
    }

    public Attribute<T, ?> getAttribute(String fieldname) {
        return this.attributes.get(fieldname);
    }

    public T getDefaultTypeInstance() {
        return defaultTypeInstance;
    }

    public Map<String, Attribute<T, ?>> getAttributes() {
        return attributes;
    }
}
