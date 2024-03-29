package org.jresearch.hamcrest.beanmatcher.test;

import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestBean02 {

	String stringValue;
	String stringValue2;

	List<String> listValue;
	List<Number> listValue2;
	int listValueSize;

	int intValue;

	int[] intArrayValue;

	Map<String, Integer> mapValue;

	AccessLevel enumValue;

	TestBean03 anotherBean;
}
