package org.jresearch.hamcrest.beanmatcher.apt;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementScanner14;
import javax.tools.Diagnostic.Kind;

public class BeanMatcherElementScanner extends ElementScanner14<Void, BeanMatcherBuilder> {

    private TypeElement element;
    private BeanMatcherProcessor processor;

    public static BeanMatcherElementScanner create(TypeElement element, BeanMatcherProcessor processor) {
        return new BeanMatcherElementScanner(element, processor);
    }

    private BeanMatcherElementScanner(TypeElement element, BeanMatcherProcessor processor) {
        this.element = element;
        this.processor = processor;
    }

    @Override
    public Void visitType(TypeElement e, BeanMatcherBuilder b) {
        if (e != element) {
            processor.getMessager().printMessage(Kind.NOTE, String.format("Skip inner type %s", e));
            return DEFAULT_VALUE;
        }
        return super.visitType(e, b);
    }

    @Override
    public Void visitExecutable(ExecutableElement e, BeanMatcherBuilder b) {
        processor.getProperty(e).ifPresent(p -> processor.processProperty(p, b));
        return super.visitExecutable(e, b);
    }

    @Override
    public Void visitRecordComponent(RecordComponentElement e, BeanMatcherBuilder b) {
        processor.getProperty(e).ifPresent(p -> processor.processProperty(p, b));
        return super.visitRecordComponent(e, b);
    }

}
