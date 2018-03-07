/**
 * <p>
 * see: https://redkale.org
 *
 * @author zhangjx
 *
module org.redkale {

    requires java.base;
    requires java.logging;
    requires java.xml;
    requires java.sql;
    requires java.sql.rowset;

    requires jdk.unsupported;  //sun.misc.Unsafe

    exports javax.annotation;
    exports javax.persistence;

    exports org.redkale.boot;
    exports org.redkale.boot.watch;
    exports org.redkale.convert;
    exports org.redkale.convert.bson;
    exports org.redkale.convert.ext;
    exports org.redkale.convert.json;
    exports org.redkale.net;
    exports org.redkale.net.http;
    exports org.redkale.net.sncp;
    exports org.redkale.service;
    exports org.redkale.source;
    exports org.redkale.util;
    exports org.redkale.watch;
    
}
*/