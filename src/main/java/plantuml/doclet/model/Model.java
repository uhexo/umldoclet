/*
 * Copyright (C) 2016 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package plantuml.doclet.model;

import java.util.Collection;

/**
 * This is the main interface for any UML model implementation.
 *
 * @author Gerald Boersma
 * @author Sjoerd Talsma
 */
public interface Model {

    /**
     * @return All packages contained in this model.
     */
    Collection<Package> getPackages();

    Package findPackage(String qualifiedName);

    /**
     * @return All types (classes, interfaces and enumerations) contained in this model.
     */
    Collection<Type> getTypes();

    Type findType(String qualifiedName);

}