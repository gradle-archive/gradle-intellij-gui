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

import com.intellij.openapi.project.Project;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;

/**
 
 This panel is shown when an error occurs trying to extract the gradle UI from an
 existing gradle installation. It provides some information about what went wrong
 so you can correct the problem.

 @author mhunsicker
  */
public class GradleSetupErrorPanel
{
   private Project myProject;

   private JPanel mainPanel;
   private JPanel messagePanel;
   private JLabel messageLabel;

   private String messageDetails;
   private String message;
   private JButton detailsButton;

   //
   public GradleSetupErrorPanel( Project myProject )
   {
      this.myProject = myProject;
      setupUI();
   }

   public Component getComponent() { return mainPanel; }

   private void setupUI()
   {
      mainPanel = new JPanel( new BorderLayout() );

      JPanel innerPanel = new JPanel( );
      innerPanel.setLayout( new BoxLayout( innerPanel, BoxLayout.Y_AXIS ) );

      innerPanel.add( createMessageComponent() );
      //innerPanel.add( Box.createVerticalGlue() );

      mainPanel.add( innerPanel, BorderLayout.NORTH );   //this pushes things up. Glue doesn't work in this situation.

      mainPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 10, 10 ) );
   }

   private Component createMessageComponent()
   {
      messagePanel = new JPanel();
      messagePanel.setLayout( new BoxLayout( messagePanel, BoxLayout.Y_AXIS ) );

      messageLabel = new JLabel();

      detailsButton = new JButton( new AbstractAction( "Details...")
      {
         public void actionPerformed( ActionEvent e )
         {
            showDetails();
         }
      });

      //this panel centers the details button 
      JPanel centeringPanel = new JPanel();
      centeringPanel.setLayout( new BoxLayout( centeringPanel, BoxLayout.X_AXIS ) );
      centeringPanel.add( Box.createHorizontalGlue() );
      centeringPanel.add( detailsButton );
      centeringPanel.add( Box.createHorizontalGlue() );

      messagePanel.add( messageLabel, BorderLayout.CENTER );
      messagePanel.add( Box.createVerticalStrut( 10 ) );
      messagePanel.add( centeringPanel );

      messagePanel.setVisible( false );   //hidden by default.

      return messagePanel;
   }

   private void showDetails()
   {
      JTextArea detailsTextArea = new JTextArea( );

      detailsTextArea.setOpaque( true );
      detailsTextArea.setEditable( false );
      detailsTextArea.setBorder( null );

      detailsTextArea.setLineWrap( false );
      detailsTextArea.setWrapStyleWord( true );

      detailsTextArea.setText( message + "\n" + messageDetails );
      detailsTextArea.setCaretPosition( 0 ); //put the caret at the front.

      JScrollPane scrollPane = new JScrollPane( detailsTextArea );
      scrollPane.setPreferredSize( new Dimension( 400, 500 ) );

      JOptionPane.showMessageDialog( mainPanel, scrollPane );
   }

   public void setMessage( String message, String messageDetails )
   {
      this.messageDetails = messageDetails;
      this.message = message;

      messageLabel.setText( "<html><body>" + message + "</body></html>" );

      detailsButton.setVisible( messageDetails != null );   //only show the 'details' button if there are details

      messagePanel.setVisible( true );
   }

   public void hideMessage()
   {
      messagePanel.setVisible( false );
   }

   /**
      Call this before you show this panel. This is so we can initialize some
      things.
      @author mhunsicker
   */
   public void aboutToShow()
   {

   }
}
