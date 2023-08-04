package org.jresearch.hamcrest.beanmatcher.test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jresearch.hamcrest.beanmatcher.test.TestBean01Matcher.*;
import static org.jresearch.hamcrest.beanmatcher.test.TestBean02Matcher.*;
import static org.jresearch.hamcrest.beanmatcher.test.TestBean06Matcher.*;

import java.util.List;
import java.util.stream.Stream;

import ignore.me.IgnoreBean;
import org.hamcrest.Matcher;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TestMatchersTest {

	@ParameterizedTest
	@MethodSource
	@SuppressWarnings("static-method")
	void testTestBean01(Matcher<TestBean01> matcher) {
		TestBean01 bean = TestBean01.builder()
				.stringValue("value01")
				.build();
		assertThat(bean, matcher);
	}

	private static Stream<Arguments> testTestBean01() {
		return Stream.of(
				Arguments.of(testBean01Matcher().withStringValue("value01")),
				Arguments.of(testBean01Matcher().withStringValue(StringContains.containsStringIgnoringCase("lUe"))));
	}

	@ParameterizedTest
	@MethodSource
	@SuppressWarnings("static-method")
	void testTestBean02(Matcher<TestBean02> matcher) {
		TestBean02 bean = createTestBean02();
		assertThat(bean, matcher);
	}

	private static Stream<Arguments> testTestBean02() {
		return Stream.of(
				Arguments.of(testBean02Matcher().withStringValue("value01").withListValue(contains("a", "b", "c"))),
				Arguments.of(testBean02Matcher().withStringValue2(is(emptyOrNullString()))),
				Arguments.of(testBean02Matcher().withListValue2(contains(Short.valueOf((short) 2), 3))));
	}

	private static TestBean02 createTestBean02() {
		return TestBean02.builder()
				.stringValue("value01")
				.listValue(List.of("a", "b", "c"))
				.listValue2(List.of(Short.valueOf((short) 2), 3))
				.anotherBean(createTestBean03())
				.build();
	}

	private static TestBean03 createTestBean03() {
		return TestBean03.builder()
				.stringValue("value02")
				.listValue(List.of("z", "x", "c"))
				.listValue2(List.of(Short.valueOf((short) 2), 3))
				.build();
	}

	@ParameterizedTest
	@MethodSource
	@SuppressWarnings("static-method")
	void testTestBean06(Matcher<TestBean06> matcher) {
		TestBean06 bean = createTestBean06();
		assertThat(bean, matcher);
	}

	private static Stream<Arguments> testTestBean06() {
		return Stream.of(
				Arguments.of(testBean06Matcher().withStringValue(Holder.of("value01"))));
	}

	private static TestBean06 createTestBean06() {
		return TestBean06.builder()
				.stringValue(Holder.of("value01"))
				.ignoreValue(IgnoreBean.of("ignoreValue"))
				.build();
	}
}
