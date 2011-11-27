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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.gradle.ideaplugin.util.GradleUtils;
import org.gradle.openapi.external.ui.AlternateUIInteractionVersion1;
import org.gradle.openapi.external.ui.DualPaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.DualPaneUIVersion1;
import org.gradle.openapi.external.ui.GradleTabVersion1;
import org.gradle.openapi.external.ui.SettingsNodeVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIInteractionVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;
import org.gradle.openapi.external.ui.UIFactory;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**

 This shows the gradle UI. It maintains both the main gradle UI and the output
 pane. They are both separated for this plugin.

 The gradle UI is loaded dynamically from an external gradle installation.
 There is a gradle open API jar that does the real work (and is part of gradle).
 The gradle UI implementation uses versioned interfaces and wrappers to interact
 with us. This allows us to be forward and backward compatible with gradle
 (although certain major changes will break us). This allows the gradle UI to
 change and gain new features that we don't have to know about (new features that
 require our interaction obviously won't work, but the wrappers provide an
 appropriate default behavior that is defined inside gradle).

 You can also add custom tabs to the gradle UI through this class without
 having to know whether or not the UI has loaded or not.

 @author mhunsicker
  */
public class GradlePanelWrapper
{
   private JPanel mainPanel = new JPanel( new BorderLayout() );
   private JPanel outputPanel = new JPanel( new BorderLayout() );
   private Project myProject;
   private SettingsNodeVersion1 settings;
   private DualPaneUIVersion1 gradleUI;
   private GradleSetupErrorPanel gradleSetupErrorPanel;
   private List<GradleTabVersion1> additionalTabs = new ArrayList<GradleTabVersion1>();
   private GradleUIApplicationComponent applicationComponent;
   private File gradleHomeDirectory;

   public void initialize( Project myProject, SettingsNodeVersion1 settings )
   {
      this.myProject = myProject;
      this.settings = settings;
      this.applicationComponent = ApplicationManager.getApplication().getComponent( GradleUIApplicationComponent.class );

      //Just to initialize it to 'not configured'. This is so there's something useful there when its not configured.
      addNotConfiguredPanel();

      reset();
   }

   public Project getProject() { return myProject; }

   /**
    * This sets the gradle home directory and tries to extract the UI from it. You can also
    * call this if you have changed the gradle directory behind our backs. We'll reload it.
    */
   public synchronized void reset()
   {
      File newGradleHomeDirectory = GradleUtils.getGradleSDKDirectory( myProject );

      //this will either load the UI from gradle or display the gradle setup panel.
      extractGradleUI(newGradleHomeDirectory);
   }

   /**
      Compares two files but also checks for their both being null, which is
      valid, but if one is, so must the other be.
   */
   public static boolean areEqual( File file1, File file2)
   {
      if ( file1 == null || file2 == null )
         return file2 == file1; //yes, we're not using '.equals', we're making sure they both equal null because one of them is null!

      return file1.equals(file2);
   }

   //this tries to setup the gradle UI by extracting it from a gradle installation
   //NOTE: do not return from this function without firing off
   //applicationComponent.notifyGradleUILoaded or applicationComponent.notifyGradleUIUnloaded
   private synchronized boolean extractGradleUI( File newGradleHomeDirectory )
   {
      if( areEqual( this.gradleHomeDirectory, newGradleHomeDirectory ) )
         return false;  //this is the only place we can return without calling applicationComponent.notifyGradleUIxxxx. That's because there's nothing to do.

      this.gradleHomeDirectory = newGradleHomeDirectory;

      DualPaneUIVersion1 previousGradleUI = gradleUI;

      try
      {
         if( gradleHomeDirectory == null )
         {
            addNotConfiguredPanel();
         }
         else
            if( !gradleHomeDirectory.exists()  )
            {
               addGradleSetupErrorPanel( "Gradle directory does not exist.", "" );
            }
            else
            {
               //Since this is Swing-related we want to make sure its always executed in the EDT.
               //When called by Idea, we're not in the EDT. When called from out setup panel, we
               //ARE in the EDT. If this fails, it will add the gradle setup panel.
               if( SwingUtilities.isEventDispatchThread() )
                  loadUIFromGradle();
               else
                  SwingUtilities.invokeAndWait( new Runnable()
                  {
                     public void run()
                     {
                        loadUIFromGradle();
                     }
                  } );
            }

         if( gradleUI != null )
         {
            //by default, we'll set it to your project's directory, but this will probably be overridden when its settings are loaded in aboutToShow.
            gradleUI.setCurrentDirectory( new File( myProject.getBaseDir().getPath() ) );
            addAdditionalTabs();
            setPanelContents( mainPanel, gradleUI.getMainComponent() );
            setPanelContents( outputPanel, gradleUI.getOutputPanel() );
            gradleUI.aboutToShow();

            applicationComponent.notifyGradleUILoaded( this );
            return true;
         }

         if( previousGradleUI != null )   //if they previously had a UI, but now don't, then it was unloaded
            applicationComponent.notifyGradleUIUnloaded( myProject );
      }
      catch( Exception e )
      {
         gradleUI = null;
         applicationComponent.notifyGradleUIUnloaded( myProject );
         addGradleSetupPanel( "Failed to load the gradle library (1).", e );
      }

      return false;
   }

