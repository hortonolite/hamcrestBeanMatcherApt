package org.jresearch.hamcrest.beanmatcher.test;

import static org.hamcrest.core.IsEqual.*;

import org.hamcrest.Matcher;
import org.jresearch.hamcrest.beanmatcher.matcher.AbstractBeanMatcher;

public class TestBean01Matcher extends AbstractBeanMatcher<TestBean01> {

	public static TestBean01Matcher create() {
		return new TestBean01Matcher();
	}

	public static TestBean01Matcher testBean01Matcher() {
		return create();
	}

	protected TestBean01Matcher add(String propertyName, final Matcher<?> matcher) {
		addPropertyMatcher(propertyName, matcher);
		return this;
	}

	public TestBean01Matcher withStringValue(Matcher<? super String> matcher) {
		return add("stringValue", matcher);
	}

	public TestBean01Matcher withStringValue(String testValue) {
		return withStringValue(equalTo(testValue));
	}

}
