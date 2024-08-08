/*

*/

package org.redkale.cached.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.cached.CachedManager;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class CachedManagerLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());
    private final CachedModuleEngine engine;
    private Map<String, AnyValue> configMap;

    public CachedManagerLoader(CachedModuleEngine engine, Map<String, AnyValue> configMap) {
        this.engine = engine;
        this.configMap = configMap;
    }

    @Override
    public Object load(
            ResourceFactory rf,
            String srcResourceName,
            Object srcObj,
            String resourceName,
            Field field,
            Object attachment) {
        try {
            CachedManager manager = rf.find(resourceName, CachedManager.class);
            if (manager != null) {
                return manager;
            }
            AnyValue config = configMap.get(resourceName);
            if (config == null) {
                throw new RedkaleException(
                        "Not found " + CachedManager.class.getSimpleName() + "(name='" + resourceName + "') config");
            }
            manager = engine.createManager(config);
            if (manager != null) {
                rf.register(resourceName, CachedManager.class, manager);
                engine.cacheManagerMap.put(resourceName, new CachedModuleEngine.ManagerEntity(manager, config));
                if (!engine.getApplication().isCompileMode()) {
                    rf.inject(manager);
                    if (manager instanceof Service) {
                        ((Service) manager).init(config);
                    }
                    logger.info("Load " + CachedManager.class.getSimpleName() + " (type="
                            + manager.getClass().getSimpleName() + ", resourceName='"
                            + resourceName + "', schema='"
                            + manager.getSchema() + "')");
                }
                if (field != null) {
                    field.set(srcObj, manager);
                }
            }
            return manager;
        } catch (Exception e) {
            logger.log(Level.SEVERE, CachedManager.class.getSimpleName() + " inject error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return CachedManager.class;
    }
}
