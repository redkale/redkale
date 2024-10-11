/*

*/

package org.redkale.boot;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Priority;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.net.sncp.Sncp;
import org.redkale.net.sncp.SncpRpcGroups;
import org.redkale.service.Service;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class NodeExpectServiceLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final NodeServer nodeServer;

    private final Class type;

    private final Class<? extends Service> serviceImplClass;

    private final AtomicInteger serviceCount;

    private final SncpRpcGroups rpcGroups;

    private final ClassFilter.FilterEntry<? extends Service> entry;

    private final String group;

    private final boolean localMode;

    public NodeExpectServiceLoader(
            NodeServer nodeServer,
            Class type,
            Class<? extends Service> serviceImplClass,
            AtomicInteger serviceCount,
            SncpRpcGroups rpcGroups,
            ClassFilter.FilterEntry<? extends Service> entry,
            String group,
            boolean localMode) {
        this.nodeServer = nodeServer;
        this.type = type;
        this.serviceImplClass = serviceImplClass;
        this.serviceCount = serviceCount;
        this.rpcGroups = rpcGroups;
        this.entry = entry;
        this.group = group;
        this.localMode = localMode;
    }

    @Override
    public Object load(
            ResourceFactory rf,
            String srcResourceName,
            final Object srcObj,
            final String resourceName,
            Field field,
            final Object attachment) {
        try {
            Application application = nodeServer.getApplication();
            final ResourceFactory resourceFactory = nodeServer.getResourceFactory();
            final ResourceFactory appResourceFactory = application.getResourceFactory();
            ResourceFactory regFactory = nodeServer.isSNCP() ? application.getResourceFactory() : resourceFactory;

            if (Sncp.loadRemoteMethodActions(Sncp.getResourceType(serviceImplClass))
                            .isEmpty()
                    && (serviceImplClass.getAnnotation(Priority.class) == null
                            && serviceImplClass.getAnnotation(javax.annotation.Priority.class)
                                    == null)) { // class没有可用的方法且没有标记启动优先级的， 通常为BaseService
                if (!serviceImplClass.getName().startsWith("org.redkale.")
                        && !serviceImplClass.getSimpleName().contains("Base")) {
                    logger.log(
                            Level.FINE,
                            serviceImplClass + " cannot load because not found less one public non-final method");
                }
                return null;
            }
            RedkaleClassLoader.putReflectionPublicMethods(serviceImplClass.getName());
            MessageAgent mqAgent = nodeServer.getMessageAgent(entry.getProperty());
            Service service;
            if (Sncp.isComponent(serviceImplClass)) { // Component
                RedkaleClassLoader.putReflectionPublicConstructors(serviceImplClass, serviceImplClass.getName());
                if (!nodeServer.acceptsComponent(serviceImplClass)) {
                    return null;
                }
                service = serviceImplClass.getDeclaredConstructor().newInstance();
            } else if (srcObj instanceof WebSocketServlet || localMode) { // 本地模式
                AsmMethodBoost methodBoost = application.createAsmMethodBoost(false, serviceImplClass);
                service = Sncp.createLocalService(
                        nodeServer.serverClassLoader,
                        resourceName,
                        serviceImplClass,
                        methodBoost,
                        appResourceFactory,
                        rpcGroups,
                        nodeServer.sncpClient,
                        mqAgent,
                        group,
                        entry.getProperty());
            } else {
                AsmMethodBoost methodBoost = application.createAsmMethodBoost(true, serviceImplClass);
                service = Sncp.createRemoteService(
                        nodeServer.serverClassLoader,
                        resourceName,
                        serviceImplClass,
                        methodBoost,
                        appResourceFactory,
                        rpcGroups,
                        nodeServer.sncpClient,
                        mqAgent,
                        group,
                        entry.getProperty());
            }
            final Class resType = Sncp.getResourceType(service);
            if (rf.find(resourceName, resType) == null) {
                regFactory.register(resourceName, resType, service);
            } else if (nodeServer.isSNCP() && !entry.isAutoload()) {
                throw new RedkaleException(resType.getSimpleName() + "(class:" + serviceImplClass.getName() + ", name:"
                        + resourceName + ", group:" + group + ") is repeat.");
            }
            if (Sncp.isRemote(service)) {
                nodeServer.remoteServices.add(service);
                if (mqAgent != null) {
                    nodeServer.sncpRemoteAgents.put(mqAgent.getName(), mqAgent);
                }
            } else {
                if (field != null) {
                    rf.inject(resourceName, service); // 动态加载的Service也存在按需加载的注入资源
                }
                nodeServer.localServices.add(service);
                if (!Sncp.isComponent(service)) {
                    nodeServer.servletServices.add(service);
                }
            }
            serviceCount.incrementAndGet();
            return service;
        } catch (Exception e) {
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return type;
    }
}