   /**
      This dynamically loads the UI from a gradle installation. We call into
      a function inside the gradle open API jar that handles all the reflection
      of loading the classes. If this fails, we show the gradle setup panel.

      @author mhunsicker
   */
   private void loadUIFromGradle( )
   {
      try
      {
         gradleUI = UIFactory.createDualPaneUI( GradlePanelWrapper.class.getClassLoader(), gradleHomeDirectory, new IdeaUIInteraction(), false );
         if( gradleUI != null )
            return;

         addGradleSetupErrorPanel( "Failed to load the gradle library. Nothing was returned by the UI Factory.", "" );
      }
      catch( Throwable e )
      {
         e.printStackTrace();
         addGradleSetupPanel( "Failed to load the gradle library (3).", e );
      }

      gradleUI = null;
   }

   public JPanel getMainComponent() { return mainPanel; }
   public JPanel getOutputComponent() { return outputPanel; }

   private void addNotConfiguredPanel()
   {
      addGradleSetupErrorPanel( "Gradle not configured. Go to\n" +
                                "File/Settings and select Gradle\n" +
                                "in the Project Settings list on\n" +
                                "the left. Then specify the path\n" +
                                "on the right. Lastly press the\n" +
                                "'" + GradleSetupErrorPanel.ATTEMPT_TO_RELOAD_UI_TEXT + "'\n" +
                                "button.",  null );
   }

   /**
      This sets the main panel to the gradle setup error panel. This is used when
      we could not load gradle either because of an error or lack of setup.

      @param  message    a message of why we're displaying this.
      @param  messageDetails detailed information about why we're not displaying this.
                         this will only be visible if the user chooses to show it.
      @author mhunsicker
   */
   private void addGradleSetupErrorPanel( String message, String messageDetails )
   {
      if( gradleSetupErrorPanel == null )
         gradleSetupErrorPanel = new GradleSetupErrorPanel( myProject, new GradleSetupErrorPanel.GradleLoader()
         {
            public void reload()
            {
               reset();
            }
         } );

      if( this.gradleHomeDirectory != null )
         messageDetails += "\n" + this.gradleHomeDirectory.getAbsolutePath();

      gradleSetupErrorPanel.aboutToShow();
      gradleSetupErrorPanel.setMessage( message, messageDetails );
      setPanelContents( mainPanel, gradleSetupErrorPanel.getComponent() );
   }

   private void addGradleSetupPanel( String message, Throwable throwable )
   {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      throwable.printStackTrace(pw);

      addGradleSetupErrorPanel( message, throwable.getMessage() + "\n" + sw.toString() );
   }

   /**
    This replaces the main panel's contents with the specified new component.

      @author mhunsicker
   */
   private void setPanelContents( JPanel parent, Component newComponent )
   {
      parent.removeAll();
      parent.invalidate();
      parent.add( newComponent, BorderLayout.CENTER );
      parent.validate();
      parent.repaint();
   }


   //
         /**
            This class is how we interact with Gradle, specifically how to specify
            what we use to interact with it. The purpose of SinglePaneUIInteractionVersion1
            is to aid forward/backward compatibility.
            @author mhunsicker
         */
         private class IdeaUIInteraction implements DualPaneUIInteractionVersion1, SinglePaneUIInteractionVersion1
         {
            private IdeaUIInteraction()
            {
            }

            /**
               This is only called once and is how we get ahold of the AlternateUIInteraction.
               @return an AlternateUIInteraction object. This cannot be null.
               @author mhunsicker
            */
            public AlternateUIInteractionVersion1 instantiateAlternateUIInteraction()
            {
               return new IdeaAlternateUIInteraction();
            }

            /**
             This is only called once and is how we get ahold of how the owner wants
             to store preferences.
             @return a settings object. This cannot be null.
             @author mhunsicker
             */
            public SettingsNodeVersion1 instantiateSettings()
            {
               return settings;
            }
         }

   //
         /**
          Inner class that handles messages from the gradle UI such as editing/opening files.
          @author mhunsicker
         */
         private class IdeaAlternateUIInteraction implements AlternateUIInteractionVersion1
         {
            public void editFile(File file, int lineNumber)
            {
               GradlePanelWrapper.this.editFile( file, lineNumber);
            }

            public void openFile(File file, int lineNumber)
            {
               if( file.getName().toLowerCase().endsWith(".htm") ||
                   file.getName().toLowerCase().endsWith(".html") ) //by default, we'll browse HTML files. Ultimately, this should be probably be configurable.
                  GradlePanelWrapper.this.openInBrowser( file );
               else
                  GradlePanelWrapper.this.editFile( file, lineNumber);
            }

            public boolean doesSupportEditingOpeningFiles()
            {
               return true;
            }

