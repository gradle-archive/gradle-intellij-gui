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
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import org.gradle.ideaplugin.ui.MainGradleComponent;
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;

/**
 This wraps a single favorite in an Idea action so it can be assigned hot keys.
 We use the favorite's name as the key since its friendly and this allows the user
 to assign a hot key to, for example, 'build' regardless of what 'build' does for
 each project.

 It works upon the active project.

 @author mhunsicker
*/
public class FavoriteTaskActionWrapper implements ActionWrapper
{
   private AnAction action;
   private String favoriteTaskName;

   /*package*/ FavoriteTaskActionWrapper( String favoriteTaskName )
   {
      this.favoriteTaskName = favoriteTaskName;

      action = new AnAction( getName() )
      {
         @Override
         public void actionPerformed( AnActionEvent anActionEvent )
         {
            //get the active project
            DataContext dataContext = anActionEvent.getDataContext();
            Project project = DataKeys.PROJECT.getData(dataContext);
            
            executeFavorite( project );
         }
      };
   }

   /**
    @return the name for this action. It should be unique to this action and be re-usable across
    projects. This will be used to generate a unique ID for an action.
    */
   public String getName()
   {
      return "Gradle: Execute Favorite '" + favoriteTaskName + "'";
   }

   public AnAction getAction()
   {
      return action;
   }


   private void executeFavorite( Project project )
   {
      DualPaneUIVersion1 gradleUI = MainGradleComponent.getGradleUIForProject( project );
      if( gradleUI != null )
      {
         FavoriteTaskVersion1 favorite = gradleUI.getFavoritesEditor().getFavoriteByDisplayName( favoriteTaskName );
         if( favorite != null ) //the user might have renamed it or they may have deleted it or switched to a different project that doesn't have this favorite
            gradleUI.executeCommand( favorite.getFullCommandLine(), favorite.getDisplayName() );
      }
   }
}

