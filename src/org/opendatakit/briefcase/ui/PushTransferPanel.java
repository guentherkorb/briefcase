/*
 * Copyright (C) 2011 University of Washington.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.briefcase.ui;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.opendatakit.briefcase.model.EndPointType;
import org.opendatakit.briefcase.model.FormStatus;
import org.opendatakit.briefcase.model.FormStatusEvent;
import org.opendatakit.briefcase.model.BriefcaseFormDefinition;
import org.opendatakit.briefcase.model.RetrieveAvailableFormsFailedEvent;
import org.opendatakit.briefcase.model.ServerConnectionInfo;
import org.opendatakit.briefcase.model.TerminationFuture;
import org.opendatakit.briefcase.model.TransferAbortEvent;
import org.opendatakit.briefcase.model.TransferFailedEvent;
import org.opendatakit.briefcase.model.TransferSucceededEvent;
import org.opendatakit.briefcase.model.UpdatedBriefcaseFormDefinitionEvent;
import org.opendatakit.briefcase.util.FileSystemUtils;
import org.opendatakit.briefcase.util.TransferAction;

/**
 * Push forms and data to external locations.
 * 
 * @author mitchellsundt@gmail.com
 *
 */
public class PushTransferPanel extends JPanel {

  /**
	 * 
	 */
  private static final long serialVersionUID = -2192404551259501394L;

  public static final String TAB_NAME = "Push";
  public static int TAB_POSITION = -1;

  private static final String UPLOADING_DOT_ETC = "Uploading..........";

  private JComboBox listDestinationDataSink;
  private JButton btnDestinationAction;
  private JLabel lblDestination;
  private JTextField txtDestinationName;
  private ServerConnectionInfo destinationServerInfo = null;

  private FormTransferTable formTransferTable;
  private JButton btnSelectOrClearAllForms;
  private JLabel lblUploading;
  private JButton btnTransfer;
  private JButton btnCancel;

  private boolean transferStateActive = false;
  private TerminationFuture terminationFuture;

  /**
   * UI changes related to the selection of the destination location from
   * drop-down box.
   */
  class DestinationSinkListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      JComboBox cb = (JComboBox) e.getSource();
      String strSelection = (String) cb.getSelectedItem();
      EndPointType selection = (strSelection != null) ? EndPointType.fromString(strSelection)
          : null;

