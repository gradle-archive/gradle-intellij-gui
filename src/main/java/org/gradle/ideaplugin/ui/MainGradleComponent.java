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
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;
import org.gradle.openapi.external.ui.GradleTabVersion1;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.gradle.GradleLibraryManager;

import javax.swing.JPanel;

/*
This is a project component that shows a gradle UI in Idea. Idea instantiates
this whenever a project is opened. The real work is done in GradlePanelWrapper.
See it for more information. This class is focused on the necessary
interactions with Idea to display a component and deal with its lifecycle.
This creates a tool window and adds the GradlePanelWrapper to it (which may
just display an error).
This relies on you setting your gradle path in Idea correctly. This is currently
done in Settings (there should be a Gradle option on the left). Note: this requires
the JetGroovy to be installed where much of the Gradle-language support has
already been added to Idea.
*/
public class MainGradleComponent implements ProjectComponent, ProjectManagerListener, GradleUIAvailabilityObserver
{
   private GradlePanelWrapper gradlePanelWrapper;

   private Project myProject;

   private ToolWindow myToolWindow;

   public static final String TOOL_WINDOW_ID = "Gradle"; //this will show up on the 'tab' in Idea. It will also show up as the title on this window.

   private long lastTimeAskedCanClose = 0l;
   private boolean lastAnswerWhenAskedCanClose;

   public MainGradleComponent( Project project )
   {
      this.myProject = project;
   }

   public static MainGradleComponent getInstance( Project project )
   {
      return project.getComponent( MainGradleComponent.class );
   }

   public void initComponent()
   {
      gradlePanelWrapper = new GradlePanelWrapper();

      SettingsNodeVersion1 settingsNodeVersion1 = getSettings();

      gradlePanelWrapper.initialize( myProject, settingsNodeVersion1 );

      GradleUIApplicationComponent gradleUIApplicationComponent = ApplicationManager.getApplication().getComponent( GradleUIApplicationComponent.class );
      gradleUIApplicationComponent.addUIAvailabilityObserver( this, true, false );
   }

   public SettingsNodeVersion1 getSettings()
   {
      GradleUISettings2 gradleUISettings = GradleUISettings2.getInstance(myProject);

      if( gradleUISettings != null )
      {
         if( gradleUISettings.getRootNode().getChildNodes().isEmpty() ) //if we have no settings, perhaps we need to upgrade.
            gradleUISettings.upgradeSettings( myProject );

         return gradleUISettings.getRootNode();
      }

      return null;
   }

   public void disposeComponent()
   {
      //ProjectManager.getInstance().removeProjectManagerListener( this );
   }

   @NotNull
   public String getComponentName()
   {
      return "MainGradleWindow"; //I believe this is used as an internal ID by Idea. It shouldn't ever change.
   }

   public void projectOpened()
   {
      initToolWindow();
   }

   public void projectClosed()
   {
      unregisterToolWindow();
   }

   public void projectOpened( Project project ) { }
   public void projectClosed( Project project ) { }
   public void projectClosing( Project project ) { }

   /**
      This is called to see if we can close a project. If gradle is running,
      we'll ask the user for confirmation. Unfortunately, there's this odd
      behavior I'm seeing where this is called twice if you return true the first
      time. I don't think this mechanism was meant for prompting the user for
      confirmation. As an ugly hack, I'll track when we asked and if its less
      than so many seconds, we'll just return the last answer. This is so we
      don't get duplicate questions to the user.

      @param  project    the project being closed
      @return true if the project can be closed, false if not.
      @author mhunsicker
   */
   public boolean canCloseProject( Project project )
   {
      if( project != myProject )
         return true;

      long now = System.currentTimeMillis();
      long timeSinceLastPrompt = now - lastTimeAskedCanClose;
      if( timeSinceLastPrompt <= 2000 )
         return lastAnswerWhenAskedCanClose;

      lastAnswerWhenAskedCanClose = gradlePanelWrapper.canClose();

      lastTimeAskedCanClose = System.currentTimeMillis(); //set this AFTER asking because it displays a UI and the user may let it sit there before answering.

      return lastAnswerWhenAskedCanClose;
   }

