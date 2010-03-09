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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import org.gradle.ideaplugin.ui.GradleUIApplicationComponent;
import org.gradle.ideaplugin.ui.GradleUIAvailabilityObserver;
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;
import org.gradle.openapi.external.foundation.favorites.FavoritesEditorVersion1;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**

 This create Idea actions that call gradle functionality. This is useful for
 assigning hot-keys to gradle functions (such as favorites). There should only
 be 1 instance of this per Idea instance. As such, it tracks actions across
 multiple projects.

 @author mhunsicker
  */
public class GradleActionLord implements GradleUIAvailabilityObserver
{
   private HashMap<Project, List<ActionWrapper>> projectsToActionMap = new HashMap<Project,List<ActionWrapper>>();

   public GradleActionLord( GradleUIApplicationComponent gradleUIApplicationComponent )
   {
      gradleUIApplicationComponent.addUIAvailabilityObserver( this, true, true );
   }

   /**
    Notification that the gradle UI has been loaded.
    @param gradleUIVersion1 the gradle object that was loaded
    @author mhunsicker
    */
   public void gradleUILoaded( DualPaneUIVersion1 gradleUIVersion1, Project project )
   {
      unregisterActionsFromIdea( project );   //remove any existing wrappers for this project in case this is being reloaded.

      List<ActionWrapper> actionWrappers = generateActionWrappers( gradleUIVersion1 );

      projectsToActionMap.put( project, actionWrappers );

      registerActionsWithIdea( actionWrappers );
   }

   /**
    Notification that the gradle UI has been unloaded.
    @param project the idea project that gradle has been unloaded from.
    @author mhunsicker
    */
   public void gradleUIUnloaded( Project project )
   {
      unregisterActionsFromIdea( project );
   }

   /**
    Generates all the action wrappers for this project. This does NOT register them with Idea.
    @param gradleUIVersion1 gradle
    @return a list of action wrappers
    */
   private List<ActionWrapper> generateActionWrappers( DualPaneUIVersion1 gradleUIVersion1 )
   {
      List<ActionWrapper> actionWrappers = new ArrayList<ActionWrapper>( );

      getFavoriteTaskActionWrappers( gradleUIVersion1, actionWrappers );

      actionWrappers.add( new ReExecuteLastCommandActionWrapper() );

      return actionWrappers;
   }

   /**
    this creates action wrappers for all the favorite tasks.
    @param gradleUIVersion1 gradle
    @param actionWrappers where we put the action wrappers we generate
    */
   private void getFavoriteTaskActionWrappers( DualPaneUIVersion1 gradleUIVersion1, List<ActionWrapper> actionWrappers )
   {
      FavoritesEditorVersion1 favoritesEditor = gradleUIVersion1.getFavoritesEditor();
      Iterator<FavoriteTaskVersion1> favoriteTask = favoritesEditor.getFavoriteTasks().iterator();
      while( favoriteTask.hasNext() )
      {
         FavoriteTaskVersion1 favoriteTaskVersion1 = favoriteTask.next();

         actionWrappers.add( new FavoriteTaskActionWrapper( favoriteTaskVersion1.getDisplayName() ) );
      }
   }

   /**
    Registers the actions with Idea. This makes Idea aware of them and they can be assigned hot-keys, etc.
    @param actionWrappers the actions to add
    */
   private void registerActionsWithIdea( List<ActionWrapper> actionWrappers )
   {
      Iterator<ActionWrapper> iterator = actionWrappers.iterator();
      while( iterator.hasNext() )
      {
         ActionWrapper actionWrapper = iterator.next();
         String name = actionWrapper.getName();

         if( ActionManager.getInstance().getAction( name ) == null )
            ActionManager.getInstance().registerAction( name, actionWrapper.getAction() );
      }
   }

   /**
    This cleans up the actions associated with the specified project. We unregister
    the actions from Idea.
    */
   private void unregisterActionsFromIdea( Project project )
   {
      List<ActionWrapper> actionWrappers = projectsToActionMap.get( project );
      if( actionWrappers == null )
         return;

      //remove it from the map before we unregister them so we can verify no one else is using them.
      projectsToActionMap.remove( project );

      Iterator<ActionWrapper> iterator = actionWrappers.iterator();
      while( iterator.hasNext() )
      {
         ActionWrapper actionWrapper = iterator.next();
         String name = actionWrapper.getName();

         if( ActionManager.getInstance().getAction( name ) != null && isInUse( name ) )
            ActionManager.getInstance().unregisterAction( name );
      }
   }

   /**
    Determines if an action with the specified name exists and is therefore in use.
    We look through the project/actions map for an action with the specified name.
    This is used to unregister actions. We only want to unregister ones that aren't
    being used.
    @param name the sought name.
    @return
    */
   private boolean isInUse( String name )
   {
      Iterator<List<ActionWrapper>> mainIterator = projectsToActionMap.values().iterator();
      while( mainIterator.hasNext() )
      {
         List<ActionWrapper> actionWrappers = mainIterator.next();
         Iterator<ActionWrapper> innerIterator = actionWrappers.iterator();

         while( innerIterator.hasNext() )
         {
            ActionWrapper actionWrapper = innerIterator.next();
            if( actionWrapper.getName().equals( name ) )
               return true;
         }
      }

      return false;
   }
}
