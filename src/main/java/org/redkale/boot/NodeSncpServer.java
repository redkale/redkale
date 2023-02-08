/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.reflect.Modifier;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.mq.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.sncp.*;
import org.redkale.service.*;
import org.redkale.util.AnyValue.DefaultAnyValue;
import org.redkale.util.*;

/**
 * SNCP Server节点的配置Server
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@NodeProtocol("SNCP")
public class NodeSncpServer extends NodeServer {

    protected final SncpServer sncpServer;

    private NodeSncpServer(Application application, AnyValue serconf) {
        super(application, createServer(application, serconf));
        this.sncpServer = (SncpServer) this.server;
        this.consumer = sncpServer == null || application.isSingletonMode() ? null : (agent, x) -> {//singleton模式下不生成SncpServlet
            if (x.getClass().getAnnotation(Local.class) != null) {
                return; //本地模式的Service不生成SncpServlet
            }
            SncpDynServlet servlet = sncpServer.addSncpServlet(x);
            dynServletMap.put(x, servlet);
            if (agent != null) {
                agent.putService(this, x, servlet);
            }
        };
    }

    public static NodeServer createNodeServer(Application application, AnyValue serconf) {
        return new NodeSncpServer(application, serconf);
    }

    private static Server createServer(Application application, AnyValue serconf) {
        return new SncpServer(application, application.getStartTime(), serconf, application.getResourceFactory().createChild());
    }

    @Override
    public InetSocketAddress getSocketAddress() {
        return sncpServer == null ? null : sncpServer.getSocketAddress();
    }

    public void consumerAccept(MessageAgent messageAgent, Service service) {
        if (this.consumer != null) {
            this.consumer.accept(messageAgent, service);
        }
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
        //-------------------------------------------------------------------
        if (sncpServer == null) {
            return; //调试时server才可能为null
        }
        final StringBuilder sb = logger.isLoggable(Level.FINE) ? new StringBuilder() : null;
        List<SncpServlet> servlets = sncpServer.getSncpServlets();
        Collections.sort(servlets);
        for (SncpServlet en : servlets) {
            if (sb != null) {
                sb.append("Load ").append(en).append(LINE_SEPARATOR);
            }
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.FINE, sb.toString());
        }
    }

    @Override
    public boolean isSNCP() {
        return true;
    }

    public SncpServer getSncpServer() {
        return sncpServer;
    }

    @Override
    protected void loadFilter(ClassFilter<? extends Filter> filterFilter, ClassFilter otherFilter) throws Exception {
        if (sncpServer != null) {
            loadSncpFilter(this.serverConf.getAnyValue("fliters"), filterFilter);
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadSncpFilter(final AnyValue servletsConf, final ClassFilter<? extends Filter> classFilter) throws Exception {
        final StringBuilder sb = logger.isLoggable(Level.INFO) ? new StringBuilder() : null;
        List<FilterEntry<? extends Filter>> list = new ArrayList(classFilter.getFilterEntrys());
        for (FilterEntry<? extends Filter> en : list) {
            Class<SncpFilter> clazz = (Class<SncpFilter>) en.getType();
            if (Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
            final SncpFilter filter = clazz.getDeclaredConstructor().newInstance();
            resourceFactory.inject(filter, this);
            DefaultAnyValue filterConf = (DefaultAnyValue) en.getProperty();
            this.sncpServer.addSncpFilter(filter, filterConf);
            if (sb != null) {
                sb.append("Load ").append(clazz.getName()).append(LINE_SEPARATOR);
            }
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.INFO, sb.toString());
        }
    }

    @Override
    protected void loadServlet(ClassFilter<? extends Servlet> servletFilter, ClassFilter otherFilter) throws Exception {
        RedkaleClassLoader.putReflectionPublicClasses(SncpServlet.class.getName());
        RedkaleClassLoader.putReflectionPublicClasses(SncpDynServlet.class.getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ClassFilter<Filter> createFilterClassFilter() {
        return createClassFilter(null, null, SncpFilter.class, new Class[]{org.redkale.watch.WatchFilter.class}, null, "filters", "filter");
    }

    @Override
    protected ClassFilter<Servlet> createServletClassFilter() {
        return null;
    }

}
