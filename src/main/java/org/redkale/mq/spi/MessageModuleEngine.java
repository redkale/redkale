/*
 *
 */
package org.redkale.mq.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Component;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.boot.Application;
import org.redkale.boot.ClassFilter;
import org.redkale.boot.ModuleEngine;
import org.redkale.boot.NodeServer;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertFactory;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.ResourceAnnotationLoader;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.inject.ResourceTypeLoader;
import org.redkale.mq.MessageConsumer;
import org.redkale.mq.MessageEvent;
import org.redkale.mq.MessageManager;
import org.redkale.mq.MessageProducer;
import org.redkale.mq.Messaged;
import org.redkale.mq.ResourceConsumer;
import org.redkale.mq.ResourceProducer;
import org.redkale.net.http.RestException;
import org.redkale.net.sncp.Sncp;
import org.redkale.service.Service;
import org.redkale.util.AnyValue;
import org.redkale.util.AnyValueWriter;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleClassLoader.DynBytesClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.Utility;

/** @author zhangjx */
public class MessageModuleEngine extends ModuleEngine {

    // MQ管理配置资源
    // @since 2.8.0
    private final Properties messageProperties = new Properties();

    //
    private final Map<String, List<MessageConsumer>> agentConsumers = new ConcurrentHashMap<>();

    // MQ管理接口
    // @since 2.1.0
    private MessageAgent[] messageAgents;

    private List<ClassFilter.FilterEntry<? extends MessageConsumer>> allMessageConsumerEntrys;

    public MessageModuleEngine(Application application) {
        super(application);
    }

    /**
     * 动态扩展类的方法
     *
     * @param remote 是否远程模式
     * @param serviceClass 类
     * @return 方法动态扩展器
     */
    @Override
    public AsmMethodBoost createAsmMethodBoost(boolean remote, Class serviceClass) {
        return new MessageAsmMethodBoost(remote, serviceClass, this);
    }

    // 在doInstance方法里被调用
    void addMessageConsumer(MessageConsumer consumer) {
        ResourceConsumer rc = consumer.getClass().getAnnotation(ResourceConsumer.class);
        String mqName = environment.getPropertyValue(rc.mq());
        if (rc.required() && findMessageAgent(mqName) == null) {
            throw new RedkaleException("Not found " + MessageAgent.class.getSimpleName() + "(name = " + mqName + ") on "
                    + consumer.getClass().getName());
        }
        agentConsumers
                .computeIfAbsent(mqName, v -> new CopyOnWriteArrayList<>())
                .add(consumer);
    }

    /**
     * 判断模块的配置项合并策略， 返回null表示模块不识别此配置项
     *
     * @param path 配置项路径
     * @param key 配置项名称
     * @param val1 配置项原值
     * @param val2 配置项新值
     * @return MergeEnum
     */
    @Override
    public AnyValue.MergeEnum mergeAppConfigStrategy(String path, String key, AnyValue val1, AnyValue val2) {
        if ("".equals(path) && "mq".equals(key)) {
            if (Objects.equals(val1.getValue("name"), val2.getValue("name"))) {
                return AnyValue.MergeEnum.REPLACE;
            } else {
                return AnyValue.MergeEnum.DEFAULT;
            }
        }
        return null;
    }

    /** 配置项加载后被调用 */
    @Override
    public void onEnvironmentLoaded(Properties allProps) {
        if (this.messageAgents == null) {
            return;
        }
        allProps.forEach((key, val) -> {
            if (key.toString().startsWith("redkale.mq.") || key.toString().startsWith("redkale.mq[")) {
                if (key.toString().endsWith(".name")) {
                    logger.log(Level.WARNING, "skip illegal key " + key + " in mq config, key cannot endsWith '.name'");
                } else {
                    this.messageProperties.put(key, val);
                }
            }
        });
    }

