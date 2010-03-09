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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class GradleAccess
{
   private static final String GRADLE_DIRECTORY = "tools/gradle";

   @Nullable private final ClassLoader gradleClassLoader;
   @Nullable private final File projectRootDir;

   //this constructor is useful if you haven't opened a project yet, or know of where a gradle home directory
   //is through some other means.
   public GradleAccess( File projectRootDir )
   {
      this.projectRootDir = projectRootDir;
      gradleClassLoader = createGradleClassLoader( getGradleLibJars( getGradleHomeDirectory() ) );
      initGradleHome();
   }

   public GradleAccess(final Project project)
   {
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null)
         projectRootDir = VfsUtil.virtualToIoFile(baseDir).getParentFile().getParentFile();
      else
         projectRootDir = new File(System.getProperty("user.dir"));

      gradleClassLoader = createGradleClassLoader( getGradleLibJars( project ) );
      initGradleHome();
   }

   private void initGradleHome()
   {
      System.setProperty("gradle.home", getGradleHomeDirectory().getAbsolutePath());
   }

   @Nullable public File getProjectRootDir()
   {
      return projectRootDir;
   }

   public File getGradleHomeDirectory()
   {
      return new File( projectRootDir, GRADLE_DIRECTORY );
   }

   private static List<URL> getGradleLibJars( Project project )
   {
      List<VirtualFile> files = org.jetbrains.plugins.groovy.gradle.GradleSettings.getInstance(project).getClassRoots();
      VirtualFile[] vfiles = files.toArray( new VirtualFile[ files.size() ] );
      List<URL> urls = new ArrayList<URL>(vfiles.length );
      convertToURLAndAddToList(vfiles, urls);
      return urls;
   }

   //this reads off jars in the gradle lib directory
   public static List<URL> getGradleLibJars( File gradleHomeDirectory )
   {
      List<URL> urls = new ArrayList<URL>();

      File libDirectory = new File( gradleHomeDirectory, "lib" );
      if( !libDirectory.exists() )
         System.out.println( "Gradle directory '" + gradleHomeDirectory + "' does not exist!" );
      else
      {
         File[] files = libDirectory.listFiles();
         if( files != null && files.length != 0 )
         {
            for( int index = 0; index < files.length; index++ )
            {
               File file = files[index];
               if( !file.isHidden() && !file.isDirectory() && file.getName().toLowerCase().endsWith( ".jar" ) )
               {
                  try
                  {
                     URL url = file.toURI().toURL();
                     urls.add( url );
                  }
                  catch( MalformedURLException e )
                  {
                     e.printStackTrace();
                  }
               }
            }
         }
      }

      return urls;
   }

   @Nullable private static ClassLoader createGradleClassLoader(final List<URL> urls )
   {
      //return new URLClassLoader(urls.toArray(new URL[urls.size()]), ClassLoader.getSystemClassLoader());
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

   private static void convertToURLAndAddToList(VirtualFile[] vfiles, List<URL> urls)
   {
      for (VirtualFile vfile : vfiles)
      {
         if (vfile.isDirectory() && !"jar".equals(vfile.getExtension()))
         {
            convertToURLAndAddToList(vfile.getChildren(), urls);
         }
         else
         {
            try
            {
               URL url = VfsUtil.virtualToIoFile(vfile).toURI().toURL();
               urls.add(url);
            }
            catch (MalformedURLException ignored)
            {
               // just skip this one
            }
         }
      }
   }

   //returns the gradle class loader. Note: this will be null for non-gradle projects.
   @Nullable public ClassLoader getGradleClassLoader()
   {
      return gradleClassLoader;
   }

   public ScriptExecutor createExecutor()
   {
      return new ScriptExecutor(gradleClassLoader);
   }
}

