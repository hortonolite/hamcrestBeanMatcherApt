package org.jresearch.hamcrest.beanmatcher.apt;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MethodInfoTest {

	@Test
	void test() {
		MethodInfo mi1 = MethodInfo.of("mi", ImmutableList.of("p1", "p2"));
		MethodInfo mi2 = MethodInfo.of("mi", ImmutableList.of("p1", "p2"));
		assertEquals(mi1, mi2);
	}

}
