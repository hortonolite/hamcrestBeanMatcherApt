package org.jresearch.hamcrest.beanmatcher.apt;

import java.util.List;

import com.squareup.javapoet.TypeName;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PropertyInfo {
	List<TypeName> types;
	String name;
	PropertyKind kind;
}
