/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.redkale.boot.Application;
import org.redkale.util.*;

/**
 * 根Servlet， 一个Server只能存在一个根Servlet
 * 由之前PrepareServlet更名而来，since 2.7.0
 * 用于分发Request请求
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <K> SessionID的类型
 * @param <C> Context的子类型
 * @param <R> Request的子类型
 * @param <P> Response的子类型
 * @param <S> Servlet的子类型
 */
public abstract class DispatcherServlet<K extends Serializable, C extends Context, R extends Request<C>, P extends Response<C, R>, S extends Servlet<C, R, P>> extends Servlet<C, R, P> {

    private final LongAdder executeCounter = new LongAdder(); //执行请求次数

    private final LongAdder illegalRequestCounter = new LongAdder(); //错误请求次数

    private final ReentrantLock servletLock = new ReentrantLock();

    private Set<S> servlets = new HashSet<>();

    private final ReentrantLock mappingLock = new ReentrantLock();

    private Map<K, S> mappings = new HashMap<>();

    private final List<Filter<C, R, P>> filters = new ArrayList<>();

    protected final ReentrantLock filtersLock = new ReentrantLock();

    protected Application application;

    protected Filter<C, R, P> headFilter;

    protected void incrExecuteCounter() {
        executeCounter.increment();
    }

    protected void incrIllegalRequestCounter() {
        illegalRequestCounter.increment();
    }

    protected void putServlet(S servlet) {
        servletLock.lock();
        try {
            Set<S> newservlets = new HashSet<>(servlets);
            newservlets.add(servlet);
            this.servlets = newservlets;
        } finally {
            servletLock.unlock();
        }
    }

    protected void removeServlet(S servlet) {
        servletLock.lock();
        try {
            Set<S> newservlets = new HashSet<>(servlets);
            newservlets.remove(servlet);
            this.servlets = newservlets;
            doAfterRemove(servlet);
        } finally {
            servletLock.unlock();
        }
    }

    public boolean containsServlet(Class<? extends S> servletClass) {
        servletLock.lock();
        try {
            for (S servlet : new HashSet<>(servlets)) {
                if (servlet.getClass().equals(servletClass)) {
                    return true;
                }
            }
            return false;
        } finally {
            servletLock.unlock();
        }
    }

    public boolean containsServlet(String servletClassName) {
        servletLock.lock();
        try {
            for (S servlet : new HashSet<>(servlets)) {
                if (servlet.getClass().getName().equals(servletClassName)) {
                    return true;
                }
            }
            return false;
        } finally {
            servletLock.unlock();
        }
    }

    protected void putMapping(K key, S servlet) {
        mappingLock.lock();
        try {
            Map<K, S> newmappings = new HashMap<>(mappings);
            newmappings.put(key, servlet);
            this.mappings = newmappings;
        } finally {
            mappingLock.unlock();
        }
    }

    protected void removeMapping(K key) {
        mappingLock.lock();
        try {
            if (mappings.containsKey(key)) {
                Map<K, S> newmappings = new HashMap<>(mappings);
                S s = newmappings.remove(key);
                this.mappings = newmappings;
                doAfterRemove(s);
            }
        } finally {
            mappingLock.unlock();
        }
    }

    protected void removeMapping(S servlet) {
        mappingLock.lock();
        try {
            List<K> keys = new ArrayList<>();
            Map<K, S> newmappings = new HashMap<>(mappings);
            for (Map.Entry<K, S> en : newmappings.entrySet()) {
                if (en.getValue().equals(servlet)) {
                    keys.add(en.getKey());
                }
            }
            for (K key : keys) newmappings.remove(key);
            this.mappings = newmappings;
            doAfterRemove(servlet);
        } finally {
            mappingLock.unlock();
        }
    }

    protected void doAfterRemove(S servlet) {
    }

    protected S mappingServlet(K key) {
        return mappings.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(C context, AnyValue config) {
        if (application != null && application.isCompileMode()) {
            return;
        }
        filtersLock.lock();
        try {
            if (!filters.isEmpty()) {
                Collections.sort(filters);
                for (Filter<C, R, P> filter : filters) {
                    filter.init(context, config);
                }
                this.headFilter = filters.get(0);
                Filter<C, R, P> filter = this.headFilter;
                for (int i = 1; i < filters.size(); i++) {
                    filter._next = filters.get(i);
                    filter = filter._next;
                }
            }
        } finally {
            filtersLock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void destroy(C context, AnyValue config) {
        filtersLock.lock();
        try {
            if (!filters.isEmpty()) {
                for (Filter filter : filters) {
                    filter.destroy(context, config);
                }
            }
        } finally {
            filtersLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public void addFilter(Filter<C, R, P> filter, AnyValue conf) {
        filter._conf = conf;
        filtersLock.lock();
        try {
            this.filters.add(filter);
            Collections.sort(this.filters);
        } finally {
            filtersLock.unlock();
        }
    }

    public <T extends Filter<C, R, P>> T removeFilter(Class<T> filterClass) {
        return removeFilter(f -> filterClass.equals(f.getClass()));
    }

    public boolean containsFilter(Class<? extends Filter> filterClass) {
        if (this.headFilter == null || filterClass == null) {
            return false;
        }
        Filter filter = this.headFilter;
        do {
            if (filter.getClass().equals(filterClass)) {
                return true;
            }
        } while ((filter = filter._next) != null);
        return false;
    }

    public boolean containsFilter(String filterClassName) {
        if (this.headFilter == null || filterClassName == null) {
            return false;
        }
        Filter filter = this.headFilter;
        do {
            if (filter.getClass().getName().equals(filterClassName)) {
                return true;
            }
        } while ((filter = filter._next) != null);
        return false;
    }

    @SuppressWarnings("unchecked")
    public <T extends Filter<C, R, P>> T removeFilter(Predicate<T> predicate) {
        if (this.headFilter == null || predicate == null) {
            return null;
        }
        filtersLock.lock();
        try {
            Filter filter = this.headFilter;
            Filter prev = null;
            do {
                if (predicate.test((T) filter)) {
                    break;
                }
                prev = filter;
            } while ((filter = filter._next) != null);
            if (filter != null) {
                if (prev == null) {
                    this.headFilter = filter._next;
                } else {
                    prev._next = filter._next;
                }
                filter._next = null;
                this.filters.remove(filter);
            }
            return (T) filter;
        } finally {
            filtersLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Filter<C, R, P>> List<T> getFilters() {
        return (List) new ArrayList<>(filters);
    }

    @SuppressWarnings("unchecked")
    public abstract void addServlet(S servlet, Object attachment, AnyValue conf, K... mappings);

    public final void dispatch(final R request, final P response) {
        try {
            Traces.computeCurrTraceid(request.getTraceid());
            request.prepare();
            response.filter = this.headFilter;
            response.servlet = this;
            response.inNonBlocking = true;
            response.nextEvent();
        } catch (Throwable t) {
            response.context.logger.log(Level.WARNING, "prepare servlet abort, force to close channel ", t);
            response.error(t);
        }
    }

    protected AnyValue getServletConf(Servlet servlet) {
        return servlet._conf;
    }

    protected void setServletConf(Servlet servlet, AnyValue conf) {
        servlet._conf = conf;
    }

    public List<S> getServlets() {
        return new ArrayList<>(servlets);
    }

    protected Stream<S> servletStream() {
        return servlets.stream();
    }

    public Long getExecuteCounter() {
        return executeCounter.longValue();
    }

    public Long getIllRequestCounter() {
        return illegalRequestCounter.longValue();
    }

}
