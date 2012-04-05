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

package org.opendatakit.briefcase.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JOptionPane;

import org.bouncycastle.openssl.PEMReader;
import org.bushe.swing.event.EventBus;
import org.opendatakit.briefcase.model.ExportFailedEvent;
import org.opendatakit.briefcase.model.ExportSucceededEvent;
import org.opendatakit.briefcase.model.ExportType;
import org.opendatakit.briefcase.model.LocalFormDefinition;
import org.opendatakit.briefcase.model.TerminationFuture;

public class ExportAction {

  static final String SCRATCH_DIR = "scratch";
  static final String UTF_8 = "UTF-8";

  private static ExecutorService backgroundExecutorService = Executors.newCachedThreadPool();

  private static class TransformFormRunnable implements Runnable {
    ITransformFormAction action;

    TransformFormRunnable(ITransformFormAction action) {
      this.action = action;
    }

    @Override
    public void run() {
      try {
        boolean allSuccessful = true;
        allSuccessful = action.doAction();
        if (allSuccessful) {
          EventBus.publish(new ExportSucceededEvent(action.getFormDefinition()));
        } else {
          EventBus.publish(new ExportFailedEvent(action.getFormDefinition()));
        }
      } catch (Exception e) {
        e.printStackTrace();
        EventBus.publish(new ExportFailedEvent(action.getFormDefinition()));
      }
    }

  }

  private static void backgroundRun(ITransformFormAction action) {
    backgroundExecutorService.execute(new TransformFormRunnable(action));
  }

  public static void export(
      File outputDir, ExportType outputType, LocalFormDefinition lfd, File pemFile,
      TerminationFuture terminationFuture) throws IOException {

    if (lfd.isFileEncryptedForm() || lfd.isFieldEncryptedForm()) {

      boolean success = false;
      while (!success) {
        try {
          BufferedReader br = new BufferedReader(new FileReader(pemFile));
          Object o = new PEMReader(br).readObject();
          if ( o == null ) {
            JOptionPane.showMessageDialog(null, 
                "The supplied file is not in PEM format.",
                "Invalid RSA Private Key", JOptionPane.ERROR_MESSAGE);
            continue;
          }
          PrivateKey privKey;
          if ( o instanceof KeyPair ) {
            KeyPair kp = (KeyPair) o;
            privKey = kp.getPrivate();
          } else if ( o instanceof PrivateKey ) {
            privKey = (PrivateKey) o;
          } else {
            privKey = null;
          }
          if ( privKey == null ) {
            JOptionPane.showMessageDialog(null, 
                "The supplied file does not contain a private key.",
                "Invalid RSA Private Key", JOptionPane.ERROR_MESSAGE);
            continue;
          }
          lfd.setPrivateKey(privKey);
          success = true;
        } catch (IOException e) {
          e.printStackTrace();
          JOptionPane.showMessageDialog(null, 
              "The supplied PEM file could not be parsed.",
              "Invalid RSA Private Key", JOptionPane.ERROR_MESSAGE);
          continue;
        }
      }
    }

    ITransformFormAction action;
    if (outputType == ExportType.CSV) {
      action = new ExportToCsv(outputDir, lfd, terminationFuture);
    } else {
      throw new IllegalStateException("outputType not recognized");
    }

    backgroundRun(action);
  }
}
