package org.qommons.tree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class DefaultTree<V> implements Tree<V, DefaultTree<V>, List<DefaultTree<V>>> {
	private V theValue;
	private List<DefaultTree<V>> theChildren;

	public DefaultTree() {
		theChildren = new ArrayList<>();
	}

	public DefaultTree(V value) {
		this();
		theValue = value;
	}

	@Override
	public V getValue() {
		return theValue;
	}

	public DefaultTree<V> setValue(V value) {
		theValue = value;
		return this;
	}

	@Override
	public List<DefaultTree<V>> getChildren() {
		return theChildren;
	}

	public DefaultTree<V> replaceAll(Function<? super V, ? extends V> replacer) {
		setValue(replacer.apply(getValue()));
		for (DefaultTree<V> child : getChildren())
			child.replaceAll(replacer);
		return this;
	}

	@Override
	public String toString() {
		return String.valueOf(theValue);
	}

	public static <V> DefaultTree<V> treeOf(V root, Function<V, Iterable<V>> childFn) {
		DefaultTree<V> ret = new DefaultTree<>(root);
		for (V child : childFn.apply(root)) {
			ret.getChildren().add(treeOf(child, childFn));
		}
		return ret;
	}

	public static <V> DefaultTree<V> treeOfArray(V root, Function<V, V[]> childFn) {
		return treeOf(root, childFn.andThen(array -> Arrays.asList(array)));
	}
}
