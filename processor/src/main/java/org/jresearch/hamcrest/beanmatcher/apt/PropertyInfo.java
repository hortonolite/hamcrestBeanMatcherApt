package org.jresearch.hamcrest.beanmatcher.apt;

import java.util.List;

import javax.lang.model.type.TypeMirror;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PropertyInfo {
	List<TypeMirror> types;
	String name;
	PropertyKind kind;
}
