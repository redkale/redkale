/*

*/

package org.redkale.test.mq;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redkale.boot.Application;
import org.redkale.boot.LoggingBaseHandler;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.spi.MessageAgent;
import org.redkale.mq.spi.MessageAsmMethodBoost;
import org.redkale.mq.spi.MessageClientProducer;
import org.redkale.mq.spi.MessageModuleEngine;
import org.redkale.net.AsyncGroup;
import org.redkale.net.client.ClientAddress;
import org.redkale.net.sncp.Sncp;
import org.redkale.net.sncp.SncpClient;
import org.redkale.net.sncp.SncpRpcGroups;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class MessagedInstanceTest {

    private static Application application;

    private static MessageModuleEngine engine;

    private static ResourceFactory resourceFactory;

    private static RedkaleClassLoader classLoader;

    public static void main(String[] args) throws Throwable {
        LoggingBaseHandler.initDebugLogConfig();
        MessagedInstanceTest test = new MessagedInstanceTest();
        init();
        test.run1();
        test.run2();
    }

    @BeforeAll
    public static void init() throws Exception {
        application = Application.create(true);
        resourceFactory = application.getResourceFactory();
        engine = new MessageModuleEngine(application);
        classLoader = RedkaleClassLoader.getRedkaleClassLoader();

        MessageAgent agent = createMessageAgent(application, "mymq");
        MessageAgent[] messageAgents = new MessageAgent[] {agent};
        Field field = MessageModuleEngine.class.getDeclaredField("messageAgents");
        field.setAccessible(true);
        field.set(engine, messageAgents);
    }

    @Test
    public void run1() throws Exception {
        Class<TestMessageService> serviceClass = TestMessageService.class;
        MessageAsmMethodBoost boost = new MessageAsmMethodBoost(false, serviceClass, engine);
        SncpRpcGroups grous = new SncpRpcGroups();
        AsyncGroup iGroup = AsyncGroup.create("", Utility.newScheduledExecutor(1), 0, 0);
        SncpClient client = new SncpClient(
                "", iGroup, "0", new InetSocketAddress("127.0.0.1", 8080), new ClientAddress(), "TCP", 1, 16);
        TestMessageService instance = Sncp.createLocalService(
                classLoader, "", serviceClass, boost, resourceFactory, grous, client, null, null, null);
        resourceFactory.inject(instance);
    }

    @Test
    public void run2() throws Exception {
        TestMessageFacade facade = new TestMessageFacade();
        engine.onServicePostInit(null, facade);
    }

    public static MessageAgent createMessageAgent(Application application, String name) throws Exception {
        MessageAgent agent = new MessageAgent() {
            @Override
            protected void startMessageConsumer() {
                //
            }

            @Override
            protected void stopMessageConsumer() {
                //
            }

            @Override
            protected void startMessageProducer() {
                //
            }

            @Override
            protected void stopMessageProducer() {
                //
            }

            @Override
            protected void startMessageClientConsumer() {
                //
            }

            @Override
            protected void stopMessageClientConsumer() {
                //
            }

            @Override
            protected MessageClientProducer startMessageClientProducer() {
                return null;
            }

            @Override
            public void onResourceChange(ResourceEvent[] events) {}

            @Override
            public CompletableFuture<Void> createTopic(String... topics) {
                return null;
            }

            @Override
            public CompletableFuture<Void> deleteTopic(String... topics) {
                return null;
            }

            @Override
            public CompletableFuture<List<String>> queryTopic() {
                return null;
            }

            @Override
            public boolean acceptsConf(AnyValue config) {
                return true;
            }
        };
        Field field = MessageAgent.class.getDeclaredField("application");
        field.setAccessible(true);
        field.set(agent, application);
        field = MessageAgent.class.getDeclaredField("environment");
        field.setAccessible(true);
        field.set(agent, application.getEnvironment());
        agent.init(AnyValue.create().addValue("name", name));
        return agent;
    }
}
