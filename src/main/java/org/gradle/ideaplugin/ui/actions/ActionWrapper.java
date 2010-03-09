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
package org.gradle.ideaplugin.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;

/**
 A wrapper around an action. These are generated from gradle 'actions' (favorites, commands).
 These should generate an action. The action should not be specific to a gradle project. That
 is, a user can have multiple gradle-based projects open in Idea and they can execute the action
 associated with an ActionWrapper regardless of the current project. It may or may not apply.
 The action should handle that gracefully.
 These may be temporarily and destroyed. Be careful that you hold onto very little in the implementation.

 @author mhunsicker
 */
public interface ActionWrapper
{
   /**
      @return the Idea action associated with this wrapper
      @author mhunsicker
   */
   public AnAction getAction();

   /**
    @return the name for this action. It should be unique to this action and be re-usable across
    projects. This will be used to generate a unique ID for an action.
    */
   public String getName();
}
