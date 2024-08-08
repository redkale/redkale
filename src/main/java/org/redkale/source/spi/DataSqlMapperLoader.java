/*

*/

package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.redkale.annotation.AutoLoad;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.source.DataSource;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
@AutoLoad(false)
class DataSqlMapperLoader implements ResourceTypeLoader {

    private final Logger logger = Logger.getLogger(getClass().getSimpleName());

    private final SourceModuleEngine engine;

    public DataSqlMapperLoader(SourceModuleEngine engine) {
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
            ResourceFactory resourceFactory = engine.getResourceFactory();
            Class<? extends DataSqlMapper> mapperType = (Class) field.getType();
            DataSqlMapper old = resourceFactory.find(resourceName, mapperType);
            if (old != null) {
                return old;
            }
            DataSource source = engine.loadDataSource(resourceName, false);
            DataSqlMapper mapper =
                    DataSqlMapperBuilder.createMapper(engine.nativeSqlParser, (DataSqlSource) source, mapperType);
            resourceFactory.register(resourceName, mapperType, mapper);
            if (field != null) {
                field.set(srcObj, mapper);
            }
            return mapper;
        } catch (Exception e) {
            logger.log(Level.SEVERE, DataSqlMapper.class.getSimpleName() + " inject to " + srcObj + " error", e);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RedkaleException(e);
        }
    }

    @Override
    public Type resourceType() {
        return DataSqlMapper.class;
    }
}
