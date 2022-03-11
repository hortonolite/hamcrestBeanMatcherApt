package org.jresearch.hamcrest.beanmatcher.matcher;

import static org.hamcrest.beans.HasPropertyWithValue.*;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import one.util.streamex.StreamEx;

public class AbstractBeanMatcher<T> extends BaseMatcher<T> {

	private final List<Matcher<? super T>> propertyMatchers = new ArrayList<>();

	private List<Matcher<? super T>> failedMatchers = new ArrayList<>();

	protected void addPropertyMatcher(String propertyName, final Matcher<?> propertyMatcher) {
		addBeanMatcher(hasProperty(propertyName, propertyMatcher));
	}

	protected void addBeanMatcher(final Matcher<? super T> matcher) {
		propertyMatchers.add(matcher);
	}

	@SuppressWarnings("resource")
	@Override
	public boolean matches(final Object item) {
		failedMatchers = StreamEx.of(propertyMatchers).remove(m -> m.matches(item)).toList();
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
