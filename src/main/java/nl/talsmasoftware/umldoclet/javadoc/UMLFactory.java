/*
 * Copyright 2016-2018 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.talsmasoftware.umldoclet.javadoc;

import jdk.javadoc.doclet.DocletEnvironment;
import nl.talsmasoftware.umldoclet.configuration.Configuration;
import nl.talsmasoftware.umldoclet.uml.*;
import nl.talsmasoftware.umldoclet.rendering.Renderer;
import nl.talsmasoftware.umldoclet.rendering.indent.IndentingChildRenderer;

import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.ElementKind.ENUM;
import static nl.talsmasoftware.umldoclet.uml.Reference.Side.from;
import static nl.talsmasoftware.umldoclet.uml.Reference.Side.to;

/**
 * @author Sjoerd Talsma
 */
public class UMLFactory {

    final Configuration config;
    private final DocletEnvironment env;

    public UMLFactory(Configuration config, DocletEnvironment env) {
        this.config = requireNonNull(config, "Configuration is <null>.");
        this.env = requireNonNull(env, "Doclet environment is <null>.");
    }

    Namespace packageOf(TypeElement typeElement) {
        return new Namespace(config, env.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString());
    }

    Field createField(Type containingType, VariableElement variable) {
        Set<Modifier> modifiers = requireNonNull(variable, "Variable element is <null>.").getModifiers();
        return new Field(containingType,
                visibilityOf(modifiers),
                modifiers.contains(Modifier.STATIC),
                variable.getSimpleName().toString(),
                TypeNameVisitor.INSTANCE.visit(variable.asType())
        );
    }

    private Parameters toParameters(List<? extends VariableElement> params) {
        Parameters result = new Parameters(config);
        params.forEach(param -> result.add(param.getSimpleName().toString(), TypeNameVisitor.INSTANCE.visit(param.asType())));
        return result;
    }

    Method createConstructor(Type containingType, ExecutableElement executableElement) {
        Set<Modifier> modifiers = requireNonNull(executableElement, "Executable element is <null>.").getModifiers();
        return new Method(
                containingType,
                visibilityOf(modifiers),
                modifiers.contains(Modifier.ABSTRACT),
                modifiers.contains(Modifier.STATIC),
                containingType.name.simple,
                toParameters(executableElement.getParameters()),
                null
        );
    }

    Method createMethod(Type containingType, ExecutableElement executableElement) {
        Set<Modifier> modifiers = requireNonNull(executableElement, "Executable element is <null>.").getModifiers();
        return new Method(
                containingType,
                visibilityOf(modifiers),
                modifiers.contains(Modifier.ABSTRACT),
                modifiers.contains(Modifier.STATIC),
                executableElement.getSimpleName().toString(),
                toParameters(executableElement.getParameters()),
                TypeNameVisitor.INSTANCE.visit(executableElement.getReturnType())
        );
    }

    static Visibility visibilityOf(Set<Modifier> modifiers) {
        return modifiers.contains(Modifier.PRIVATE) ? Visibility.PRIVATE
                : modifiers.contains(Modifier.PROTECTED) ? Visibility.PROTECTED
                : modifiers.contains(Modifier.PUBLIC) ? Visibility.PUBLIC
                : Visibility.PACKAGE_PRIVATE;
    }

    Type createType(TypeElement typeElement) {
        return createType(packageOf(typeElement), typeElement);
    }

    static boolean addChild(IndentingChildRenderer parent, Renderer child) {
        Collection<Renderer> children = (Collection<Renderer>) parent.getChildren();
        return children.add(child);
    }

