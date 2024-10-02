package org.jresearch.hamcrest.beanmatcher.matcher;

import one.util.streamex.StreamEx;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.beans.HasPropertyWithValue.*;

public class AbstractBeanMatcher<T> extends BaseMatcher<T> {

	private final Map<String, Matcher<? super T>> propertyMatchers = new HashMap<>();

	private List<Matcher<? super T>> failedMatchers = new ArrayList<>();

	protected void addPropertyMatcher(String propertyName, final Matcher<?> propertyMatcher) {
		addBeanMatcher(propertyName, hasProperty(propertyName, propertyMatcher));
	}

	protected void addBeanMatcher(String propertyName, final Matcher<? super T> matcher) {
		propertyMatchers.put(propertyName, matcher);
	}

	@Override
	public boolean matches(final Object item) {
		failedMatchers = StreamEx.of(propertyMatchers.values()).remove(m -> m.matches(item)).toList();
		return failedMatchers.isEmpty();
	}

	@Override
	public void describeTo(final Description description) {
		description.appendText("All matches passed");
	}

	@Override
	public void describeMismatch(final Object item, final Description description) {
		description.appendText("\n");
		failedMatchers.forEach(m -> describeMismatch(m, item, description));
	}

	private void describeMismatch(Matcher<? super T> matcher, Object item, Description description) {
		description
				.appendText("Expected: ")
				.appendDescriptionOf(matcher)
				.appendText(" but ");
		matcher.describeMismatch(item, description);
	}

}
