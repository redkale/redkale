/*

*/

package org.redkale.boot;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.asm.AsmMethodBoost;
import static org.redkale.boot.Application.RESNAME_SNCP_ADDRESS;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.http.WebSocketNode;
import org.redkale.net.http.WebSocketNodeService;
import org.redkale.net.http.WebSocketServlet;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class NodeWebSocketNodeLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final NodeServer nodeServer;

    public NodeWebSocketNodeLoader(NodeServer nodeServer) {
        this.nodeServer = nodeServer;
    }

    @Override
    public Object load(
            ResourceFactory rf,
            String srcResourceName,
            Object srcObj,
            String resourceName,
            Field field,
            Object attachment) {
        if (srcObj instanceof WebSocketServlet) {
            return loadInServlet(rf, srcResourceName, srcObj, resourceName, field, attachment);
        } else {
            return loadInService(rf, srcResourceName, srcObj, resourceName, field, attachment);
        }
    }

    private Object loadInService(
            ResourceFactory rf,
            String srcResourceName,
            Object srcObj,
            String resourceName,
            Field field,
            Object attachment) {
        try {
            if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                return null; // 远程模式不得注入
            }
            Application application = nodeServer.getApplication();
            final ResourceFactory appResFactory = application.getResourceFactory();
            final ResourceFactory resourceFactory = nodeServer.getResourceFactory();
            Service nodeService = rf.find(resourceName, WebSocketNode.class);
            if (nodeService == null) {
                final HashSet<String> groups = new HashSet<>();
                if (groups.isEmpty() && nodeServer.isSNCP() && nodeServer.sncpGroup != null) {
                    groups.add(nodeServer.sncpGroup);
                }
                AsmMethodBoost methodBoost = application.createAsmMethodBoost(false, WebSocketNodeService.class);
                nodeService = Sncp.createLocalService(
                        nodeServer.serverClassLoader,
                        resourceName,
                        WebSocketNodeService.class,
                        methodBoost,
                        application.getResourceFactory(),
                        application.getSncpRpcGroups(),
                        nodeServer.sncpClient,
                        null,
                        (String) null,
                        (AnyValue) null);
                (nodeServer.isSNCP() ? appResFactory : resourceFactory)
                        .register(resourceName, WebSocketNode.class, nodeService);
                ((org.redkale.net.http.WebSocketNodeService) nodeService).setName(resourceName);
            }
            resourceFactory.inject(resourceName, nodeService, nodeServer);
            if (field != null) {
                field.set(srcObj, nodeService);
            }
            if (Sncp.isRemote(nodeService)) {
                nodeServer.remoteServices.add(nodeService);
            } else {
                rf.inject(resourceName, nodeService); // 动态加载的Service也存在按需加载的注入资源
                nodeServer.localServices.add(nodeService);
                if (!Sncp.isComponent(nodeService)) {
                    nodeServer.servletServices.add(nodeService);
                }
            }
            return nodeService;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "WebSocketNode inject error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    private Object loadInServlet(
            ResourceFactory rf,
            String srcResourceName,
            Object srcObj,
            String resourceName,
            Field field,
            Object attachment) { // 主要用于单点的服务
        try {
            if (!(srcObj instanceof WebSocketServlet)) {
                return null;
            }
            Application application = nodeServer.getApplication();
            final ResourceFactory regFactory = application.getResourceFactory();
            final ResourceFactory resourceFactory = nodeServer.getResourceFactory();
            ResourceTypeLoader loader = null;
            ResourceFactory sncpResFactory = null;
            for (NodeServer ns : application.servers) {
                if (!ns.isSNCP()) {
                    continue;
                }
                sncpResFactory = ns.resourceFactory;
                loader = sncpResFactory.findResourceTypeLoader(WebSocketNode.class, field);
                if (loader != null) {
                    break;
                }
            }
            Service nodeService = null;
            if (loader != null) {
                nodeService =
                        (Service) loader.load(sncpResFactory, srcResourceName, srcObj, resourceName, field, attachment);
            }
            regFactory.lock();
            try {
                if (nodeService == null) {
                    nodeService = (Service) rf.find(resourceName, WebSocketNode.class);
                }
                if (sncpResFactory != null && resourceFactory.find(RESNAME_SNCP_ADDRESS, String.class) == null) {
                    resourceFactory.register(
                            RESNAME_SNCP_ADDRESS,
                            InetSocketAddress.class,
                            sncpResFactory.find(RESNAME_SNCP_ADDRESS, InetSocketAddress.class));
                    resourceFactory.register(
                            RESNAME_SNCP_ADDRESS,
                            SocketAddress.class,
                            sncpResFactory.find(RESNAME_SNCP_ADDRESS, SocketAddress.class));
                    resourceFactory.register(
                            RESNAME_SNCP_ADDRESS,
                            String.class,
                            sncpResFactory.find(RESNAME_SNCP_ADDRESS, String.class));
                }
                if (nodeService == null) {
                    MessageAgent messageAgent = null;
                    try {
                        Field c = WebSocketServlet.class.getDeclaredField("messageAgent");
                        RedkaleClassLoader.putReflectionField("messageAgent", c);
                        c.setAccessible(true);
                        messageAgent = (MessageAgent) c.get(srcObj);
                    } catch (Exception ex) {
                        logger.log(Level.WARNING, "WebSocketServlet getMessageAgent error", ex);
                    }
                    AsmMethodBoost methodBoost = application.createAsmMethodBoost(false, WebSocketNodeService.class);
                    nodeService = Sncp.createLocalService(
                            nodeServer.serverClassLoader,
                            resourceName,
                            WebSocketNodeService.class,
                            methodBoost,
                            application.getResourceFactory(),
                            application.getSncpRpcGroups(),
                            nodeServer.sncpClient,
                            messageAgent,
                            (String) null,
                            (AnyValue) null);
                    regFactory.register(resourceName, WebSocketNode.class, nodeService);
                }
                resourceFactory.inject(resourceName, nodeService, nodeServer);
                if (field != null) {
                    field.set(srcObj, nodeService);
                }
                logger.fine("Load Service " + nodeService);
                return nodeService;
            } finally {
                regFactory.unlock();
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "WebSocketNode inject error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return WebSocketNode.class;
    }
}