    Type createType(Namespace containingPackage, TypeElement typeElement) {
        ElementKind kind = requireNonNull(typeElement, "Type element is <null>.").getKind();
        Set<Modifier> modifiers = typeElement.getModifiers();
        TypeClassification classification = ENUM.equals(kind) ? TypeClassification.ENUM
                : ElementKind.INTERFACE.equals(kind) ? TypeClassification.INTERFACE
                : ElementKind.ANNOTATION_TYPE.equals(kind) ? TypeClassification.ANNOTATION
                : modifiers.contains(Modifier.ABSTRACT) ? TypeClassification.ABSTRACT_CLASS
                : TypeClassification.CLASS;

        Type type = new Type(containingPackage,
                classification,
                TypeNameVisitor.INSTANCE.visit(typeElement.asType())
        );

        // Add the various parts of the class UML, order matters here, obviously!
        if (TypeClassification.ENUM.equals(classification)) typeElement.getEnclosedElements().stream() // Enum const
                .filter(elem -> ElementKind.ENUM_CONSTANT.equals(elem.getKind()))
                .filter(VariableElement.class::isInstance).map(VariableElement.class::cast)
                .forEach(elem -> addChild(type, createField(type, elem)));
        typeElement.getEnclosedElements().stream() // Add fields
                .filter(elem -> ElementKind.FIELD.equals(elem.getKind()))
                .filter(VariableElement.class::isInstance).map(VariableElement.class::cast)
                .forEach(elem -> addChild(type, createField(type, elem)));
        typeElement.getEnclosedElements().stream() // Add constructors
                .filter(elem -> ElementKind.CONSTRUCTOR.equals(elem.getKind()))
                .filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
                .forEach(elem -> addChild(type, createConstructor(type, elem)));
        typeElement.getEnclosedElements().stream() // Add methods
                .filter(elem -> ElementKind.METHOD.equals(elem.getKind()))
                .filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
                .forEach(elem -> addChild(type, createMethod(type, elem)));

        return type;
    }

    private void addForeignType(Map<Namespace, Collection<Type>> foreignTypes, Element typeElement) {
        if (foreignTypes != null && typeElement instanceof TypeElement) {
            Type type = createType((TypeElement) typeElement);
            foreignTypes.computeIfAbsent(type.containingPackage, (namespace) -> new LinkedHashSet<>()).add(type);
        }
    }

    private Collection<Reference> findPackageReferences(
            Namespace namespace, Map<Namespace, Collection<Type>> foreignTypes, TypeElement typeElement, Type type) {
        Collection<Reference> references = new LinkedHashSet<>();

        // Superclass reference.
        if (!TypeKind.NONE.equals(typeElement.getSuperclass().getKind())) {
            String superclass = TypeNameVisitor.INSTANCE.visit(typeElement.getSuperclass()).qualified;
            if (!config.getExcludedReferences().contains(superclass)) {
                references.add(new Reference(
                        from(type.name.qualified), "--|>",
                        to(superclass)
                ));
            }
        }

        // Implemented interfaces.
        typeElement.getInterfaces().forEach(interfaceType -> {
            TypeName ifName = TypeNameVisitor.INSTANCE.visit(interfaceType);
            if (!config.getExcludedReferences().contains(ifName.qualified)) {
                references.add(new Reference(
                        from(type.name.qualified), "..|>",
                        to(ifName.qualified)));
                if (!namespace.contains(ifName)) {
                    addForeignType(foreignTypes, env.getTypeUtils().asElement(interfaceType));
                }
            }
        });

        // Add reference to containing class from innner classes.
        ElementKind enclosingKind = typeElement.getEnclosingElement().getKind();
        if (enclosingKind.isClass() || enclosingKind.isInterface()) {
            references.add(new Reference(
                    from(TypeNameVisitor.INSTANCE.visit(typeElement.getEnclosingElement().asType()).qualified),
                    "+--", to(type.name.qualified)));
        }

        // Add 'uses' references by replacing visible fields
//        final Set<Visibility> visible = EnumSet.of(Visibility.PACKAGE_PRIVATE, Visibility.PUBLIC);
        final Set<Visibility> visible = EnumSet.allOf(Visibility.class);
        typeElement.getEnclosedElements().stream()
                .filter(member -> ElementKind.FIELD.equals(member.getKind()))
                .filter(VariableElement.class::isInstance).map(VariableElement.class::cast)
                .filter(field -> visible.contains(visibilityOf(field.getModifiers())))
                .forEach(field -> {
                    String fieldName = field.getSimpleName().toString();
                    TypeName fieldType = propertyType(field.asType());
                    if (namespace.contains(fieldType)) {
                        addReference(references, new Reference(from(type.name.qualified),
                                "-->", to(fieldType.qualified), fieldName));
                        type.getChildren().removeIf(child -> child instanceof Field && ((Field) child).name.equals(fieldName));
                    }
                });
        // Add 'uses' reference by replacing visible getters/setters
        typeElement.getEnclosedElements().stream()
                .filter(member -> ElementKind.METHOD.equals(member.getKind()))
                .filter(ExecutableElement.class::isInstance).map(ExecutableElement.class::cast)
                .filter(method -> visible.contains(visibilityOf(method.getModifiers())))
                .forEach(method -> {
                    String propertyName = propertyName(method);
                    if (propertyName != null) {
                        TypeName returnType = propertyType(method.getReturnType());
                        if (namespace.contains(returnType)) {
                            addReference(references, new Reference(from(type.name.qualified),
                                    "-->", to(returnType.qualified), propertyName));
                            type.getChildren().removeIf(child -> child instanceof Method
                                    && ((Method) child).name.equals(method.getSimpleName().toString()));
                        }
                    }
                });

        return references;
    }

