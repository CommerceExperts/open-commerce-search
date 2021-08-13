package de.cxp.ocs.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * A set that only returns the min and the max value (based on natural ordering)
 * of all added values.
 * 
 * If only one value is added, it is the min and the max value, however if
 * returned as iterator or array, the set will only contain that one value.
 */
public class MinMaxSet<E> implements Set<E> {

	private final TreeSet<E> values = new TreeSet<>();

	public static MinMaxSet<?> of(Object value) {
		if (value.getClass().isArray()) {
			return new MinMaxSet<Object>((Object[]) value);
		}
		else {
			return new MinMaxSet<Object>(value);
		}
	}

	public MinMaxSet(E val) {
		add(val);
	}

	public MinMaxSet(E[] values) {
		for (E val : values) {
			add(val);
		}
	}

	@Override
	public int size() {
		return values.size();
	}

	@Override
	public boolean isEmpty() {
		return values.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return values.contains(o);
	}

	public E min() {
		return values.first();
	}

	public E max() {
		return values.last();
	}

	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {

			E next = min() != null ? min() : max();

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public E next() {
				E retVal = next;
				if (next != null) {
					if (next.equals(max())) next = null;
					else if (next.equals(min())) next = max();
				}
				return retVal;
			}
		};
	}

	@Override
	public Object[] toArray() {
		boolean hasMax = max() != null && !max().equals(min());
		Object[] arr = new Object[hasMax ? 2 : min() != null ? 1 : 0];
		if (min() != null) {
			arr[0] = min();
			if (hasMax) arr[1] = max();
		}
		else {
			arr[0] = max();
		}
		return arr;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T[] toArray(T[] a) {
		boolean hasMax = max() != null && !max().equals(min());
		if (a.length >= 1) {
			if (min() != null) {
				a[0] = (T) min();
			}
			else if (hasMax) {
				a[0] = (T) max();
			}
		}
		if (a.length >= 2 && hasMax) {
			a[1] = (T) max();
		}
		return a;
	}

	@Override
	public boolean add(E e) {
		return values.add(e);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return c.stream().filter(this::contains).count() == c.size();
	}

	@Override
	public boolean addAll(Collection<? extends E> c) {
		c.forEach(this::add);
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return values.retainAll(c);
	}

	@Override
	public boolean remove(Object o) {
		throw new RuntimeException("operation not supported, yet");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		throw new RuntimeException("operation not supported, yet");
	}

	@Override
	public void clear() {
		values.clear();
	}

}
