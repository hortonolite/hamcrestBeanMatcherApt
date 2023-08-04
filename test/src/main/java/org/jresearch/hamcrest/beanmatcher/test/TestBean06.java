package org.jresearch.hamcrest.beanmatcher.test;

import ignore.me.IgnoreBean;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TestBean06 {

	Holder<String> stringValue;
	IgnoreBean ignoreValue;

}
