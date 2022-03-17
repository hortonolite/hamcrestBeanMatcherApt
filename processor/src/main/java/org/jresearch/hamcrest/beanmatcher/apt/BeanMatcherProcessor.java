package org.jresearch.hamcrest.beanmatcher.apt;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.AnnotatedConstruct;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.jresearch.hamcrest.beanmatcher.annotation.BeanMatcher;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.JavaFile.Builder;
import com.squareup.javapoet.TypeSpec;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@AutoService(Processor.class)
public class BeanMatcherProcessor extends AbstractProcessor {

	private final AtomicInteger round = new AtomicInteger();

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return ImmutableSet.of(BeanMatcher.class.getName());
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@SuppressWarnings({ "resource", "boxing" })
	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Call the round %d for %s", round.incrementAndGet(), StreamEx
				.of(roundEnv.getRootElements()).map(Element::getSimpleName).joining(", ")));

		StreamEx.of(roundEnv.getElementsAnnotatedWith(BeanMatcher.class))
				.filterBy(Element::getKind, ElementKind.PACKAGE)
				.map(PackageElement.class::cast)
				.forEach(this::generate);
		return true;
	}

	private void generate(final PackageElement annotatedPackage) {
		Name packageName = annotatedPackage.getQualifiedName();
		GeneratorVisitor generatorVisitor = new GeneratorVisitor();
		getAnnotationMirror(annotatedPackage, BeanMatcher.class)
				.flatMap(BeanMatcherProcessor::getAnnotationDefaultAttributeValue)
				.ifPresent(v -> v.accept(generatorVisitor, m -> generateMatcher(m, packageName)));

	}

	@SuppressWarnings("resource")
	private static Optional<AnnotationMirror> getAnnotationMirror(AnnotatedConstruct element, Class<?> annotation) {
		String annotationName = annotation.getName();
		return StreamEx.of(element.getAnnotationMirrors())
				.map(AnnotationMirror.class::cast)
				.findAny(m -> annotationName.equals(m.getAnnotationType().toString()));
	}

	private static Optional<AnnotationValue> getAnnotationDefaultAttributeValue(AnnotationMirror annotationMirror) {
		return getAnnotationAttributeValue(annotationMirror, "value");
	}

	@SuppressWarnings("resource")
	private static Optional<AnnotationValue> getAnnotationAttributeValue(AnnotationMirror annotationMirror, String attributeName) {
		return EntryStream.of(annotationMirror.getElementValues())
				.mapKeys(ExecutableElement::getSimpleName)
				.mapKeys(Name::toString)
				.filterKeys(attributeName::equals)
				.values()
				.map(AnnotationValue.class::cast)
				.findAny();
	}

	private void generateMatcher(final TypeMirror beanClass, final Name packageName) {
		processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Generate beam matcher for %s", beanClass));

		Types typeUtils = processingEnv.getTypeUtils();
		TypeElement element = (TypeElement) typeUtils.asElement(beanClass);

		final BeanMatcherBuilder builder = BeanMatcherBuilder.create(processingEnv.getMessager(), packageName, element);
		element.accept(new ElementScanner8<Void, BeanMatcherBuilder>() {
			@Override
			public Void visitExecutable(ExecutableElement e, BeanMatcherBuilder b) {
				getProperty(e).ifPresent(b::add);
				return super.visitExecutable(e, b);
			}
		}, builder);

		writeJavaFile(packageName, builder.build(), builder.getStaticImports());
	}

	protected Optional<PropertyInfo> getProperty(ExecutableElement method) {
		processingEnv.getMessager().printMessage(Kind.NOTE, String.format("Process metthod %s", method));
		return Optional.of(method)
				.filter(e -> !TypeKind.VOID.equals(e.getReturnType().getKind()))
				.filter(e -> e.getParameters().isEmpty())
				.filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
				.map(ExecutableElement::getSimpleName)
				.map(Name::toString)
				.map(BeanMatcherProcessor::getPropertyName)
				.map(name -> PropertyInfo.of(getPropertyType(method), name));
	}

	private ClassName getPropertyType(ExecutableElement method) {
		Types typeUtils = processingEnv.getTypeUtils();
		TypeElement element = (TypeElement) typeUtils.asElement(method.getReturnType());
		return ClassName.get(element);
	}

	protected static String getPropertyName(String methodName) {
		return getPropertyName(methodName, "get").orElseGet(() -> getPropertyName(methodName, "is").orElse(null));
	}

	protected static Optional<String> getPropertyName(String methodName, String prefix) {
		boolean getName = methodName.length() > prefix.length() && methodName.startsWith(prefix);
		if (getName) {
			return Optional.of(methodName.substring(prefix.length()));
		}
		return Optional.empty();
	}

	private void writeJavaFile(final Name packageName, TypeSpec spec, List<ClassName> staticImports) {

		final Builder javaFileBuilder = JavaFile.builder(packageName.toString(), spec).indent("\t");
		staticImports.forEach(i -> javaFileBuilder.addStaticImport(i, "*"));
		JavaFile javaFile = javaFileBuilder.build();

		try {
			final JavaFileObject jfo = processingEnv.getFiler().createSourceFile(packageName.toString() + "." + spec.name);
			try (Writer wr = jfo.openWriter()) {
				javaFile.writeTo(wr);
			}
		} catch (final IOException e) {
			processingEnv.getMessager().printMessage(Kind.ERROR, String.format("Can't write class %s to package %s: %s", spec, packageName, e.getMessage()));
		}
	}

}
