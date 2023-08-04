package org.jresearch.hamcrest.beanmatcher.apt;

import java.util.List;
import java.util.function.Consumer;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

public class IgnorePackageVisitor extends SimpleAnnotationValueVisitor8<Void, Consumer<String>> {

	@Override
	public Void visitString(String packageToIgnore, Consumer<String> packageProcessor) {
		packageProcessor.accept(packageToIgnore);
		return null;
	}

	@Override
	public Void visitArray(List<? extends AnnotationValue> values, Consumer<String> generator) {
		values.forEach(v -> v.accept(this, generator));
		return null;
	}

}
