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
    exports org.redkale.cluster;
    exports org.redkale.convert;
    exports org.redkale.convert.bson;
    exports org.redkale.convert.ext;
    exports org.redkale.convert.json;
    exports org.redkale.mq;
    exports org.redkale.net;
    exports org.redkale.net.client;
    exports org.redkale.net.http;
    exports org.redkale.net.sncp;
    exports org.redkale.persistence;
    exports org.redkale.service;
    exports org.redkale.source;
    exports org.redkale.util;
    exports org.redkale.watch;
    
    uses org.redkale.boot.PropertiesAgentProvider;
    uses org.redkale.cluster.ClusterAgentProvider;
    uses org.redkale.convert.ConvertProvider;
    uses org.redkale.mq.MessageAgentProvider;
    uses org.redkale.source.CacheSourceProvider;
    uses org.redkale.source.DataSourceProvider;
    uses org.redkale.util.ResourceAnnotationProvider;
    
}
