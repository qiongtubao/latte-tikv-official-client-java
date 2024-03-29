/*
 * Copyright 2022 TiKV Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.tikv.common;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Test;
import org.tikv.common.KVMockServer.State;
import org.tikv.raw.RawKVClient;

public class SeekProxyStoreTest extends MockThreeStoresTest {
  private RawKVClient createClient() {
    return session.createRawClient();
  }

  @Test
  public void testSeekProxyStore() {
    RawKVClient client = createClient();
    ByteString key = ByteString.copyFromUtf8("key");
    ByteString value = ByteString.copyFromUtf8("value");
    put(key, value);

    client.put(key, value);
    Assert.assertEquals(value, client.get(key).get());
    // Set the leader to state Fail, the request will route to peer 0x2, which is not the leader.
    // The state of three peers is the same.
    // Thus, with the correct context, the peer 0x2 will return normally.
    servers.get(0).setState(State.Fail);

    Assert.assertEquals(value, client.get(key).get());
  }
}
