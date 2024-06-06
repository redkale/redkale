/*

*/

package org.redkale.source;

/**
 * DataSource的监控借口
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataSqlMonitor {
    public void visitCostTime(DataSqlSource source, long costMills, String... sqls);
}
