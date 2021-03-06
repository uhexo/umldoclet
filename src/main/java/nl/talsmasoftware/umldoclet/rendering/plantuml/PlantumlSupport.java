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
package nl.talsmasoftware.umldoclet.rendering.plantuml;

import net.sourceforge.plantuml.version.Version;
import nl.talsmasoftware.umldoclet.logging.LogSupport;

/**
 * Simple class to perform detection of the plantuml library on the classpath.
 * Detection happens without a runtime dependency on plantuml itself, so we can cleanly avoid attempts
 * to generate images if the required library is not on the classpath at all.
 *
 * @author Sjoerd Talsma
 */
public class PlantumlSupport {

    /**
     * String signifying the plantuml support status.
     * Value <code>null</code> means not-yet determined: figure it out and updat this value.
     * The empty string (<code>""</code>) means that it was determined that no plantuml version was available.
     * Any non-empty string means that plantuml is available.
     */
    private static volatile String plantumlVersion = null;

    /**
     * @return <code>true</code> if 'some version of' the <code>plantuml</code> library is detected on the classpath,
     * otherwise <code>false</code>.
     */
    public static boolean isPlantumlDetected() {
        if (plantumlVersion == null) {
            plantumlVersion = determinePlantumlVersion();
        }
        LogSupport.trace("Detected plantuml version: \"{0}\".", plantumlVersion);
        return !plantumlVersion.isEmpty();
    }

    /**
     * Use reflection to determine the plantuml version that is available on the classpath.
     *
     * @return The plant UML version or the empty String (<code>""</code>) if not found.
     */
    private static synchronized String determinePlantumlVersion() {
        return Version.versionString();
    }

}
