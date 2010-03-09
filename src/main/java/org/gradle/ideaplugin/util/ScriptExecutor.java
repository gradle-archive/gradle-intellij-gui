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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.script.ScriptException;
import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.Bindings;

public class ScriptExecutor
{
   @Nullable private final ClassLoader classLoader;

   public ScriptExecutor(@Nullable ClassLoader classLoader)
   {
      this.classLoader = classLoader;
   }

   @Nullable public Object execute(@NotNull  final String scriptText,
                                   @Nullable final NameValuePair... bindings) throws ScriptException
   {
      if ( classLoader == null )
         return null;

      ClassLoader oldCtxClassLoader = Thread.currentThread().getContextClassLoader();
      try
      {
         Thread.currentThread().setContextClassLoader(classLoader);

         ScriptEngineManager sem = new ScriptEngineManager(classLoader);
         ScriptEngine se = sem.getEngineByName("groovy");

         Bindings scriptBindings = se.createBindings();
         if( bindings != null )
            for (NameValuePair binding : bindings)
               scriptBindings.put(binding.getName(), binding.getValue());

         return se.eval(scriptText, scriptBindings);
      }
      finally
      {
         Thread.currentThread().setContextClassLoader(oldCtxClassLoader);
      }
   }

   public static class NameValuePair
   {
      private String name;
      private Object value;

      public NameValuePair(String name, Object value)
      {
         this.name = name;
         this.value = value;
      }

      public String getName() { return name; }
      public Object getValue() { return value; }
   }
}