/*

*/

package org.redkale.cached.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.service.Service;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class CachedKeyGeneratorLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final CachedModuleEngine engine;

    private final Map<String, CachedKeyGenerator> generatorMap = new ConcurrentHashMap<>();

    public CachedKeyGeneratorLoader(CachedModuleEngine engine) {
        this.engine = engine;
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
            CachedKeyGenerator generator = rf.find(resourceName, CachedKeyGenerator.class);
            if (generator != null) {
                return generator;
            }
            generator = generatorMap.computeIfAbsent(resourceName, n -> {
                for (CachedKeyGenerator instance : ServiceLoader.load(
                        CachedKeyGenerator.class, engine.getApplication().getClassLoader())) {
                    if (Objects.equals(n, instance.key())) {
                        rf.inject(instance);
                        if (instance instanceof Service) {
                            ((Service) instance).init(null);
                        }
                        return instance;
                    }
                }
                return null;
            });
            if (generator != null) {
                rf.register(resourceName, CachedKeyGenerator.class, generator);
                if (field != null) {
                    field.set(srcObj, generator);
                }
            }
            return generator;
        } catch (Exception e) {
            logger.log(Level.SEVERE, CachedKeyGenerator.class.getSimpleName() + " inject error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return CachedKeyGenerator.class;
    }
}
