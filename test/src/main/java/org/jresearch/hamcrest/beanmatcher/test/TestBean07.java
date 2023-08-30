package org.jresearch.hamcrest.beanmatcher.test;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestBean07 {

	String beanValue;
	Inner innerValue;

	@Value
	public static class Inner {
		String beanInnerValue;
	}

}
