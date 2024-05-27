/*
 *
 */
package org.redkale.source;

import java.util.concurrent.CompletableFuture;

/**
 * DataSource的事务类 <br>
 * 示例: <br>
 *
 * <blockquote>
 *
 * <pre>
 * DataSource source = ...;
 * DataTransaction tran = source.createTransaction();
 * try {
 *    tran.source().insert(record1); //必须使用tran.source()，不能使用source
 *    tran.source().update(record2); //必须使用tran.source()，不能使用source
 *    tran.commit(); //事务提交
 * } catch(Exception e){
 *    tran.rollback(); //回滚
 * }
 * </pre>
 *
 * </blockquote>
 *
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public interface DataTransaction {

	// 事务版的DataSource
	DataSource source();

	// 同步模式提交
	public void commit();

	// 同步模式回滚
	public void rollback();

	// 异步模式提交
	public CompletableFuture<Void> commitAsync();

	// 异步模式回滚
	public CompletableFuture<Void> rollbackAsync();
}
