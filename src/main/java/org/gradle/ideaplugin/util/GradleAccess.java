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

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleLibraryManager;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class GradleAccess
{
   @Nullable private final ClassLoader gradleClassLoader;

   public GradleAccess(final Project project)
   {
      initGradleHome( getGradleHomeDirectory( project ) );
      gradleClassLoader = createGradleClassLoader( getGradleLibJars( project ) );
   }

   private void initGradleHome( File gradleHomeDirectory )
   {
      System.setProperty("gradle.home", gradleHomeDirectory.getAbsolutePath());
   }

   public File getGradleHomeDirectory( Project project )
   {
      return GradleUtils.getGradleLibraryManager().getGradleHome( project );
   }

   private List<URL> getGradleLibJars( Project project )
   {
      Collection<File> allLibraries = GradleUtils.getGradleLibraryManager().getAllLibraries( project );
      return convertToURLList( allLibraries );
   }

   @Nullable private static ClassLoader createGradleClassLoader(final List<URL> urls )
   {
      if( urls == null )
         return null;   //this happens for non-gradle projects.

      return new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader())
      {
         /**
            Overrides the method from ClassLoader.  This implementation attempts to
            load the class using our classpath first.  Only if the class cannot be
            found does it delegate.  The delegation handles loading all the standard
            Java classes, and things from the ext and endorsed directories.
            @author jmurph
         */
         @Override protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
         {
            Class<?> c = findLoadedClass(name);
            if (c != null)
               return c;

            try
            {
               c = findClass(name);
               if (resolve)
                  resolveClass(c);
               return c;
            }
            catch (ClassNotFoundException e)
            {
               return super.loadClass(name, resolve);
            }
         }
      };
   }

   private List<URL> convertToURLList( Collection<File> files )
   {
      if( files == null )
         return null;

      List<URL> urls = new ArrayList<URL>();

      Iterator<File> iterator = files.iterator();
      while( iterator.hasNext() )
      {
         File file = iterator.next();
         try
         {
            urls.add( file.toURI().toURL() );
         }
         catch( MalformedURLException e )
         {
            //skip it
         }
      }

      return urls;
   }

   //returns the gradle class loader. Note: this will be null for non-gradle projects.
   @Nullable public ClassLoader getGradleClassLoader()
   {
      return gradleClassLoader;
   }

   //This allows you to execute groovy scripts using the current
   //gradle settings (the groovy embedded within gradle).
   public ScriptExecutor createExecutor()
   {
      return new ScriptExecutor(gradleClassLoader);
   }
}

