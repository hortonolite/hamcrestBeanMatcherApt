package org.jresearch.hamcrest.beanmatcher.test;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestBean02 {

	String stringValue;
	String stringValue2;

	List<String> listValue;

}
