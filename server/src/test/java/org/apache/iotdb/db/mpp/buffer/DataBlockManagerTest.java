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

package org.apache.iotdb.db.mpp.buffer;

import org.apache.iotdb.common.rpc.thrift.TEndPoint;
import org.apache.iotdb.commons.client.IClientManager;
import org.apache.iotdb.commons.client.sync.SyncDataNodeDataBlockServiceClient;
import org.apache.iotdb.db.client.DataNodeClientPoolFactory;
import org.apache.iotdb.db.mpp.execution.FragmentInstanceContext;
import org.apache.iotdb.db.mpp.memory.LocalMemoryManager;
import org.apache.iotdb.db.mpp.memory.MemoryPool;
import org.apache.iotdb.mpp.rpc.thrift.TFragmentInstanceId;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Executors;

public class DataBlockManagerTest {
  @Test
  public void testCreateLocalSinkHandle() {
    final TFragmentInstanceId localFragmentInstanceId = new TFragmentInstanceId("q0", 1, "0");
    final TFragmentInstanceId remoteFragmentInstanceId = new TFragmentInstanceId("q0", 0, "0");
    final String remotePlanNodeId = "exchange_0";
    final FragmentInstanceContext mockFragmentInstanceContext =
        Mockito.mock(FragmentInstanceContext.class);

    // Construct a mock LocalMemoryManager with capacity 5 * mockTsBlockSize per query.
    LocalMemoryManager mockLocalMemoryManager = Mockito.mock(LocalMemoryManager.class);
    MemoryPool spyMemoryPool = Mockito.spy(new MemoryPool("test", 10240L, 5120L));
    Mockito.when(mockLocalMemoryManager.getQueryPool()).thenReturn(spyMemoryPool);

    DataBlockManager dataBlockManager =
        new DataBlockManager(
            mockLocalMemoryManager,
            new TsBlockSerdeFactory(),
            Executors.newSingleThreadExecutor(),
            new IClientManager.Factory<TEndPoint, SyncDataNodeDataBlockServiceClient>()
                .createClientManager(
                    new DataNodeClientPoolFactory.SyncDataNodeDataBlockServiceClientPoolFactory()));

    ISinkHandle localSinkHandle =
        dataBlockManager.createLocalSinkHandle(
            localFragmentInstanceId,
            remoteFragmentInstanceId,
            remotePlanNodeId,
            mockFragmentInstanceContext);

    Assert.assertTrue(localSinkHandle instanceof LocalSinkHandle);

    ISourceHandle localSourceHandle =
        dataBlockManager.createLocalSourceHandle(
            remoteFragmentInstanceId, remotePlanNodeId, localFragmentInstanceId, t -> {});

    Assert.assertTrue(localSourceHandle instanceof LocalSourceHandle);

    Assert.assertEquals(
        ((LocalSinkHandle) localSinkHandle).getSharedTsBlockQueue(),
        ((LocalSourceHandle) localSourceHandle).getSharedTsBlockQueue());
  }

  @Test
  public void testCreateLocalSourceHandle() {
    final TFragmentInstanceId remoteFragmentInstanceId = new TFragmentInstanceId("q0", 1, "0");
    final TFragmentInstanceId localFragmentInstanceId = new TFragmentInstanceId("q0", 0, "0");
    final String localPlanNodeId = "exchange_0";
    final FragmentInstanceContext mockFragmentInstanceContext =
        Mockito.mock(FragmentInstanceContext.class);

    // Construct a mock LocalMemoryManager with capacity 5 * mockTsBlockSize per query.
    LocalMemoryManager mockLocalMemoryManager = Mockito.mock(LocalMemoryManager.class);
    MemoryPool spyMemoryPool = Mockito.spy(new MemoryPool("test", 10240L, 5120L));
    Mockito.when(mockLocalMemoryManager.getQueryPool()).thenReturn(spyMemoryPool);

    DataBlockManager dataBlockManager =
        new DataBlockManager(
            mockLocalMemoryManager,
            new TsBlockSerdeFactory(),
            Executors.newSingleThreadExecutor(),
            new IClientManager.Factory<TEndPoint, SyncDataNodeDataBlockServiceClient>()
                .createClientManager(
                    new DataNodeClientPoolFactory.SyncDataNodeDataBlockServiceClientPoolFactory()));

    ISourceHandle localSourceHandle =
        dataBlockManager.createLocalSourceHandle(
            localFragmentInstanceId, localPlanNodeId, remoteFragmentInstanceId, t -> {});

    Assert.assertTrue(localSourceHandle instanceof LocalSourceHandle);

    ISinkHandle localSinkHandle =
        dataBlockManager.createLocalSinkHandle(
            remoteFragmentInstanceId,
            localFragmentInstanceId,
            localPlanNodeId,
            mockFragmentInstanceContext);

    Assert.assertTrue(localSinkHandle instanceof LocalSinkHandle);

    Assert.assertEquals(
        ((LocalSinkHandle) localSinkHandle).getSharedTsBlockQueue(),
        ((LocalSourceHandle) localSourceHandle).getSharedTsBlockQueue());
  }
}
