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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.gradle.ideaplugin.util.GradleUtils;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;
import org.gradle.openapi.external.ui.OutputObserverVersion1;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 Component that handles the output window. It creates an Idea tool window and puts the gradle UI's output
 in said tool window.
 */
public class GradleOutputComponent implements ProjectComponent, GradleUIAvailabilityObserver
{
   private Project myProject;

   private ToolWindow myToolWindow;
   private boolean isFirstRefresh = true;

   public static final String TOOL_WINDOW_ID = "Gradle Output"; //this will show up on the 'tab' in Idea. It will also show up as the title on this window.
   private DualPaneUIVersion1 gradleUI;
   private AnimatedToolIcon animatedToolIcon;
   private Icon gradleIcon;

   public GradleOutputComponent( Project project )
   {
      myProject = project;
   }

   public void initComponent()
   {
      gradleIcon = GradleUtils.loadGradleIcon();
      GradleUIApplicationComponent gradleUIApplicationComponent = ApplicationManager.getApplication().getComponent( GradleUIApplicationComponent.class );
      gradleUIApplicationComponent.addUIAvailabilityObserver( this, true, false );
   }

   public void disposeComponent()
   {
      
   }

   @NotNull
   public String getComponentName()
   {
      return "GradleOutputComponent";
   }


   public void projectOpened()
   {
      initToolWindow();
   }

   public void projectClosed()
   {
      unregisterToolWindow();
   }

   /**
      This initializes this tool window. We actually get our contents from the
      MainGradleComponent. This is also where we add a listener so we'll show
      the output whenever a request is made (in case a user closes it). We
      consider whether the user wants the output displayed always.

      This can be called multiple times, such as if the gradle home is changed.

      @author mhunsicker
   */
   private synchronized void initToolWindow()
   {
      MainGradleComponent mainGradleComponent = MainGradleComponent.getInstance( myProject );

      gradleUI = mainGradleComponent.getGradleUI();
      if( gradleUI != null )  //if we have a gradle UI, add it to our tool window
      {
         gradleUI.addOutputObserver( new IdeaOutputObserver() );

         if( myToolWindow == null ) //if we haven't created our tool window, do so now
         {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance( myProject );

            //register the tool Window. Ignore the 'Disposable' object. It's required (even though it doesn't apply to us)
            //if we want to set the 'canWorkInDumbMode' argument to true. That argument means this window will be enabled while
            //Idea is re-indexing the project settings.
            myToolWindow = toolWindowManager.registerToolWindow( TOOL_WINDOW_ID, false, ToolWindowAnchor.BOTTOM, new Disposable()
                  {
                     public void dispose() { }
                  }, true );

            hideToolWindow(); //nobody wants to watch it refresh, so we'll just hide this window by default
            initializeAnimation();
         }
         else
            myToolWindow.getContentManager().removeAllContents( true ); //remove any previous contents (this can be called to reset a new gradle home)

         ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
         Content content = contentFactory.createContent( mainGradleComponent.getOutputComponent(), "", false );
         myToolWindow.getContentManager().addContent( content );
         myToolWindow.setAvailable(true, new Runnable() { public void run() { } });

         setIcon();
      }
      else                    //otherwise, just unregister our tool window
         unregisterToolWindow();
   }

   /**
    Loads a series of images used for animating the gradle icon on the tool window
    so a user can see we're busy even if the tool window is closed.
    */
   private void initializeAnimation()
   {
      List<Icon> icons = new ArrayList<Icon>();
      
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_1.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_2.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_3.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_4.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_5.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_6.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_7.png", icons );
      loadIcon( "/org/gradle/ideaplugin/ui/gradle_busy_8.png", icons );

      animatedToolIcon = new AnimatedToolIcon( myToolWindow, 100, gradleIcon, icons );
   }

   //this safely loads the icons. Specifically, it doesn't crash if the icons aren't found.
   public static void loadIcon( String path, List<Icon> icons )
   {
      try
      {
         icons.add( IconLoader.getIcon( path ) );
      }
      catch (Exception e)
      {

         System.err.println("Failed to load icon at '" + path + "'");
      }
   }

   private synchronized void unregisterToolWindow()
   {
      if( myToolWindow == null )
         return;

      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance( myProject );
      toolWindowManager.unregisterToolWindow( TOOL_WINDOW_ID );
      myToolWindow = null;
   }

   //

         /**
          This listens for requests being added or completed so it can setup the icon animation
          as well as hiding/showing the output appropriately
          */
         private class IdeaOutputObserver implements OutputObserverVersion1
         {
            //add a listener that will show our output tool window whenever a command is executed
            public void executionRequestAdded( long requestID, String fullCommand, String displayName, boolean forceOutputToBeShown )
            {
               showToolWindow();
               setIcon();
            }

            public void refreshRequestAdded( long requestID, boolean forceOutputToBeShown )
            {
               if( !isFirstRefresh )   //skip the first refresh
                  showToolWindow();

               isFirstRefresh = false;
               setIcon();
            }

            public void requestComplete( long requestID, final boolean wasSuccessful )
            {
               SwingUtilities.invokeLater( new Runnable()
               {
                  public void run()
                  {
                     //if a request fails and we're hidden, show us.
                     if( !wasSuccessful && !myToolWindow.isVisible() )  //can only check visibility in the EDT
                        showToolWindow();
                  }
               } );

               setIcon();
            }

            public void outputTabClosed( long requestID )
            {
               if( gradleUI.getNumberOfOpenedOutputTabs() == 0 )
                  hideToolWindow(); //close it if its the last output tab

               setIcon();
            }
         }

   /**
      Makes the tool window visible.
      This seems to work when done in the EDT or not, but just to be safe since
      Idea throws exceptions in many places where it's expecting things to be
      done in the EDT. This will just be on the safe side.
      @author mhunsicker
   */
   private void showToolWindow()
   {
      if( myToolWindow != null )
         SwingUtilities.invokeLater( new Runnable()
         {
            public void run()
            {
               myToolWindow.activate( new Runnable() { public void run() { } } );   //we're really just interested in the activate, not the runnable.
            }
         } );
   }

   private void hideToolWindow()
   {
      if( myToolWindow != null )
         SwingUtilities.invokeLater( new Runnable()
         {
            public void run()
            {
               myToolWindow.hide( new Runnable() { public void run() { } } );   //we're really just interested in the activate, not the runnable.
            }
         } );
   }

   /**
      This sets the icon. Well, the animatedToolIcon actually handles most of
      the icon, but this determines its busy state. Call this whenever a task
      is start, stopped, or cancelled.
      @author mhunsicker
   */
   private void setIcon()
   {
      if( myToolWindow != null && animatedToolIcon != null )
         SwingUtilities.invokeLater( new Runnable()
         {
            public void run()
            {
               if( gradleUI == null )
               {
                  myToolWindow.setIcon( gradleIcon );
                  animatedToolIcon.stop();
               }
               else
               {
                  if( gradleUI.isBusy() )
                     animatedToolIcon.start();
                  else
                     animatedToolIcon.stop();
               }
            }
         } );
   }

   /**
    Notification that the gradle UI has been loaded.
    @param gradleUIVersion1 the gradle object that was loaded
    @param ideaProject           the idea project that loaded gradle
    @author mhunsicker
    */
   public void gradleUILoaded(DualPaneUIVersion1 gradleUIVersion1, Project ideaProject)
   {
      initToolWindow();
   }

   /**
    Notification that the gradle UI has been unloaded.
    @param project the idea project that gradle has been unloaded from.
    @author mhunsicker
    */
   public void gradleUIUnloaded( Project project )
   {
      unregisterToolWindow();
   }
}
