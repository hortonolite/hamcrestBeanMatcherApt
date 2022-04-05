package org.jresearch.hamcrest.beanmatcher.test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jresearch.hamcrest.beanmatcher.test.TestBean02Matcher.*;

import java.util.List;
import java.util.stream.Stream;

import org.hamcrest.Matcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestBean01Test {

	@ParameterizedTest
	@MethodSource()
	void testTestBean02(Matcher<TestBean02> matcher) {
		TestBean02 bean = TestBean02.builder()
				.stringValue("value01")
				.listValue(List.of("a", "b", "c"))
				.listValue2(List.of(Short.valueOf((short) 2), 3))
				.build();
		assertThat(bean, matcher);
	}

	private static Stream<Arguments> testTestBean02() {
		return Stream.of(
				Arguments.of(testBean02Matcher().withStringValue("value01").withListValue(contains("a", "b", "c"))),
				Arguments.of(testBean02Matcher().withStringValue2(is(emptyOrNullString()))),
				Arguments.of(testBean02Matcher().withListValue2(contains(Short.valueOf((short) 2), 3))));
		// Arguments.of(testBean02Matcher().withListValue(hasItems("a", "d"))));
	}

}
