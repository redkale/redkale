/**
 * see: https://redkale.org
 *
 * @author zhangjx
 */
module org.redkale {
    requires java.base;
    requires java.logging;
    requires java.net.http;
    requires java.sql;
    requires jdk.unsupported; // sun.misc.Unsafe

    exports org.redkale.annotation;
    exports org.redkale.boot;
    exports org.redkale.boot.watch;
    exports org.redkale.cached;
    exports org.redkale.cached.spi;
    exports org.redkale.cluster;
    exports org.redkale.cluster.spi;
    exports org.redkale.convert;
    exports org.redkale.convert.ext;
    exports org.redkale.convert.json;
    exports org.redkale.convert.pb;
    exports org.redkale.convert.spi;
    exports org.redkale.inject;
    exports org.redkale.locked;
    exports org.redkale.locked.spi;
    exports org.redkale.mq;
    exports org.redkale.mq.spi;
    exports org.redkale.net;
    exports org.redkale.net.client;
    exports org.redkale.net.http;
    exports org.redkale.net.sncp;
    exports org.redkale.persistence;
    exports org.redkale.props.spi;
    exports org.redkale.scheduled;
    exports org.redkale.scheduled.spi;
    exports org.redkale.service;
    exports org.redkale.source;
    exports org.redkale.source.spi;
    exports org.redkale.util;
    exports org.redkale.watch;

    uses org.redkale.util.YamlProvider;
    uses org.redkale.props.spi.PropertiesAgentProvider;
    uses org.redkale.cached.spi.CachedManagerProvider;
    uses org.redkale.cluster.spi.ClusterAgentProvider;
    uses org.redkale.convert.spi.ConvertProvider;
    uses org.redkale.mq.spi.MessageAgentProvider;
    uses org.redkale.scheduled.spi.ScheduledManagerProvider;
    uses org.redkale.source.spi.CacheSourceProvider;
    uses org.redkale.source.spi.DataSourceProvider;
    uses org.redkale.source.spi.DataNativeSqlParserProvider;
}