            /**
             Notification that a command is about to be executed. This is mostly useful
             for IDE's that may need to save their files.

             We don't care about the command, but we need to save our current files.
             This is so they're on disk when gradle compiles them.

             @param fullCommandLine the command that's about to be executed.
             @author mhunsicker
             */
            public void aboutToExecuteCommand( String fullCommandLine )
            {
               //this must be done inside the EDT. However, if we're not in the EDT, we need to wait until Idea
               //has in fact, saved its files before we return so we know our files will be saved before a build is performed.
               if( SwingUtilities.isEventDispatchThread() )
                  FileDocumentManager.getInstance().saveAllDocuments();
               else
               {
                  try
                  {
                     SwingUtilities.invokeAndWait( new Runnable()
                     {
                        public void run()
                        {
                           FileDocumentManager.getInstance().saveAllDocuments();
                        }
                     } );
                  }
                  catch( Exception e )
                  {
                     e.printStackTrace();
                  }
               }
            }
         }

    /**
      Opens a single file in Idea.

      @param  file       the file to open
      @author mhunsicker
   */
   private void editFile( File file, int lineNumber )
   {
      if( file != null && file.exists() )
      {
         VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile( file );
         if( virtualFile != null )
         {
            FileEditorManager.getInstance(myProject).openFile( virtualFile, true);
            gotoLine( lineNumber );
         }
      }
   }

   //this navigates to the given line number in the current editor. If the line number is too high, we'll go to the
   //last line. If its -1, we'll ignore it since that's our flag for 'no line specified'.

   public boolean gotoLine(int lineNumber)
   {
      if (lineNumber <= -1)
         return false;

      Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
      if (editor == null)
         return false;

      CaretModel caretModel = editor.getCaretModel();
      int totalLineCount = editor.getDocument().getLineCount();

      if (lineNumber > totalLineCount)
         lineNumber = totalLineCount;

      //Moving caret to line number
      caretModel.moveToLogicalPosition(new LogicalPosition(lineNumber - 1, 0));

      //Scroll to the caret
      ScrollingModel scrollingModel = editor.getScrollingModel();
      scrollingModel.scrollToCaret(ScrollType.CENTER);

      return true;
   }

   //This opens the file in the user's default browser. Well ... default in Idea's eyes. It uses
   //Idea's Web Browser settings to open the file.
   private void openInBrowser( File file )
   {
      try
      {
         String path = file.getAbsolutePath();
         path = path.replace( '\\', '/' );
         BrowserUtil.launchBrowser( "file://" + path );  //launchBrowser doesn't like File.toURI().toURL(), so I'll just manually build it up (which may cause other problems).
      }
      catch( Exception e )
      {
         editFile( file, -1 );
      }
   }

   public void close()
   {
      if( gradleUI != null )
      {
         gradleUI.close();

         //I'm going to clear this out because I think this is being called multiple times.
         setPanelContents( mainPanel, new JLabel( "Closing" ) );
         gradleUI = null;
         applicationComponent.notifyGradleUIUnloaded( myProject );
      }
   }

   /**
      Call this to determine if we can close. We'll just ask the gradle UI.
      @author mhunsicker
   */
   public boolean canClose()
   {
      if( gradleUI != null )
         return gradleUI.canClose( new SinglePaneUIVersion1.CloseInteraction()
         {
            public boolean promptUserToConfirmClosingWhileBusy()
            {
               int result = JOptionPane.showConfirmDialog( SwingUtilities.getWindowAncestor( mainPanel ), "Gradle tasks are being currently being executed. Exit anyway?", "Exit While Busy?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE );
               return result == JOptionPane.YES_OPTION;
            }
         } );

      return true;
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
      additionalTabs.add( tab );
      if( gradleUI != null )
         gradleUI.addTab( gradleUI.getGradleTabCount() + 1, tab );
   }

   public void removeTab( GradleTabVersion1 tab )
   {
      additionalTabs.remove( tab );
      if( gradleUI != null )
         gradleUI.removeTab( tab );
   }

   private void addAdditionalTabs()
   {
      Iterator<GradleTabVersion1> iterator = additionalTabs.iterator();
      while( iterator.hasNext() )
      {
         GradleTabVersion1 gradleTab = iterator.next();
         gradleUI.addTab( gradleUI.getGradleTabCount() + 1, gradleTab );
      }
   }

   /**
      @return the gradle UI. Note: this is very likely to return null if
               this hasn't been setup yet or hasn't loaded yet.
      @author mhunsicker
   */
   public DualPaneUIVersion1 getGradleUI()
   {
      return gradleUI;
   }

   public synchronized void refreshTaskList()
   {
      if( gradleUI != null )
         gradleUI.refreshTaskTree();
   }

   /**
    * This determines if we've got a gradle home directory even specified. If we do,
    * then gradle was specified by the project and should be used. This is just a useful
    * flag for determining if an gradle components should be shown.
    * @return true if we have a gradle home, false if not.
    */
   public synchronized boolean hasGradleHomeDirectory()
   {
      return gradleHomeDirectory != null;
   }
}


