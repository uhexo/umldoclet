/*
 * Copyright 2016-2017 Talsma ICT
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
package nl.talsmasoftware.umldoclet.model;

import nl.talsmasoftware.umldoclet.rendering.indent.IndentingPrintWriter;

import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.Collection;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * @author Sjoerd Talsma
 */
public class Package extends UMLRenderer {

    protected final PackageElement packageElement;
    private final Collection<Reference> references;

    Package(UMLDiagram diagram, PackageElement packageElement) {
        super(diagram);
        this.packageElement = requireNonNull(packageElement, "Package element is <null>.");

        // Add all types contained in this package.
        packageElement.getEnclosedElements().stream()
                .filter(TypeElement.class::isInstance).map(TypeElement.class::cast)
                .map(type -> new Type(diagram, type))
                .forEach(children::add);

        this.references = children.stream()
                .filter(Type.class::isInstance).map(Type.class::cast)
                .flatMap(type -> type.references.stream().map(Reference::canonical))
                .collect(toSet());
    }

    @Override
    public IndentingPrintWriter writeTo(IndentingPrintWriter output) {
        output.append("namespace").whitespace().append(packageElement.getQualifiedName()).whitespace().append("{").newline();
        writeChildrenTo(output).newline();
        references.stream().map(Object::toString).forEach(ref -> output.indent().append(ref).newline());
        return output.newline().append("}").newline();
    }

}