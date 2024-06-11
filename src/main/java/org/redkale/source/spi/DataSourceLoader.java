/*

*/

package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.source.DataSource;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
class DataSourceLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final SourceModuleEngine engine;

    public DataSourceLoader(SourceModuleEngine engine) {
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
            DataSource source = engine.loadDataSource(resourceName, false);
            if (field != null) {
                field.set(srcObj, source);
            }
            return source;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "DataSource inject to " + srcObj + " error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return DataSource.class;
    }
}
