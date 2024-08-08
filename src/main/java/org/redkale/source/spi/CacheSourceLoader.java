/*

*/

package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Resource;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.net.Servlet;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.source.CacheSource;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class CacheSourceLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final SourceModuleEngine engine;

    public CacheSourceLoader(SourceModuleEngine engine) {
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
            if ((srcObj instanceof Service) && Sncp.isRemote((Service) srcObj)) {
                return null; // 远程模式不得注入
            }
            if (srcObj instanceof Servlet) {
                throw new RedkaleException("CacheSource cannot inject in Servlet " + srcObj);
            }
            final boolean ws = (srcObj instanceof org.redkale.net.http.WebSocketNodeService);
            CacheSource source = engine.loadCacheSource(resourceName, ws);
            if (field != null) {
                field.set(srcObj, source);
                Resource res = field.getAnnotation(Resource.class);
                if (res != null && res.required() && source == null) {
                    throw new RedkaleException("CacheSource (resourceName='" + resourceName + "') not found");
                } else {
                    logger.info("Load CacheSource (type="
                            + (source == null ? null : source.getClass().getSimpleName()) + ", resourceName='"
                            + resourceName + "')");
                }
            }
            return source;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DataSource inject error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return CacheSource.class;
    }

    @Override
    public boolean autoNone() {
        return false;
    }
}
