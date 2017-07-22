package org.redkale.source;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 事务管理器
 * @author wicks
 */
public class Transaction {
	
	private Connection connection = null;
	private ResultSet resultSet = null;
	private PreparedStatement statement = null;
	
	/**
	 * 开启事务
	 * @param conn
	 * @throws SQLException 
	 */
	public void beigin(Connection conn) throws SQLException{
		if(conn != null && connection == null){
			connection = conn;
			conn.setAutoCommit(false);
		}
	}
	
	/**
	 * 查询
	 * @param sql
	 * @return
	 * @throws SQLException
	 */
	public ResultSet query(String sql) throws SQLException{
		closeResources();
		statement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		return statement.executeQuery();
	}
	
	/**
	 * 执行
	 * @param sql
	 * @return
	 * @throws SQLException
	 */
	public int execute(String sql) throws SQLException{
		closeResources();
		statement = connection.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
		return statement.executeUpdate();
	}
	
	/**
	 * 提交事务
	 * @param conn
	 * @throws SQLException 
	 */
	public void commit() throws SQLException{
		if(connection != null){
			connection.commit();
			connection.setAutoCommit(true);
			closeResources();
			connection.close();
		}
	}
	
	/**
	 * 回滚事务
	 * @param conn
	 * @throws SQLException 
	 */
	public void rollback() throws SQLException{
		if(connection != null){
			connection.rollback();
			connection.setAutoCommit(true);
			closeResources();
			connection.close();
		}
	}
	
	/**
	 * 关闭共用资源
	 * @throws SQLException
	 */
	private void closeResources() throws SQLException{
		if(resultSet != null){
			resultSet.close();
		}
		if(statement != null){
			statement.close();
		}
	}

}
