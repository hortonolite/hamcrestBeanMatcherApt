package org.jresearch.hamcrest.beanmatcher.apt;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner8;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import org.jresearch.hamcrest.beanmatcher.annotation.BeanMatcher;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.JavaFile.Builder;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;

@AutoService(Processor.class)
public class BeanMatcherProcessor extends AbstractProcessor {

	private final AtomicInteger round = new AtomicInteger();
	private Types types;
	private Messager messager;

	// Special types
	private DeclaredType mapType;
	private DeclaredType iterableType;

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return ImmutableSet.of(BeanMatcher.class.getName());
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		types = env.getTypeUtils();
		Elements elements = env.getElementUtils();
		messager = env.getMessager();

		// Special types
		mapType = types.getDeclaredType(elements.getTypeElement(Map.class.getCanonicalName()));
		iterableType = types.getDeclaredType(elements.getTypeElement(Iterable.class.getCanonicalName()));
		super.init(env);
	}

	@SuppressWarnings({ "resource", "boxing" })
	@Override
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		messager.printMessage(Kind.NOTE, String.format("Call the round %d for %s", round.incrementAndGet(), StreamEx
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
		messager.printMessage(Kind.NOTE, String.format("Generate beam matcher for %s", beanClass));

		TypeElement element = (TypeElement) types.asElement(beanClass);

		final BeanMatcherBuilder builder = BeanMatcherBuilder.create(messager, packageName, element);
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
		messager.printMessage(Kind.NOTE, String.format("Process metthod %s", method));
		PropertyKind propertyKind = getPropertyKind(method);
		return Optional.of(method)
			.filter(e -> !TypeKind.VOID.equals(e.getReturnType().getKind()))
			.filter(e -> e.getParameters().isEmpty())
			.filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
			.map(ExecutableElement::getSimpleName)
			.map(Name::toString)
			.map(BeanMatcherProcessor::getPropertyName)
			.map(name -> PropertyInfo.builder().name(name))
			.map(builder -> builder.kind(propertyKind))
			.map(builder -> builder.types(getPropertyTypes(propertyKind, method)))
			.map(PropertyInfo.PropertyInfoBuilder::build);
	}

	private PropertyKind getPropertyKind(ExecutableElement method) {
		TypeMirror returnType = method.getReturnType();
		return returnType.accept(new SimpleTypeVisitor8<PropertyKind, Void>() {
			@Override
			public PropertyKind visitArray(ArrayType t, Void p) {
				return PropertyKind.ARRAY;
			}

			@Override
			public PropertyKind visitDeclared(DeclaredType type, Void p) {
				return Optional.of(type)
					.flatMap(BeanMatcherProcessor.this::checkSpecificKinds)
					.orElse(PropertyKind.SCALAR);
			}

			@Override
			public PropertyKind visitPrimitive(PrimitiveType t, Void p) {
				return PropertyKind.SCALAR;
			}
		}, null);
	}

	@SuppressWarnings("resource")
	private Optional<PropertyKind> checkSpecificKinds(TypeMirror type) {
		if (types.isSameType(types.erasure(type), mapType)) {
			return Optional.of(PropertyKind.MAP);
		}
		if (types.isSameType(types.erasure(type), iterableType)) {
			return Optional.of(PropertyKind.ITERABLE);
		}
		return StreamEx.of(types.directSupertypes(type))
			.map(this::checkSpecificKinds)
			.findAny(Optional::isPresent)
			.orElseGet(Optional::empty);
	}

	@SuppressWarnings("resource")
	private static List<TypeName> getPropertyTypes(PropertyKind propertyKind, ExecutableElement method) {
		return method.getReturnType().accept(new SimpleTypeVisitor8<List<TypeName>, Void>() {
			@Override
			public List<TypeName> visitArray(ArrayType type, Void p) {
				return ImmutableList.of(TypeName.get(type.getComponentType()));
			}

			@Override
			public List<TypeName> visitDeclared(DeclaredType type, Void p) {
				if (propertyKind == PropertyKind.SCALAR) {
					return ImmutableList.of(TypeName.get(type));
				}
				return StreamEx.of(type.getTypeArguments()).map(TypeName::get).toList();
			}

			@Override
			public List<TypeName> visitPrimitive(PrimitiveType type, Void p) {
				return ImmutableList.of(TypeName.get(type));
			}
		}, null);
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
