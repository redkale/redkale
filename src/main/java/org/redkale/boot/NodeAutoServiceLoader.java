/*

*/

package org.redkale.boot;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Local;
import org.redkale.service.Service;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
class NodeAutoServiceLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final NodeServer nodeServer;

    public NodeAutoServiceLoader(NodeServer nodeServer) {
        this.nodeServer = nodeServer;
    }

    @Override
    public Object load(
            ResourceFactory rf,
            String srcResourceName,
            final Object srcObj,
            final String resourceName,
            Field field,
            final Object attachment) {
        Application application = nodeServer.getApplication();
        final ResourceFactory appResFactory = application.getResourceFactory();
        Class<Service> serviceImplClass = Service.class;
        try {
            serviceImplClass = (Class) field.getType();
            if (serviceImplClass.getAnnotation(Local.class) == null) {
                return null;
            }
            if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                return null; // 远程模式不得注入 AutoLoad Service
            }
            boolean auto = true;
            AutoLoad al = serviceImplClass.getAnnotation(AutoLoad.class);
            if (al != null) {
                auto = al.value();
            }
            org.redkale.util.AutoLoad al2 = serviceImplClass.getAnnotation(org.redkale.util.AutoLoad.class);
            if (al2 != null) {
                auto = al2.value();
            }
            if (auto && !Utility.isAbstractOrInterface(serviceImplClass)) {
                return null;
            }

            // ResourceFactory resfactory = (isSNCP() ? appResFactory : resourceFactory);
            Service service;
            if (Modifier.isFinal(serviceImplClass.getModifiers()) || Sncp.isComponent(serviceImplClass)) {
                service = (Service) serviceImplClass.getConstructor().newInstance();
            } else if (Utility.isAbstractOrInterface(serviceImplClass)) { // 没有具体实现类
                AsmMethodBoost methodBoost = application.createAsmMethodBoost(true, serviceImplClass);
                MessageAgent mqAgent = appResFactory.find("", MessageAgent.class);
                service = Sncp.createRemoteService(
                        nodeServer.serverClassLoader,
                        resourceName,
                        serviceImplClass,
                        methodBoost,
                        appResFactory,
                        application.getSncpRpcGroups(),
                        nodeServer.sncpClient,
                        mqAgent,
                        null,
                        null);
            } else {
                AsmMethodBoost methodBoost = application.createAsmMethodBoost(false, serviceImplClass);
                service = Sncp.createLocalService(
                        nodeServer.serverClassLoader,
                        resourceName,
                        serviceImplClass,
                        methodBoost,
                        appResFactory,
                        application.getSncpRpcGroups(),
                        nodeServer.sncpClient,
                        null,
                        null,
                        null);
            }
            appResFactory.register(resourceName, serviceImplClass, service);

            field.set(srcObj, service);
            rf.inject(resourceName, service, nodeServer); // 给其可能包含@Resource的字段赋值;
            if (!application.isCompileMode() && !Sncp.isRemote(service)) {
                service.init(null);
            }
            logger.info("Load Service(" + (Sncp.isRemote(service) ? "Remote" : "@Local") + " @AutoLoad service = "
                    + serviceImplClass.getSimpleName() + ", resourceName = '" + resourceName + "')");
            return service;
        } catch (Exception e) {
            logger.log(
                    Level.SEVERE,
                    "Load @AutoLoad(false) Service inject " + serviceImplClass + " to " + srcObj + " error",
                    e);
            return null;
        }
    }

    @Override
    public Type resourceType() {
        return Service.class;
    }
}
