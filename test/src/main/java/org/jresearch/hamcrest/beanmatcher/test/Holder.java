package org.jresearch.hamcrest.beanmatcher.test;

import lombok.Value;

@Value(staticConstructor = "of")
public class Holder<T extends CharSequence> {

	private T value;

}