    /** 结束Application.init方法前被调用 */
    @Override
    public void onAppPostInit() {
        MessageAgent[] mqs = null;
        AnyValue[] mqConfs = application.getAppConfig().getAnyValues("mq");
        if (mqConfs != null && mqConfs.length > 0) {
            mqs = new MessageAgent[mqConfs.length];
            Set<String> mqnames = new HashSet<>();
            for (int i = 0; i < mqConfs.length; i++) {
                AnyValue mqConf = mqConfs[i];
                String names = environment.getPropertyValue(mqConf.getValue("name")); // 含,或者;表示多个别名使用同一mq对象
                if (names != null && !names.isEmpty()) {
                    for (String n : names.replace(',', ';').split(";")) {
                        if (n.trim().isEmpty()) {
                            continue;
                        }
                        if (mqnames.contains(n.trim())) {
                            throw new RedkaleException("mq.name(" + n.trim() + ") is repeat");
                        }
                        mqnames.add(n.trim());
                    }
                } else if (names != null && names.isEmpty()) {
                    String n = "";
                    if (mqnames.contains(n.trim())) {
                        throw new RedkaleException("mq.name(" + n.trim() + ") is repeat");
                    }
                    mqnames.add(n);
                }
                try {
                    String classVal = environment.getPropertyValue(
                            mqConf.getValue("type", mqConf.getValue("value"))); // 兼容value字段
                    if (classVal == null
                            || classVal.isEmpty()
                            || classVal.indexOf('.') < 0) { // 不包含.表示非类名，比如值: kafka, pulsar
                        Iterator<MessageAgentProvider> it = ServiceLoader.load(
                                        MessageAgentProvider.class, application.getClassLoader())
                                .iterator();
                        RedkaleClassLoader.putServiceLoader(MessageAgentProvider.class);
                        while (it.hasNext()) {
                            MessageAgentProvider provider = it.next();
                            if (provider != null) {
                                RedkaleClassLoader.putReflectionPublicConstructors(
                                        provider.getClass(), provider.getClass().getName()); // loader class
                            }
                            if (provider != null && provider.acceptsConf(mqConf)) {
                                mqs[i] = provider.createInstance();
                                mqs[i].setConfig(mqConf);
                                break;
                            }
                        }
                        if (mqs[i] == null) {
                            logger.log(
                                    Level.SEVERE,
                                    "load application mq resource, but not found name='value' value error: " + mqConf);
                        }
                    } else {
                        Class type = application.getClassLoader().loadClass(classVal);
                        if (!MessageAgent.class.isAssignableFrom(type)) {
                            logger.log(
                                    Level.SEVERE,
                                    "load application mq resource, but not found " + MessageAgent.class.getSimpleName()
                                            + " implements class error: " + mqConf);
                        } else {
                            RedkaleClassLoader.putReflectionDeclaredConstructors(type, type.getName());
                            mqs[i] =
                                    (MessageAgent) type.getDeclaredConstructor().newInstance();
                            mqs[i].setConfig(mqConf);
                        }
                    }
                    // 此时不能执行mq.init，因内置的对象可能依赖config.properties配置项
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "load application mq resource error: " + mqs[i], e);
                }
            }
        }
        this.messageAgents = mqs;
        // ------------------------------------ 注册 ResourceProducer MessageProducer ------------------------------------
        resourceFactory.register(new ResourceAnnotationLoader<ResourceProducer>() {
            @Override
            public void load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    ResourceProducer annotation,
                    Field field,
                    Object attachment) {
                if (field.getType() != MessageProducer.class) {
                    throw new RestException("@" + ResourceProducer.class.getSimpleName() + " must on "
                            + MessageProducer.class.getName() + " type field, but on " + field);
                }
                MessageAgent agent = resourceFactory.find(annotation.mq(), MessageAgent.class);
                if (!annotation.required() && agent == null) {
                    return;
                }
                if (agent == null) {
                    throw new RedkaleException("Not found " + MessageAgent.class.getSimpleName() + "(name = "
                            + annotation.mq() + ") on " + field);
                }
                try {
                    MessageProducer producer = agent.loadMessageProducer(annotation);
                    field.set(srcObj, producer);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception e) {
                    throw new RedkaleException(field + "inject error", e);
                }
            }

            @Override
            public Class<ResourceProducer> annotationType() {
                return ResourceProducer.class;
            }
        });

        if (this.messageAgents == null) {
            return;
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.log(Level.FINER, "MessageAgent initing");
        }
        long s = System.currentTimeMillis();
        for (MessageAgent agent : this.messageAgents) {
            String agentName = agent.getConfig().getValue("name", "");
            if (!application.isCompileMode()) {
                this.resourceFactory.inject(agent);
                agent.init(agent.getConfig());
                agentName = agent.getName();
            } else {
                agent.name = agentName;
                agent.application = application;
            }
            this.resourceFactory.register(agentName, MessageManager.class, agent);
            this.resourceFactory.register(agentName, MessageAgent.class, agent);
        }
        logger.info("MessageAgent init in " + (System.currentTimeMillis() - s) + " ms");
        // 加载MessageConsumer
        s = System.currentTimeMillis();
        final RedkaleClassLoader cl = application.getServerClassLoader();
        ClassFilter allFilter = new ClassFilter(cl, ResourceConsumer.class, MessageConsumer.class);
        application.loadServerClassFilters(allFilter);
        List<ClassFilter.FilterEntry<? extends MessageConsumer>> allEntrys = new ArrayList(allFilter.getFilterEntrys());
        for (ClassFilter.FilterEntry<? extends MessageConsumer> en : allEntrys) {
            Class<? extends MessageConsumer> clazz = en.getType();
            AutoLoad auto = clazz.getAnnotation(AutoLoad.class);
            if (auto != null && !auto.value()) {
                continue;
            }
            ResourceConsumer res = clazz.getAnnotation(ResourceConsumer.class);
            if (res != null && res.required() && findMessageAgent(res.mq()) == null) {
                throw new RedkaleException("Not found " + MessageAgent.class.getSimpleName() + "(name = " + res.mq()
                        + ") on " + clazz.getName());
            }
            if (res != null && Utility.isEmpty(res.regexTopic()) && Utility.isEmpty(res.topics())) {
                throw new RedkaleException("@" + ResourceConsumer.class.getSimpleName()
                        + " regexTopic and topics both empty on " + clazz.getName());
            }
            if (res != null && Utility.isNotEmpty(res.regexTopic()) && Utility.isNotEmpty(res.topics())) {
                throw new RedkaleException("@" + ResourceConsumer.class.getSimpleName()
                        + " regexTopic and topics both not empty on " + clazz.getName());
            }
        }
        this.allMessageConsumerEntrys = allEntrys;
        logger.info("MessageAgent load MessageConsumer in " + (System.currentTimeMillis() - s) + " ms");
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events 变更项
     */
    @Override
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        Set<String> messageRemovedKeys = new HashSet<>();
        Properties messageChangedProps = new Properties();

        for (ResourceEvent<String> event : events) {
            if (event.name().startsWith("redkale.mq.") || event.name().startsWith("redkale.mq[")) {
                if (event.name().endsWith(".name")) {
                    logger.log(
                            Level.WARNING,
                            "skip illegal key " + event.name() + " in mq config " + (namespace == null ? "" : namespace)
                                    + ", key cannot endsWith '.name'");
                } else {
                    if (!Objects.equals(event.newValue(), this.messageProperties.getProperty(event.name()))) {
                        if (event.newValue() == null) {
                            if (this.messageProperties.containsKey(event.name())) {
                                messageRemovedKeys.add(event.name());
                            }
                        } else {
                            messageChangedProps.put(event.name(), event.newValue());
                        }
                    }
                }
            }
        }
        // MQ配置项的变更
        if (!messageChangedProps.isEmpty() || !messageRemovedKeys.isEmpty()) {
            Set<String> messageNames = new LinkedHashSet<>();
            List<String> keys = new ArrayList<>();
            keys.addAll(messageRemovedKeys);
            keys.addAll((Set) messageChangedProps.keySet());
            for (final String key : keys) {
                if (key.startsWith("redkale.mq[")) {
                    messageNames.add(key.substring("redkale.mq[".length(), key.indexOf(']')));
                } else if (key.startsWith("redkale.mq.")) {
                    messageNames.add(key.substring("redkale.mq.".length(), key.indexOf('.', "redkale.mq.".length())));
                }
            }
            // 更新MQ
            for (String mqName : messageNames) {
                MessageAgent agent = Utility.find(messageAgents, s -> Objects.equals(s.resourceName(), mqName));
                if (agent == null) {
                    continue; // 多余的数据源
                }
                final AnyValueWriter old = (AnyValueWriter) findMQConfig(mqName);
                Properties newProps = new Properties();
                this.messageProperties.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.mq[" + mqName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.mq." + mqName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; // 不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                });
                List<ResourceEvent> changeEvents = new ArrayList<>();
                messageChangedProps.forEach((k, v) -> {
                    final String key = k.toString();
                    String prefix = "redkale.mq[" + mqName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.mq." + mqName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return; // 不是同一name数据源配置项
                    }
                    newProps.put(k, v);
                    changeEvents.add(ResourceEvent.create(
                            key.substring(prefix.length()), v, this.messageProperties.getProperty(key)));
                });
                messageRemovedKeys.forEach(k -> {
                    final String key = k;
                    String prefix = "redkale.mq[" + mqName + "].";
                    int pos = key.indexOf(prefix);
                    if (pos < 0) {
                        prefix = "redkale.mq." + mqName + ".";
                        pos = key.indexOf(prefix);
                    }
                    if (pos < 0) {
                        return;
                    }
                    newProps.remove(k); // 不是同一name数据源配置项
                    changeEvents.add(ResourceEvent.create(
                            key.substring(prefix.length()), null, this.messageProperties.getProperty(key)));
                });
                if (!changeEvents.isEmpty()) {
                    AnyValueWriter back = old.copy();
                    try {
                        old.replace(AnyValue.loadFromProperties(newProps)
                                .getAnyValue("redkale")
                                .getAnyValue("mq")
                                .getAnyValue(mqName));
                        agent.onResourceChange(changeEvents.toArray(new ResourceEvent[changeEvents.size()]));
                    } catch (RuntimeException e) {
                        old.replace(back); // 还原配置
                        throw e;
                    }
                }
            }
            messageRemovedKeys.forEach(this.messageProperties::remove);
            this.messageProperties.putAll(messageChangedProps);
        }
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param server NodeServer
     * @param service Service
     */
    @Override
    public void onServicePostInit(NodeServer server, Service service) {
        if (Sncp.isSncpDyn(service)) {
            return; // 跳过动态生成的Service
        }
        MessageAsmMethodBoost boost = null;
        for (Method method : service.getClass().getDeclaredMethods()) {
            Messaged messaged = method.getAnnotation(Messaged.class);
            if (messaged == null) {
                continue;
            }
            if (Modifier.isStatic(method.getModifiers())) {
                throw new RedkaleException(
                        "@" + Messaged.class.getSimpleName() + " cannot on static method, but on " + method);
            }
            if (!Modifier.isPublic(method.getModifiers())) {
                throw new RedkaleException("@" + Messaged.class.getSimpleName() + " must on public method in @"
                        + Component.class.getSimpleName() + " class, but on " + method);
            }
            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != MessageEvent[].class) {
                throw new RedkaleException("@" + Messaged.class.getSimpleName()
                        + " must on one parameter(type: MessageEvent[]) method, but on " + method);
            }

            Type messageType = MessageAsmMethodBoost.getMethodMessageType(method);
            Convert convert = ConvertFactory.findConvert(messaged.convertType());
            convert.getFactory().loadDecoder(messageType);
            if (boost == null) {
                boost = new MessageAsmMethodBoost(false, service.getClass(), this);
                String newDynName = "org/redkaledyn/service/local/_DynMessageService__"
                        + service.getClass().getName().replace('.', '_').replace('$', '_');
                boost.createInnerConsumer(null, service.getClass(), method, messageType, messaged, newDynName, null);
            }
        }
        if (boost != null && Utility.isNotEmpty(boost.consumerBytes)) {
            DynBytesClassLoader classLoader = DynBytesClassLoader.create(null);
            boost.consumerBytes.forEach((innerFullName, bytes) -> {
                try {
                    String clzName = innerFullName.replace('/', '.');
                    Class<? extends MessageConsumer> clazz = (Class) classLoader.loadClass(clzName, bytes);
                    RedkaleClassLoader.putDynClass(clzName, bytes, clazz);
                    RedkaleClassLoader.putReflectionPublicConstructors(clazz, clzName);
                    MessageConsumer consumer = (MessageConsumer) clazz.getConstructors()[0].newInstance(service);
                    addMessageConsumer(consumer);
                } catch (Exception e) {
                    throw new RedkaleException(e);
                }
            });
        }
    }

    /** 服务全部启动后被调用 */
    @Override
    public void onServersPostStart() {
        if (this.messageAgents == null) {
            return;
        }
        // startMessageAgent
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, MessageAgent.class.getSimpleName() + " starting");
        }
        long s = System.currentTimeMillis();
        Set<String> names = new HashSet<>();
        ResourceFactory serResourceFactory = this.resourceFactory.createChild();
        List<ResourceFactory> factorys = new ArrayList<>();
        for (NodeServer ns : application.getNodeServers()) {
            factorys.add(ns.getResourceFactory());
        }
        serResourceFactory.register(new ResourceTypeLoader() {
            @Override
            public Object load(
                    ResourceFactory rf,
                    String srcResourceName,
                    Object srcObj,
                    String resourceName,
                    Field field,
                    Object attachment) {
                for (ResourceFactory f : factorys) {
                    Object val = f.find(resourceName, field.getGenericType());
                    if (val != null) {
                        return val;
                    }
                }
                return null;
            }

            @Override
            public Type resourceType() {
                return Object.class;
            }

            @Override
            public boolean autoNone() {
                return false;
            }
        });
        for (MessageAgent agent : this.messageAgents) {
            names.add(agent.getName());
            List<MessageConsumer> consumers =
                    agentConsumers.getOrDefault(agent.getName(), new CopyOnWriteArrayList<>());
            AnyValue consumerConf = agent.getConfig().getAnyValue("consumer");
            if (consumerConf != null) { // 加载 MessageConsumer
                final RedkaleClassLoader cl = application.getServerClassLoader();
                ClassFilter filter = new ClassFilter(cl, ResourceConsumer.class, MessageConsumer.class);
                if (consumerConf.getBoolValue("autoload", true)) {
                    String includes = consumerConf.getValue("includes", "");
                    String excludes = consumerConf.getValue("excludes", "");
                    filter.setIncludePatterns(includes.replace(',', ';').split(";"));
                    filter.setExcludePatterns(excludes.replace(',', ';').split(";"));
                } else {
                    filter.setRefused(true);
                }

                try {
                    for (ClassFilter.FilterEntry<? extends MessageConsumer> en : allMessageConsumerEntrys) {
                        Class<? extends MessageConsumer> clazz = en.getType();
                        ResourceConsumer res = clazz.getAnnotation(ResourceConsumer.class);
                        if (!filter.accept(clazz.getName())
                                || !Objects.equals(agent.getName(), environment.getPropertyValue(res.mq()))) {
                            continue;
                        }
                        AutoLoad auto = clazz.getAnnotation(AutoLoad.class);
                        if ((auto != null && !auto.value())
                                && (filter.getIncludePatterns() == null || !filter.acceptPattern(clazz.getName()))) {
                            continue;
                        }
                        RedkaleClassLoader.putReflectionDeclaredConstructors(clazz, clazz.getName());
                        final MessageConsumer consumer =
                                clazz.getDeclaredConstructor().newInstance();
                        serResourceFactory.inject(consumer);
                        consumers.add(consumer);
                    }
                } catch (Exception e) {
                    throw new RedkaleException(e);
                }
                for (MessageConsumer consumer : consumers) {
                    consumer.init(consumerConf);
                }
            }
            agent.start(consumers);
        }
        logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") started in "
                + (System.currentTimeMillis() - s) + " ms");
    }

    /** 服务全部停掉前被调用 */
    @Override
    public void onServersPreStop() {
        if (application.isCompileMode() && this.messageAgents != null) {
            Set<String> names = new HashSet<>();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent stopping");
            }
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                if (!application.isCompileMode()) {
                    agent.stop();
                }
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") stop in "
                    + (System.currentTimeMillis() - s) + " ms");
        }
    }

    /** 服务全部停掉后被调用 */
    @Override
    public void onServersPostStop() {
        if (this.messageAgents != null) {
            Set<String> names = new HashSet<>();
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER, "MessageAgent destroying");
            }
            long s = System.currentTimeMillis();
            for (MessageAgent agent : this.messageAgents) {
                names.add(agent.getName());
                if (!application.isCompileMode()) {
                    agent.destroy(agent.getConfig());
                }
            }
            logger.info("MessageAgent(names=" + JsonConvert.root().convertTo(names) + ") destroy in "
                    + (System.currentTimeMillis() - s) + " ms");
        }
    }

    private AnyValue findMQConfig(String mqName) {
        AnyValue mqsNode = application.getAppConfig().getAnyValue("mq");
        if (mqsNode != null) {
            AnyValue confNode = mqsNode.getAnyValue(mqName);
            if (confNode != null) { // 必须要设置name属性
                ((AnyValueWriter) confNode).setValue("name", mqName);
            }
            return confNode;
        }
        return null;
    }

    public MessageAgent findMessageAgent(String mqName) {
        if (this.messageAgents != null) {
            String name = environment.getPropertyValue(mqName);
            for (MessageAgent agent : this.messageAgents) {
                if (Objects.equals(agent.getName(), name)) {
                    return agent;
                }
            }
        }
        return null;
    }
}
