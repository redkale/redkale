/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.logging.Level;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.mq.MessageAgent;
import org.redkale.net.*;
import org.redkale.net.sncp.*;
import org.redkale.service.Local;
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

        int maxTypeLength = 0;
        int maxNameLength = 0;
        for (SncpServlet en : servlets) {
            maxNameLength = Math.max(maxNameLength, en.getResourceName().length() + 1);
            maxTypeLength = Math.max(maxTypeLength, en.getResourceType().getName().length());
        }
        for (SncpServlet en : servlets) {
            if (sb != null) {
                sb.append("Load ").append(toSimpleString(en, maxTypeLength, maxNameLength)).append(LINE_SEPARATOR);
            }
        }
        if (sb != null && sb.length() > 0) {
            logger.log(Level.FINE, sb.toString());
        }
    }

    private StringBuilder toSimpleString(SncpServlet servlet, int maxTypeLength, int maxNameLength) {
        StringBuilder sb = new StringBuilder();
        Class serviceType = servlet.getResourceType();
        String serviceName = servlet.getResourceName();
        int size = servlet.getActionSize();
        sb.append(SncpServlet.class.getSimpleName()).append(" (type=").append(serviceType.getName());
        int len = maxTypeLength - serviceType.getName().length();
        for (int i = 0; i < len; i++) {
            sb.append(' ');
        }
        sb.append(", serviceid=").append(servlet.getServiceid()).append(", name='").append(serviceName).append("'");
        for (int i = 0; i < maxNameLength - serviceName.length(); i++) {
            sb.append(' ');
        }
        sb.append(", actions.size=").append(size > 9 ? "" : " ").append(size).append(")");
        return sb;
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
            if (Utility.isAbstractOrInterface(clazz)) {
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
        if (!application.isSingletonMode()) {
            this.servletServices.stream()
                .filter(x -> x.getClass().getAnnotation(Local.class) == null) //Local模式的Service不生成SncpServlet
                .forEach(x -> {
                    SncpServlet servlet = sncpServer.addSncpServlet(x);
                    dynServletMap.put(x, servlet);
                    String mq = Sncp.getResourceMQ(x);
                    if (mq != null) {
                        MessageAgent agent = application.getMessageAgent(mq);
                        agent.putService(this, x, servlet);
                    }
                });
        }
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
