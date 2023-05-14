/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.pipe.task.subtask;

import org.apache.iotdb.db.pipe.agent.PipeAgent;
import org.apache.iotdb.db.pipe.task.queue.ListenableBlockingPendingQueue;
import org.apache.iotdb.pipe.api.PipeConnector;
import org.apache.iotdb.pipe.api.event.Event;
import org.apache.iotdb.pipe.api.event.dml.deletion.DeletionEvent;
import org.apache.iotdb.pipe.api.event.dml.insertion.TabletInsertionEvent;
import org.apache.iotdb.pipe.api.event.dml.insertion.TsFileInsertionEvent;
import org.apache.iotdb.pipe.api.exception.PipeConnectionException;
import org.apache.iotdb.pipe.api.exception.PipeException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PipeConnectorSubtask extends PipeSubtask {

  private static final Logger LOGGER = LoggerFactory.getLogger(PipeConnectorSubtask.class);

  private final ListenableBlockingPendingQueue<Event> inputPendingQueue;
  private final PipeConnector outputPipeConnector;

  /** @param taskID connectorAttributeSortedString */
  public PipeConnectorSubtask(
      String taskID,
      ListenableBlockingPendingQueue<Event> inputPendingQueue,
      PipeConnector outputPipeConnector) {
    super(taskID);
    this.inputPendingQueue = inputPendingQueue;
    this.outputPipeConnector = outputPipeConnector;
  }

  // TODO: for a while
  @Override
  protected void executeForAWhile() {
    try {
      // TODO: reduce the frequency of heartbeat
      outputPipeConnector.heartbeat();
    } catch (Exception e) {
      throw new PipeConnectionException(
          "PipeConnector: failed to connect to the target system.", e);
    }

    final Event event = inputPendingQueue.poll();
    if (event == null) {
      return;
    }

    try {
      if (event instanceof TabletInsertionEvent) {
        outputPipeConnector.transfer((TabletInsertionEvent) event);
      } else if (event instanceof TsFileInsertionEvent) {
        outputPipeConnector.transfer((TsFileInsertionEvent) event);
      } else if (event instanceof DeletionEvent) {
        outputPipeConnector.transfer((DeletionEvent) event);
      } else {
        throw new RuntimeException("Unsupported event type: " + event.getClass().getName());
      }
    } catch (Exception e) {
      e.printStackTrace();
      throw new PipeException(
          "Error occurred during executing PipeConnector#transfer, perhaps need to check whether the implementation of PipeConnector is correct according to the pipe-api description.",
          e);
    }
  }

  @Override
  public void onFailure(@NotNull Throwable throwable) {
    // retry to connect to the target system if the connection is broken
    if (throwable instanceof PipeConnectionException) {
      int retry = 0;
      while (retry < MAX_RETRY_TIMES) {
        try {
          outputPipeConnector.handshake();
          break;
        } catch (Exception e) {
          retry++;
          LOGGER.error("Failed to reconnect to the target system, retrying... ({} time(s))", retry);
          try {
            // TODO: make the retry interval configurable
            Thread.sleep(retry * 1000L);
          } catch (InterruptedException interruptedException) {
            LOGGER.info(
                "Interrupted while sleeping, perhaps need to check whether the thread is interrupted.");
            Thread.currentThread().interrupt();
          }
        }
      }

      // stop current pipe task if failed to reconnect to the target system after MAX_RETRY_TIMES
      // times
      if (retry == MAX_RETRY_TIMES) {
        LOGGER.error(
            "Failed to reconnect to the target system after {} times, stopping current pipe task {}...",
            MAX_RETRY_TIMES,
            taskID);
        lastFailedCause = throwable;
        PipeAgent.runtime().report(this);
        return;
      }
    }

    // handle other exceptions as usual
    super.onFailure(throwable);
  }

  @Override
  public void close() {
    try {
      outputPipeConnector.close();
    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.info(
          "Error occurred during closing PipeConnector, perhaps need to check whether the implementation of PipeConnector is correct according to the pipe-api description.",
          e);
    }
  }
}