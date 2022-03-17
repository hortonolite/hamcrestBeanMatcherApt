package org.jresearch.hamcrest.beanmatcher.apt;

import com.squareup.javapoet.ClassName;

import lombok.Value;

@Value(staticConstructor = "of")
public class PropertyInfo {
	ClassName type;
	String name;
}
