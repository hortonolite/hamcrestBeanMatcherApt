package org.jresearch.hamcrest.beanmatcher.test;

public class Holder<T extends CharSequence> {

	private T value;

	public T getValue() {
		return value;
	}

	public void setValue(T value) {
		this.value = value;
	}

}
