package org.jresearch.hamcrest.beanmatcher.apt;

import java.util.List;
import java.util.function.Consumer;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

public class GeneratorVisitor extends SimpleAnnotationValueVisitor8<Void, Consumer<TypeMirror>> {

	@Override
	public Void visitType(TypeMirror beanClass, Consumer<TypeMirror> generator) {
		generator.accept(beanClass);
		return null;
	}

	@Override
	public Void visitArray(List<? extends AnnotationValue> values, Consumer<TypeMirror> generator) {
		values.forEach(v -> v.accept(this, generator));
		return null;
	}

}
