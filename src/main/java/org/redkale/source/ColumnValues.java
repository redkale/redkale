/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.redkale.util.LambdaFunction;
import org.redkale.util.LambdaSupplier;

/**
 * ColumnValue的集合类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class ColumnValues {

	private final List<ColumnValue> list = new ArrayList<>();

	public static ColumnValues create() {
		return new ColumnValues();
	}

	/**
	 * 返回 {column} = {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues set(String column, Serializable value) {
		list.add(ColumnValue.set(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} + {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues inc(String column, Number value) {
		list.add(ColumnValue.set(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} + 1 操作
	 *
	 * @param column 字段名
	 * @return ColumnValues
	 */
	public ColumnValues inc(String column) {
		list.add(ColumnValue.inc(column));
		return this;
	}

	/**
	 * 返回 {column} = {column} - {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues dec(String column, Number value) {
		list.add(ColumnValue.dec(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} - 1 操作
	 *
	 * @param column 字段名
	 * @return ColumnValues
	 */
	public ColumnValues dec(String column) {
		list.add(ColumnValue.dec(column));
		return this;
	}

	/**
	 * 返回 {column} = {column} * {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues mul(String column, Number value) {
		list.add(ColumnValue.mul(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} / {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues div(String column, Number value) {
		list.add(ColumnValue.div(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} % {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues mod(String column, Serializable value) {
		list.add(ColumnValue.mod(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} &#38; {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues and(String column, Serializable value) {
		list.add(ColumnValue.and(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} | {value} 操作
	 *
	 * @param column 字段名
	 * @param value 字段值
	 * @return ColumnValues
	 */
	public ColumnValues orr(String column, Serializable value) {
		list.add(ColumnValue.orr(column, value));
		return this;
	}

	/**
	 * 返回 {column} = {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 * @since 2.8.0
	 */
	public <T extends Serializable> ColumnValues set(LambdaSupplier<T> func) {
		list.add(ColumnValue.set(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} + {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues inc(LambdaSupplier<T> func) {
		list.add(ColumnValue.inc(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} - {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues dec(LambdaSupplier<T> func) {
		list.add(ColumnValue.dec(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} * {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues mul(LambdaSupplier<T> func) {
		list.add(ColumnValue.mul(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} / {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues div(LambdaSupplier<T> func) {
		list.add(ColumnValue.div(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} % {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 * @since 2.8.0
	 */
	public <T extends Serializable> ColumnValues mod(LambdaSupplier<T> func) {
		list.add(ColumnValue.mod(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} &#38; {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues and(LambdaSupplier<T> func) {
		list.add(ColumnValue.and(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} | {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T extends Serializable> ColumnValues orr(LambdaSupplier<T> func) {
		list.add(ColumnValue.orr(func));
		return this;
	}

	/**
	 * 返回 {column} = {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues set(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.set(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} + {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues inc(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.inc(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} + 1 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues inc(LambdaFunction<T, ?> func) {
		list.add(ColumnValue.inc(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} - {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues dec(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.dec(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} - 1 操作
	 *
	 * @param func 字段名Lambda
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues dec(LambdaFunction<T, ?> func) {
		list.add(ColumnValue.dec(func));
		return this;
	}

	/**
	 * 返回 {column} = {column} * {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 * @since 2.8.0
	 */
	public <T> ColumnValues mul(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.mul(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} / {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues div(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.div(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} % {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues mod(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.mod(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} &#38; {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues and(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.and(func, value));
		return this;
	}

	/**
	 * 返回 {column} = {column} | {value} 操作
	 *
	 * @param func 字段名Lambda
	 * @param value 字段值
	 * @param <T> 值的泛型
	 * @return ColumnValues
	 */
	public <T> ColumnValues orr(LambdaFunction<T, ?> func, Serializable value) {
		list.add(ColumnValue.orr(func, value));
		return this;
	}

	/**
	 * 获取ColumnValue数组
	 *
	 * @return ColumnValue[]
	 */
	public ColumnValue[] getValues() {
		return list.toArray(new ColumnValue[list.size()]);
	}

	@Override
	public String toString() {
		return String.valueOf(list);
	}
}
