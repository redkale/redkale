/**
 * <p>
 * see: https://redkale.org
 *
 * @author zhangjx
 *
 */
module org.redkale {

    requires java.base;
    requires java.logging;    
    requires java.net.http;
    requires java.sql;
    requires jdk.unsupported;  //sun.misc.Unsafe

    exports org.redkale.annotation;
    exports org.redkale.asm;
    exports org.redkale.boot;
    exports org.redkale.boot.watch;
    exports org.redkale.cache;
    exports org.redkale.cache.spi;
    exports org.redkale.cluster;
    exports org.redkale.cluster.spi;
    exports org.redkale.convert;
    exports org.redkale.convert.bson;
    exports org.redkale.convert.ext;
    exports org.redkale.convert.json;
    exports org.redkale.convert.proto;
    exports org.redkale.convert.spi;
    exports org.redkale.inject;
    exports org.redkale.inject.spi;
    exports org.redkale.lock;
    exports org.redkale.lock.spi;
    exports org.redkale.mq;
    exports org.redkale.mq.spi;
    exports org.redkale.net;
    exports org.redkale.net.client;
    exports org.redkale.net.http;
    exports org.redkale.net.sncp;
    exports org.redkale.persistence;
    exports org.redkale.props.spi;
    exports org.redkale.schedule;
    exports org.redkale.schedule.spi;
    exports org.redkale.service;
    exports org.redkale.source;
    exports org.redkale.source.spi;
    exports org.redkale.util;
    exports org.redkale.watch;
    
    uses org.redkale.props.spi.PropertiesAgentProvider;
    uses org.redkale.cache.spi.CacheManagerProvider;
    uses org.redkale.cluster.spi.ClusterAgentProvider;
    uses org.redkale.convert.spi.ConvertProvider;
    uses org.redkale.inject.spi.ResourceAnnotationProvider;
    uses org.redkale.mq.spi.MessageAgentProvider;
    uses org.redkale.schedule.spi.ScheduleManagerProvider;
    uses org.redkale.source.spi.CacheSourceProvider;
    uses org.redkale.source.spi.DataSourceProvider;
    uses org.redkale.source.spi.DataNativeSqlParserProvider;
    
}
