package org.jresearch.hamcrest.beanmatcher.test;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TestBean05 extends TestBean04 {

	String stringValue05;

}
