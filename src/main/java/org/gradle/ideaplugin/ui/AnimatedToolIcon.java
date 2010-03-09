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

import com.intellij.openapi.wm.ToolWindow;

import javax.swing.Icon;
import javax.swing.Timer;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 This manages an animated tool icon.
 You give it a default icon and list of icons to display during animation.
 Then call start and stop when you want the animation to do so.

 @author mhunsicker
 */
public class AnimatedToolIcon
{
   private ToolWindow toolWindow;

   private Icon defaultIcon;
   private List<Icon> icons;

   private int currentImageIndex;

   private Timer busyAnimationTimer;

   public AnimatedToolIcon( ToolWindow toolWindow, int delay, Icon defaultIcon, List<Icon> icons )
   {
      this.toolWindow = toolWindow;
      this.defaultIcon = defaultIcon;
      if( icons != null && !icons.isEmpty() )
         this.icons = icons;

      busyAnimationTimer = new Timer( delay, new ActionListener()
      {
         public void actionPerformed( ActionEvent e )
         {
            updateAnimatedIcon();
         }
      } );

      toolWindow.setIcon( defaultIcon );
   }

   //starts the animation. You can call this repeatedly after this starts and it has no ill effects. 
   public void start()
   {
      if( !busyAnimationTimer.isRunning() && icons != null )
      {
         busyAnimationTimer.start();
         currentImageIndex = 0;
      }
   }

   //stops the animation and sets the default icon
   //You must always call this from within the EDT.
   public void stop()
   {
      if( busyAnimationTimer.isRunning() )
          busyAnimationTimer.stop();

      try
      {
         toolWindow.setIcon( defaultIcon );
      }
      catch( Exception e )
      {
         //just eat this. This was occurring on some machines for some reason. It could be timing
         //issue within Idea. I suspect it might be when the plugin is first initialized, but not
         //yet realized.
         /*
         Error during dispatching of java.awt.event.InvocationEvent[INVOCATION_DEFAULT,runnable=com.automatedlogic.ideaplugins.gradle.ui.GradleOutputComponent$3@180295,notifier=null,catchExceptions=false,when=1260213997555] on sun.awt.windows.WToolkit@1d0a692
            java.lang.NullPointerException
               at com.intellij.ui.content.impl.ContentManagerImpl.getSelectedContent(ContentManagerImpl.java:155)
               at com.intellij.openapi.wm.impl.ToolWindowImpl.a(ToolWindowImpl.java:27)
               at com.intellij.openapi.wm.impl.ToolWindowImpl.getIcon(ToolWindowImpl.java:104)
               at com.intellij.openapi.wm.impl.ToolWindowImpl.setIcon(ToolWindowImpl.java:31)
               at com.automatedlogic.ideaplugins.gradle.ui.AnimatedToolIcon.stop(AnimatedToolIcon.java:73)
          */
      }
   }

   //this must always be called from within the EDT. That's why its only caled from the Swing timer.
   private void updateAnimatedIcon()
   {
      Icon icon = icons.get( currentImageIndex );
      toolWindow.setIcon( icon );

      currentImageIndex++;
      if( currentImageIndex >= icons.size() )
         currentImageIndex = 0;
   }
}