      if (selection != null) {
        if (EndPointType.AGGREGATE_1_0_CHOICE.equals(selection)) {
          lblDestination.setText("URL:");
          txtDestinationName.setText("");
          txtDestinationName.setEditable(false);
          txtDestinationName.setVisible(true);
          btnDestinationAction.setText("Connect...");
          btnDestinationAction.setVisible(true);
        } else {
          throw new IllegalStateException("unexpected case");
        }
      }
    }
  }

  /**
   * Handle click-action for destination "Choose..." and "Connect..." button and
   * the related UI updates (e.g., populating the available forms list).
   */
  class DestinationActionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      String strSelection = (String) listDestinationDataSink.getSelectedItem();
      EndPointType selection = (strSelection != null) ? EndPointType.fromString(strSelection)
          : null;

      if (EndPointType.AGGREGATE_1_0_CHOICE.equals(selection)) {
        // need to show (modal) connect dialog...
        ServerConnectionDialog d = new ServerConnectionDialog(
            (Window) PushTransferPanel.this.getTopLevelAncestor(), destinationServerInfo, true);
        d.setVisible(true);
        if (d.isSuccessful()) {
          ServerConnectionInfo info = d.getServerInfo();
          if (info.isOpenRosaServer()) {
            destinationServerInfo = d.getServerInfo();
            txtDestinationName.setText(destinationServerInfo.getUrl());
          } else {
            JOptionPane.showMessageDialog(PushTransferPanel.this,
                "Server is not an ODK Aggregate 1.0 server", "Invalid Server URL",
                JOptionPane.ERROR_MESSAGE);
          }
        }
      } else {
        throw new IllegalStateException("unexpected case");
      }
    }

  }

  /**
   * Handle click-action for the "Transfer" button. Extracts the settings from
   * the UI and invokes the relevant TransferAction to actually do the work.
   */
  class TransferActionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {

      String strSelection;
      strSelection = (String) listDestinationDataSink.getSelectedItem();
      EndPointType destinationSelection = (strSelection != null) ? EndPointType
          .fromString(strSelection) : null;

      List<FormStatus> formsToTransfer = formTransferTable.getSelectedForms();
      // clear the transfer history...
      for (FormStatus fs : formsToTransfer) {
        fs.clearStatusHistory();
      }

      try {
        setActiveTransferState(true);
        if (EndPointType.AGGREGATE_1_0_CHOICE.equals(destinationSelection)) {
          TransferAction.transferBriefcaseToServer(
              destinationServerInfo, terminationFuture, formsToTransfer);
        } else {
          throw new IllegalStateException("unhandled case");
        }
      } catch (IOException ex) {
        JOptionPane.showMessageDialog(PushTransferPanel.this,
            "Briefcase action failed: " + ex.getMessage(), "Briefcase Action Failed",
            JOptionPane.ERROR_MESSAGE);
        setActiveTransferState(false);
      }
    }
  }

  /**
   * Create the transfer-from-to panel.
   * 
   * @param txtBriefcaseDir
   */
  public PushTransferPanel(TerminationFuture terminationFuture) {
    super();
    AnnotationProcessor.process(this);// if not using AOP
    this.terminationFuture = terminationFuture;

    JLabel lblSendDataTo = new JLabel(TAB_NAME + " data to:");

    listDestinationDataSink = new JComboBox(
        new String[] { EndPointType.AGGREGATE_1_0_CHOICE.toString() });

    listDestinationDataSink.addActionListener(new DestinationSinkListener());

    lblDestination = new JLabel("Destination:");

    txtDestinationName = new JTextField();
    txtDestinationName.setColumns(10);

    btnDestinationAction = new JButton("Choose...");
    btnDestinationAction.addActionListener(new DestinationActionListener());

    JLabel lblFormsToTransfer = new JLabel("Forms to " + TAB_NAME + ":");

    btnSelectOrClearAllForms = new JButton("Select all");

    lblUploading = new JLabel(UPLOADING_DOT_ETC);
    lblUploading.setForeground(lblUploading.getBackground());
    btnTransfer = new JButton(TAB_NAME);
    btnCancel = new JButton("Cancel");
    btnCancel.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent arg0) {
        PushTransferPanel.this.terminationFuture.markAsCancelled(
            new TransferAbortEvent(TAB_NAME + " cancelled by user."));
      }
    });

    formTransferTable = new FormTransferTable(btnSelectOrClearAllForms,
        FormStatus.TransferType.UPLOAD, btnTransfer);

    JScrollPane scrollPane = new JScrollPane(formTransferTable);

    JSeparator separatorFormsList = new JSeparator();

    GroupLayout groupLayout = new GroupLayout(this);
    groupLayout.setHorizontalGroup(groupLayout
        .createSequentialGroup()
        .addContainerGap()
        .addGroup(
            groupLayout
                .createParallelGroup(Alignment.LEADING)
                // get-data and origin rows
                .addGroup(
                    Alignment.LEADING,
                    // sequential -- 2 elements - label - widgets
                    groupLayout
                        .createSequentialGroup()
                        .addGroup(
                            groupLayout.createParallelGroup(Alignment.TRAILING)
                                .addComponent(lblDestination).addComponent(lblSendDataTo))
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addGroup(
                            groupLayout
                                .createParallelGroup(Alignment.LEADING)
                                .addComponent(listDestinationDataSink)
                                .addGroup(
                                    Alignment.TRAILING,
                                    groupLayout.createSequentialGroup()
                                        .addComponent(txtDestinationName)
                                        .addPreferredGap(ComponentPlacement.RELATED)
                                        .addComponent(btnDestinationAction))))
                .addComponent(separatorFormsList, GroupLayout.DEFAULT_SIZE,
                    GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
                .addComponent(lblFormsToTransfer)
                // scroll pane
                .addComponent(scrollPane, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
                    Short.MAX_VALUE)
                .addGroup(
                    groupLayout.createSequentialGroup().addComponent(btnSelectOrClearAllForms)
                        .addPreferredGap(ComponentPlacement.RELATED).addComponent(lblUploading))
                .addGroup(
                    Alignment.TRAILING,
                    groupLayout.createSequentialGroup().addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(btnTransfer).addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(btnCancel))).addContainerGap());
    groupLayout.setVerticalGroup(groupLayout
        .createSequentialGroup()
        .addContainerGap()
        .addGroup(
            groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lblSendDataTo)
                .addComponent(listDestinationDataSink))
        .addPreferredGap(ComponentPlacement.RELATED)
        .addGroup(
            groupLayout.createParallelGroup(Alignment.BASELINE).addComponent(lblDestination)
                .addComponent(txtDestinationName).addComponent(btnDestinationAction))
        .addPreferredGap(ComponentPlacement.RELATED)
        .addComponent(separatorFormsList, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
            GroupLayout.PREFERRED_SIZE)
        .addPreferredGap(ComponentPlacement.RELATED)
        .addComponent(lblFormsToTransfer)
        .addPreferredGap(ComponentPlacement.RELATED)
        .addComponent(scrollPane, 200, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
        .addPreferredGap(ComponentPlacement.RELATED)
        .addGroup(
            groupLayout.createParallelGroup(Alignment.BASELINE)
                .addComponent(btnSelectOrClearAllForms).addComponent(lblUploading)
                .addComponent(btnTransfer).addComponent(btnCancel)).addContainerGap());
    setLayout(groupLayout);
    listDestinationDataSink.setSelectedIndex(0);

    // set up the transfer action...
    btnTransfer.addActionListener(new TransferActionListener());
    // and update the list of forms...
    updateFormStatuses();
    setActiveTransferState(transferStateActive);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    Component[] com = this.getComponents();
    for (int a = 0; a < com.length; a++) {
      com[a].setEnabled(enabled);
    }
    if (enabled) {
      // and then update the widgets based upon the transfer state
      setActiveTransferState(transferStateActive);
    }
  }

  public void updateFormStatuses() {
    List<FormStatus> statuses = new ArrayList<FormStatus>();

    List<BriefcaseFormDefinition> forms = FileSystemUtils.getBriefcaseFormList();
    for (BriefcaseFormDefinition f : forms) {
      statuses.add(new FormStatus(FormStatus.TransferType.UPLOAD, f));
    }
    formTransferTable.setFormStatusList(statuses);
  }

  private void updateUploadingLabel() {
    String text = lblUploading.getText();
    if (text.equals(UPLOADING_DOT_ETC)) {
      text = "Uploading.";
    } else {
      text += ".";
    }
    lblUploading.setText(text);
  }

  private void setTabEnabled(boolean active) {
    JTabbedPane pane = (JTabbedPane) getParent();
    if ( pane != null ) {
      for ( int i = 0 ; i < pane.getTabCount() ; ++i ) {
        if ( i != TAB_POSITION ) {
          pane.setEnabledAt(i, active);
        }
      }
    }
  }

  private void setActiveTransferState(boolean active) {
    setTabEnabled(!active);
    if (active) {
      // don't allow normal actions when we are transferring...
      listDestinationDataSink.setEnabled(false);
      btnDestinationAction.setEnabled(false);
      btnSelectOrClearAllForms.setEnabled(false);
      btnTransfer.setEnabled(false);
      // enable cancel button
      btnCancel.setEnabled(true);
      // show downloading progress text
      lblUploading.setText(UPLOADING_DOT_ETC);
      lblUploading.setForeground(lblDestination.getForeground());
      // reset the termination future so we can cancel activity
      terminationFuture.reset();
    } else {
      // restore normal actions when we aren't transferring...
      listDestinationDataSink.setEnabled(true);
      btnDestinationAction.setEnabled(true);
      btnSelectOrClearAllForms.setEnabled(true);
      btnTransfer.setEnabled(true);
      // disable cancel button
      btnCancel.setEnabled(false);
      // hide downloading progress text (by setting foreground color to
      // background)
      lblUploading.setText(UPLOADING_DOT_ETC);
      lblUploading.setForeground(lblUploading.getBackground());
    }
    // remember state...
    transferStateActive = active;
  }

  @EventSubscriber(eventClass = TransferFailedEvent.class)
  public void failedCompletion(TransferFailedEvent event) {
    setActiveTransferState(false);
  }

  @EventSubscriber(eventClass = TransferSucceededEvent.class)
  public void successfulCompletion(TransferSucceededEvent event) {
    setActiveTransferState(false);
  }

  @EventSubscriber(eventClass = FormStatusEvent.class)
  public void updateDetailedStatus(FormStatusEvent fse) {
    updateUploadingLabel();
  }

  @EventSubscriber(eventClass = RetrieveAvailableFormsFailedEvent.class)
  public void formsAvailableFromServer(RetrieveAvailableFormsFailedEvent event) {
    JOptionPane.showMessageDialog(PushTransferPanel.this,
        "Accessing the server failed with error: " + event.getReason(), "Accessing Server Failed",
        JOptionPane.ERROR_MESSAGE);
  }

  @EventSubscriber(eventClass = UpdatedBriefcaseFormDefinitionEvent.class)
  public void briefcaseFormListChanges(UpdatedBriefcaseFormDefinitionEvent event) {
    updateFormStatuses();
  }
}
