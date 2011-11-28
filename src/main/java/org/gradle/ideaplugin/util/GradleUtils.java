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
package org.gradle.ideaplugin.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.IconLoader;
import org.gradle.ideaplugin.ui.MainGradleComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleLibraryManager;
import org.jetbrains.plugins.groovy.util.SdkHomeConfigurable;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.HashMap;

/** @author ilyas */
public class GradleUtils
{
   @NotNull private static final Map<Project, GradleAccess> accessMap = new HashMap<Project, GradleAccess>();

   protected GradleUtils() {}

   @NotNull public static synchronized GradleAccess getAccess(@NotNull final Project project)
   {
      GradleAccess access = accessMap.get(project);
      if (access == null)
      {
         access = new GradleAccess(project);
         accessMap.put(project, access);
      }

      return access;
   }

   /**
    * This returns the current Gradle SDK directory as a File (vs VirtualFile).
    *
    * @return the gradle directory or null if its not set.
    */
   public static File getGradleSDKDirectory(Project project)
   {
      return getGradleLibraryManager().getGradleHome( project );
   }

   public static GradleLibraryManager getGradleLibraryManager()
   {
      return ServiceManager.getService( GradleLibraryManager.class );
   }

   /**
    * This sets the Gradle SDK directory for the specified project.
    * @param project the project
    * @param directory the new gradle SDK home directory
    */
   public static void setGradleSDKDirectory( final Project project, File directory )
   {
      File existingDirectory = getGradleSDKDirectory( project );
      if( existingDirectory != null && existingDirectory.equals( directory ) )
         return;  //already set

      SdkHomeConfigurable.SdkHomeBean state = new SdkHomeConfigurable.SdkHomeBean();
      state.SDK_HOME = FileUtil.toSystemIndependentName( directory.getAbsolutePath() );
      GradleSettings gradleSettings = GradleSettings.getInstance(project);
      if( gradleSettings != null )
         gradleSettings.GRADLE_HOME = directory.getAbsolutePath();

      //this tells the UI to update itself
      SwingUtilities.invokeLater(new Runnable()
      {
         public void run()
         {
            MainGradleComponent mainGradleComponent = MainGradleComponent.getInstance( project );
            mainGradleComponent.reset();
         }
      });
   }

   //this safely loads the default gradle icon.
   public static Icon loadGradleIcon()
   {
      try
      {
         return IconLoader.getIcon( "/org/gradle/ideaplugin/ui/gradle_16x16.png" );
      }
      catch( Throwable e )
      {
         return null;
      }
   }
}
