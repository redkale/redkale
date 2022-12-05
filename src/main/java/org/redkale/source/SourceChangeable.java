/*
 *
 */
package org.redkale.source;

import org.redkale.util.*;

/**
 * 资源变更回调接口
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public interface SourceChangeable {

    public void onChange(AnyValue newConf, ResourceEvent[] events);

}
