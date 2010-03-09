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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import org.gradle.ideaplugin.ui.actions.GradleActionLord;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;
import org.jetbrains.annotations.NotNull;

import javax.swing.SwingUtilities;

/**
 This object is instantiated by Idea and lives for the life of your Idea
 instance. It provides a location to add observers so you can be notified when
 the gradle UI is instantiated/destroyed across projects.
   @author mhunsicker
*/
public class GradleUIApplicationComponent implements ApplicationComponent
{
   private ObserverLord<GradleUIAvailabilityObserver> observerLord = new ObserverLord<GradleUIAvailabilityObserver>();
   private GradlePanelWrapper gradlePanelWrapper;
   private GradleActionLord actionLord = new GradleActionLord( this );


   public GradleUIApplicationComponent()
   {
   }
   
   public void initComponent()
   {
   }

   public void disposeComponent()
   {
   }

   @NotNull
   public String getComponentName()
   {
      return "GradleUIApplicationComponent";
   }

   /**
      Call this to add an observer to when the UI (and its foundation) becomes
      available or unavailable.

      @param  observer             what gets notified
      @param  inEventQueue         true to notify you only in the event queue.
      @param  notifyImmediatelyIfAlreadyAvailable convenience flag so you don't
                                   have to worry about timing issues between when
                                   your observer was added an when the UI became
                                   available.
      @author mhunsicker
   */
   public void addUIAvailabilityObserver( final GradleUIAvailabilityObserver observer, boolean inEventQueue, boolean notifyImmediatelyIfAlreadyAvailable )
   {
      observerLord.addObserver( observer, inEventQueue );

      if( notifyImmediatelyIfAlreadyAvailable && gradlePanelWrapper != null )
      {
         if( !inEventQueue || SwingUtilities.isEventDispatchThread() )
            observer.gradleUILoaded( gradlePanelWrapper.getGradleUI(), gradlePanelWrapper.getProject() );
         else
         {
            SwingUtilities.invokeLater( new Runnable()
            {
               public void run()
               {
                  observer.gradleUILoaded( gradlePanelWrapper.getGradleUI(), gradlePanelWrapper.getProject() );
               }
            } );
         }
      }
   }

   public void removeUIAvailabilityObserverObserver( GradleUIAvailabilityObserver observer )
   {
      observerLord.removeObserver( observer );
   }

   /*package*/ void notifyGradleUILoaded( final GradlePanelWrapper gradlePanelWrapper )
   {
      this.gradlePanelWrapper = gradlePanelWrapper;

      final DualPaneUIVersion1 gradleUI = gradlePanelWrapper.getGradleUI();
      final Project project = gradlePanelWrapper.getProject();

      observerLord.notifyObservers( new ObserverLord.ObserverNotification<GradleUIAvailabilityObserver>()
      {
         public void notify( GradleUIAvailabilityObserver observer )
         {
            observer.gradleUILoaded( gradleUI, project );
         }
      } );
   }

   /**
      Notification that the gradle UI has been unloaded.
      @author mhunsicker
      @param project the project that gradle was unloaded from
    */
   /*package*/ void notifyGradleUIUnloaded( final Project project )
   {
      this.gradlePanelWrapper = null;

      observerLord.notifyObservers( new ObserverLord.ObserverNotification<GradleUIAvailabilityObserver>()
      {
         public void notify( GradleUIAvailabilityObserver observer )
         {
            observer.gradleUIUnloaded( project );
         }
      } );
   }

}
