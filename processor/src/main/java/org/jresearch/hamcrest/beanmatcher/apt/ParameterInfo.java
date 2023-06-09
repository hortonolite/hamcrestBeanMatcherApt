package org.jresearch.hamcrest.beanmatcher.apt;

import com.squareup.javapoet.ParameterSpec;
import lombok.Value;

@Value(staticConstructor = "of")
public class ParameterInfo {
	String type;
	ParameterSpec parameterSpec;
}
