package org.jresearch.hamcrest.beanmatcher.apt;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class MethodInfo {
	String name;
	List<String> parameterTypes;
}