   /*
   This sets up the tool window. This can be called multiple times (such as when gradle
   home is changed). As such, this may unregister the tool window if gradle goes away.
    */
   private synchronized void initToolWindow()
   {
      //Note: if gradle isn't configured, the gradle panel will say "gradle not configured". However,
      //we don't want to show any gradle window if the project doesn't even specify a gradle home.
      if( gradlePanelWrapper != null && gradlePanelWrapper.hasGradleHomeDirectory() )
      {
         if( myToolWindow == null )
         {
            ToolWindowManager toolWindowManager = ToolWindowManager.getInstance( myProject );

            //register the tool Window. Ignore the 'Disposable' object. It's required (even though it doesn't apply to us)
            //if we want to set the 'canWorkInDumbMode' argument to true. That argument means this window will be enabled while
            //Idea is re-indexing the project settings.
            myToolWindow = toolWindowManager.registerToolWindow( TOOL_WINDOW_ID, false, ToolWindowAnchor.LEFT, new Disposable()
                  {
                     public void dispose() { }
                  }, true );
         }
         else
            myToolWindow.getContentManager().removeAllContents(true);   //remove any previous contents (this can be called to reset a new gradle home)

         JPanel gradlePanel = gradlePanelWrapper.getMainComponent();

         //I'm going to pass in blank text here. The title will already say 'Gradle'. This is for further refinement.
         ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
         Content content = contentFactory.createContent(gradlePanel, "", false );
         myToolWindow.getContentManager().addContent( content );

         //set the icon. (should be 13 x 13?  but this one works...)
         myToolWindow.setIcon(GradleLibraryManager.GRADLE_ICON);
      }
      else
         unregisterToolWindow();
   }

   private synchronized void unregisterToolWindow()
   {
      if( myToolWindow == null )
         return;

      ToolWindowManager toolWindowManager = ToolWindowManager.getInstance( myProject );
      toolWindowManager.unregisterToolWindow( TOOL_WINDOW_ID );
      myToolWindow = null;
   }

   public void readExternal( Element element ) throws InvalidDataException
   {
      DefaultJDOMExternalizer.readExternal( this, element );
   }

   public void writeExternal( Element element ) throws WriteExternalException
   {
      DefaultJDOMExternalizer.writeExternal( this, element );
   }

   /**
      This adds a tab. You'll want to add tabs using this rather than calling
      SinglePaneUIVersion1.addTab directly. Why? Because this removes the timing
      issues. That is, whether or not we've loaded the gradle UI, you can call
      this and we'll show the tabs whenever we do display the UI.
      @param  tab        the tab to add
      @author mhunsicker
   */
   public void addTab( GradleTabVersion1 tab )
   {
      gradlePanelWrapper.addTab( tab );
   }

   public void removeTab( GradleTabVersion1 tab )
   {
      gradlePanelWrapper.removeTab( tab );
   }

   /**
      @return the single pane UI version1. Note: this is very likely to return
              null if this hasn't been setup yet or hasn't loaded yet.
      @author mhunsicker
   */
   public DualPaneUIVersion1 getGradleUI() { return gradlePanelWrapper.getGradleUI(); }

   public JPanel getOutputComponent() { return gradlePanelWrapper.getOutputComponent(); }

   public void gradleUILoaded(DualPaneUIVersion1 gradleUIVersion1, Project ideaProject)
   {
      initToolWindow();
   }

   public void gradleUIUnloaded( Project project )
   {
      unregisterToolWindow();
   }

   /**
    * This updates the main gradle panel if the path to the gradle home has changed.
    * This triggers the wrapper to reload which will fire a gradleUILoaded/Unloaded
    * message -- which is when this component will reflect this change
    */
   public void reset()
   {
      if( gradlePanelWrapper != null )
         gradlePanelWrapper.reset();
   }

   /**
    This returns the gradle UI for the specified Idea project.
    @param project the project in question
    @return the gradle UI. It may return null if there is no gradle associated with
            the project, or the UI could not be loaded.
    */
   public static DualPaneUIVersion1 getGradleUIForProject( Project project )
   {
      MainGradleComponent mainGradleComponent = MainGradleComponent.getInstance( project );
      if( mainGradleComponent != null )
         return mainGradleComponent.getGradleUI();

      return null;
   }
}