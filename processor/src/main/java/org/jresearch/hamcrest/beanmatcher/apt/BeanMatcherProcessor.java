package org.jresearch.hamcrest.beanmatcher.apt;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.JavaFile.Builder;
import com.squareup.javapoet.TypeSpec;
import lombok.Getter;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jresearch.hamcrest.beanmatcher.annotation.BeanMatcher;

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
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@AutoService(Processor.class)
public class BeanMatcherProcessor extends AbstractProcessor {

    private final AtomicInteger round = new AtomicInteger();
    private Types types;
    @Getter
    private Messager messager;
    private final Set<Element> processed = new HashSet<>();
    private final Queue<TypeMirror> process = new LinkedList<>();

    // Special types
    private DeclaredType mapType;
    private DeclaredType iterableType;
    private DeclaredType collectionType;
    // Types to ignore while generate Bean Matchers
    private Set<DeclaredType> ignoreClasses;
    private Set<String> ignorePackagePrfixes = ImmutableSet.of("com.sun", "java", "javax", "jdk");

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
        collectionType = types.getDeclaredType(elements.getTypeElement(Collection.class.getCanonicalName()));

        // Initialize ignore if any
        ignoreClasses = Set.of();

        super.init(env);
    }

    @SuppressWarnings({ "boxing" })
    @Override
    public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        messager.printMessage(
            Kind.NOTE, String.format(
                "Call the round %d for %s", round.incrementAndGet(), StreamEx
                    .of(roundEnv.getRootElements()).map(Element::getSimpleName).joining(", ")
            )
        );

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
            .flatMap(this::processAnnotation)
            .ifPresent(v -> v.accept(generatorVisitor, m -> generateMatcher(m, packageName)));
        // Generate matchers for transitive beans (referenced from explicit configured)
        while (!process.isEmpty()) {
            generateMatcher(process.poll(), packageName);
        }

    }

    private static Optional<AnnotationMirror> getAnnotationMirror(AnnotatedConstruct element, Class<?> annotation) {
        String annotationName = annotation.getName();
        return StreamEx.of(element.getAnnotationMirrors())
            .map(AnnotationMirror.class::cast)
            .findAny(m -> annotationName.equals(m.getAnnotationType().toString()));
    }

    private Optional<AnnotationValue> processAnnotation(AnnotationMirror annotationMirror) {
        Optional<AnnotationValue> value = getAnnotationAttributeValue(annotationMirror, "value");
        if (value.isPresent()) {
            updateIgnorePackages(annotationMirror);
        }
        return value;
    }

    private void updateIgnorePackages(AnnotationMirror annotationMirror) {
        getAnnotationAttributeValue(annotationMirror, "ignorePackages")
            .ifPresent(a -> a.accept(new IgnorePackageVisitor(), this::addIgnorePackage));
    }

    private void addIgnorePackage(String packageToIgnore) {
        ignorePackagePrfixes = ImmutableSet.<String>builder()
            .addAll(ignorePackagePrfixes)
            .add(packageToIgnore)
            .build();
    }

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
        if (!processed.add(types.asElement(beanClass))) {
            messager.printMessage(Kind.NOTE, String.format("Generation of bean matcher for %s is skipped (already processed)", beanClass));
            return;
        }
        messager.printMessage(Kind.NOTE, String.format("Generate bean matcher for %s", beanClass));

        TypeElement element = (TypeElement) types.asElement(beanClass);
        if (element == null) {
            messager.printMessage(Kind.WARNING, String.format("Can't get TypeElement for %s", beanClass));
            return;
        }

        final BeanMatcherBuilder builder = BeanMatcherBuilder.create(messager, packageName, element);

        generateElementMatcher(element, builder);

        if (builder.hasProperties()) {
            writeJavaFile(packageName, builder.build(), builder.getStaticImports());
        } else {
            messager.printMessage(Kind.WARNING, String.format("Bean %s has not properties. Skip matcher generation", beanClass), element);
        }
    }

    private void generateElementMatcher(final TypeElement element, final BeanMatcherBuilder builder) {
        element.accept(BeanMatcherElementScanner.create(element, this), builder);
        TypeMirror superclass = element.getSuperclass();
        if (isEligibleSuperClass(superclass)) {
            TypeElement superElement = (TypeElement) types.asElement(superclass);
            if (superElement == null) {
                messager.printMessage(Kind.WARNING, String.format("Can't get TypeElement for %s", superclass));
                return;
            }
            generateElementMatcher(superElement, builder);
        }

    }

    protected void processProperty(PropertyInfo propertyInfo, BeanMatcherBuilder beanMatcherBuilder) {
        if (propertyInfo.getTypes() == null) {
            messager.printMessage(Kind.WARNING, String.format("Types for property %s is null. Ignore it", propertyInfo));
            return;
        }
        StreamEx.of(propertyInfo.getTypes())
            .filter(this::isEligibleClass)
            .toListAndThen(process::addAll);
        beanMatcherBuilder.add(propertyInfo);
    }

    private boolean isEligibleClass(TypeMirror beanClass) {
        return !beanClass.getKind().isPrimitive() && !processed.contains(types.asElement(beanClass)) && !ignoredPackage(beanClass)
            && !ignoredClass(beanClass);
    }

    private boolean isEligibleSuperClass(TypeMirror beanClass) {
        return !beanClass.getKind().isPrimitive() && !ignoredPackage(beanClass) && !ignoredClass(beanClass);
    }

    private boolean ignoredPackage(TypeMirror beanClass) {
        Element element = types.asElement(beanClass);
        if (element == null) {
            return false;
        }
        return ignoredPackage(element.getEnclosingElement());
    }

    private boolean ignoredPackage(Element element) {
        if (element == null) {
            return false;
        }
        messager.printMessage(Kind.NOTE, String.format("Ignore packages %s", ignorePackagePrfixes));
        boolean match = StreamEx.of(ignorePackagePrfixes)
            .anyMatch(pref -> startWith(element, pref));
        return match || ignoredPackage(element.getEnclosingElement());
    }

    private static boolean startWith(Element element, String packagePrefix) {
        if (element instanceof QualifiedNameable) {
            Name qualifiedName = ((QualifiedNameable) element).getQualifiedName();
            return qualifiedName.toString().toLowerCase().startsWith(packagePrefix);
        }
        return false;
    }

    private boolean ignoredClass(TypeMirror beanClass) {
        return StreamEx.of(ignoreClasses)
            .anyMatch(parent -> isSameOrExtends(beanClass, parent));
    }

    protected Optional<PropertyInfo> getProperty(ExecutableElement method) {
        messager.printMessage(Kind.NOTE, String.format("Process metthod %s", method));
        PropertyKind propertyKind = getPropertyKind(method);

        return Optional.of(method)
            .filter(e -> !TypeKind.VOID.equals(e.getReturnType().getKind()))
            .filter(e -> e.getParameters().isEmpty())
            .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
            .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
            .map(ExecutableElement::getSimpleName)
            .map(Name::toString)
            .map(BeanMatcherProcessor::getPropertyName)
            .map(name -> PropertyInfo.builder().name(name))
            .map(builder -> builder.kind(propertyKind))
            .map(builder -> builder.types(getPropertyTypes(propertyKind, method)))
            .map(PropertyInfo.PropertyInfoBuilder::build);
    }

    protected Optional<PropertyInfo> getProperty(RecordComponentElement property) {
        messager.printMessage(Kind.NOTE, String.format("Process record property %s", property));
        PropertyKind propertyKind = getPropertyKind(property);
        return Optional.of(property)
            .map(RecordComponentElement::getSimpleName)
            .map(Name::toString)
            .map(name -> PropertyInfo.builder().name(name))
            .map(builder -> builder.kind(propertyKind))
            .map(builder -> builder.types(getPropertyTypes(propertyKind, property)))
            .map(PropertyInfo.PropertyInfoBuilder::build);
    }

    private PropertyKind getPropertyKind(ExecutableElement method) {
        return getPropertyKind(method.getReturnType());
    }

    private PropertyKind getPropertyKind(RecordComponentElement property) {
        return getPropertyKind(property.asType());
    }

    private PropertyKind getPropertyKind(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor14<PropertyKind, Void>() {

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

    private Optional<PropertyKind> checkSpecificKinds(TypeMirror type) {
        if (types.isSameType(types.erasure(type), mapType)) {
            return Optional.of(PropertyKind.MAP);
        }
        if (types.isSameType(types.erasure(type), collectionType)) {
            return Optional.of(PropertyKind.COLLECTION);
        }
        if (types.isSameType(types.erasure(type), iterableType)) {
            return Optional.of(PropertyKind.ITERABLE);
        }
        return StreamEx.of(types.directSupertypes(type))
            .map(this::checkSpecificKinds)
            .findAny(Optional::isPresent)
            .orElseGet(Optional::empty);
    }

    private boolean isSameOrExtends(TypeMirror type, TypeMirror parent) {
        if (types.isSameType(types.erasure(type), parent)) {
            return true;
        }
        return StreamEx.of(types.directSupertypes(type))
            .anyMatch(st -> isSameOrExtends(st, parent));
    }

    private List<TypeMirror> getPropertyTypes(PropertyKind propertyKind, ExecutableElement method) {
        return getPropertyTypes(propertyKind, method.getReturnType());
    }

    private List<TypeMirror> getPropertyTypes(PropertyKind propertyKind, RecordComponentElement property) {
        return getPropertyTypes(propertyKind, property.asType());
    }

    private List<TypeMirror> getPropertyTypes(PropertyKind propertyKind, TypeMirror type) {
        return type.accept(new SimpleTypeVisitor14<List<TypeMirror>, Void>() {
            @Override
            public List<TypeMirror> visitArray(ArrayType type, Void p) {
                return List.of(type.getComponentType());
            }

            @Override
            public List<TypeMirror> visitDeclared(DeclaredType type, Void p) {
                if (propertyKind == PropertyKind.SCALAR) {
                    return List.of(type);
                }
                return StreamEx.of(type.getTypeArguments()).map(TypeMirror.class::cast).prepend(types.erasure(type)).toList();
            }

            @Override
            public List<TypeMirror> visitPrimitive(PrimitiveType type, Void p) {
                return List.of(type);
            }
        }, null);
    }

    protected static String getPropertyName(String methodName) {
        return getPropertyName(methodName, "get").orElseGet(() -> getPropertyName(methodName, "is").orElse(null));
    }

    protected static Optional<String> getPropertyName(String methodName, String prefix) {
        boolean getName = (methodName.length() > prefix.length()) && methodName.startsWith(prefix);
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
            processingEnv.getMessager().printMessage(
                Kind.ERROR, String.format("Can't write class %s to package %s: %s", spec, packageName, e.getMessage())
            );
        }
    }

}
