/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;
import org.redkale.convert.ConvertColumn;

/**
 * 页集合。 结构由一个total总数和一个List列表组合而成。
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @param <T> 集合元素的数据类型
 */
@SuppressWarnings("unchecked")
public class Sheet<T> implements java.io.Serializable, Iterable<T> {

	@ConvertColumn(index = 1)
	private long total = -1;

	@ConvertColumn(index = 2)
	private Collection<T> rows;

	public Sheet() {
		super();
	}

	public Sheet(int total, Collection<? extends T> data) {
		this((long) total, data);
	}

	public Sheet(long total, Collection<? extends T> data) {
		this.total = total;
		this.rows = (Collection<T>) data;
	}

	public static <E> Sheet<E> asSheet(Collection<E> data) {
		return data == null ? new Sheet() : new Sheet(data.size(), data);
	}

	public static <E> Sheet<E> empty() {
		return new Sheet<>();
	}

	public Sheet<T> copyTo(Sheet<T> copy) {
		if (copy == null) {
			return copy;
		}
		copy.total = this.total;
		Collection<T> data = this.getRows();
		if (data != null) {
			copy.setRows(new ArrayList(data));
		} else {
			copy.rows = null;
		}
		return copy;
	}

	/**
	 * 判断数据列表是否为空
	 *
	 * @return 是否为空
	 */
	@ConvertColumn(index = 3)
	public boolean isEmpty() {
		Collection<T> data = this.rows;
		return data == null || data.isEmpty();
	}

	@Override
	public String toString() {
		return "{\"total\":" + this.total + ", \"rows\":" + this.rows + "}";
	}

	public long getTotal() {
		return this.total;
	}

	public void setTotal(long total) {
		this.total = total;
	}

	public Collection<T> getRows() {
		return this.rows;
	}

	public List<T> list() {
		return list(false);
	}

	public List<T> list(boolean created) {
		Collection<T> data = this.rows;
		if (data == null) {
			return created ? new ArrayList() : null;
		}
		return (data instanceof List) ? (List<T>) data : new ArrayList(data);
	}

	public void setRows(Collection<? extends T> data) {
		this.rows = (Collection<T>) data;
	}

	@Override
	public Iterator<T> iterator() {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<T>().iterator() : data.iterator();
	}

	@Override
	public void forEach(final Consumer<? super T> consumer) {
		Collection<T> data = this.rows;
		if (consumer != null && data != null && !data.isEmpty()) {
			data.forEach(consumer);
		}
	}

	public <R> Sheet<R> map(Function<T, R> mapper) {
		Collection<T> data = this.rows;
		if (data == null || data.isEmpty()) {
			return (Sheet) this;
		}
		final List<R> list = new ArrayList<>();
		for (T item : data) {
			list.add(mapper.apply(item));
		}
		return new Sheet<>(getTotal(), list);
	}

	public void forEachParallel(final Consumer<? super T> consumer) {
		Collection<T> data = this.rows;
		if (consumer != null && data != null && !data.isEmpty()) {
			data.parallelStream().forEach(consumer);
		}
	}

	@Override
	public Spliterator<T> spliterator() {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<T>().spliterator() : data.spliterator();
	}

	public Stream<T> stream() {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<T>().stream() : data.stream();
	}

	public Stream<T> parallelStream() {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<T>().parallelStream() : data.parallelStream();
	}

	public Object[] toArray() {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<T>().toArray() : data.toArray();
	}

	public <E> E[] toArray(E[] a) {
		Collection<T> data = this.rows;
		return (data == null) ? new ArrayList<E>().toArray(a) : data.toArray(a);
	}

	public <E> E[] toArray(IntFunction<E[]> generator) {
		Collection<T> data = this.rows;
		return (data == null)
				? new ArrayList<E>().toArray(generator.apply(0))
				: data.toArray(generator.apply(data.size()));
	}
}
