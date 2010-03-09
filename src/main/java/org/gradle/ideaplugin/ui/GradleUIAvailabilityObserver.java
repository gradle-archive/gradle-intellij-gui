/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.ideaplugin.ui;

import com.intellij.openapi.project.Project;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;

/**
 
 This allows you to listen for the gradle UI coming and going.

 @author mhunsicker
*/
public interface GradleUIAvailabilityObserver
{
   /**
    Notification that the gradle UI has been loaded.
    @param gradleUIVersion1 the gradle object that was loaded
    @param ideaProject           the idea project that loaded gradle
    @author mhunsicker
    */
   public void gradleUILoaded( DualPaneUIVersion1 gradleUIVersion1, Project ideaProject );

   /**
    Notification that the gradle UI has been unloaded.
    @param project the idea project that gradle has been unloaded from.
    @author mhunsicker
    */
   public void gradleUIUnloaded( Project project );
}
