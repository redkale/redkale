/*
 *
 */
package org.redkale.boot;

import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.util.Environment;

/** @author zhangjx */
public abstract class BootModule {

    protected final Application application;

    protected final ResourceFactory resourceFactory;

    protected final Environment environment;

    protected BootModule(Application application) {
        this.application = application;
        this.resourceFactory = Objects.requireNonNull(application.resourceFactory);
        this.environment = Objects.requireNonNull(application.getEnvironment());
    }

    protected void removeEnvValue(String name) {
        application.envProperties.remove(name);
    }

    protected void putEnvValue(Object name, Object value) {
        application.envProperties.put(name, value);
    }

    protected List<ModuleEngine> getModuleEngines() {
        return application.getModuleEngines();
    }

    protected void reconfigLogging(boolean first, Properties allProps) {
        application.loggingModule.reconfigLogging(first, allProps);
    }

    protected void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        if (namespace != null && namespace.contains("logging")) {
            // 日志配置单独处理
            application.loggingModule.onEnvironmentUpdated(events);
        } else {
            application.onEnvironmentChanged(namespace, events);
        }
    }
}