    private static String propertyName(ExecutableElement method) {
        String name = method.getSimpleName().toString();
        int params = method.getParameters().size();
        if (params == 0 && name.length() > 3 && name.startsWith("get")) {
            char[] result = name.substring(3).toCharArray();
            result[0] = Character.toLowerCase(result[0]);
            return new String(result);
        } else if (params == 1 && name.length() > 3 && name.startsWith("set")) {
            char[] result = name.substring(3).toCharArray();
            result[0] = Character.toLowerCase(result[0]);
            return new String(result);
        }
        // TODO: boolean isProperty() support.

        return null;
    }

    private static TypeName propertyType(TypeMirror typeMirror) {
        return iteratedType(typeMirror).orElseGet(() -> TypeNameVisitor.INSTANCE.visit(typeMirror));
    }

    private static Optional<TypeName> iteratedType(TypeMirror typeMirror) {
        if (typeMirror instanceof ArrayType) {
            return Optional.of(TypeNameVisitor.INSTANCE.visit(((ArrayType) typeMirror).getComponentType()));
        }
        return Optional.empty();
    }

    private static void addReference(Collection<Reference> collection, Reference reference) {
        Reference result = reference;
        Optional<Reference> found = collection.stream().filter(reference::equals).findFirst();
        if (found.isPresent()) {
            result = found.get();
            collection.remove(result);
            for (String note : reference.notes) result = result.addNote(note);
        }
        collection.add(result);
    }

    private static Stream<TypeElement> innerTypes(TypeElement type) {
        return Stream.concat(Stream.of(type), type.getEnclosedElements().stream()
                .filter(TypeElement.class::isInstance).map(TypeElement.class::cast)
                .flatMap(UMLFactory::innerTypes));
    }

    Namespace createPackage(PackageElement packageElement,
                            Map<Namespace, Collection<Type>> foreignTypes,
                            List<Reference> references) {
        Namespace pkg = new Namespace(config, packageElement.getQualifiedName().toString());

        // Add all types contained in this package.
        packageElement.getEnclosedElements().stream()
                .filter(TypeElement.class::isInstance).map(TypeElement.class::cast)
                .flatMap(UMLFactory::innerTypes)
                .map(typeElement -> {
                    Type type = createType(pkg, typeElement);
                    references.addAll(findPackageReferences(pkg, foreignTypes, typeElement, type));
                    return type;
                })
                .forEach(type -> addChild(pkg, type));

        return pkg;
    }

}