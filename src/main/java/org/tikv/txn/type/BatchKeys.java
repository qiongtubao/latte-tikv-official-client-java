/*
 * Copyright 2017 TiKV Project Authors.
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

package org.tikv.txn.type;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import org.tikv.common.region.TiRegion;
import org.tikv.common.region.TiStore;

public class BatchKeys {
  private final TiRegion region;
  private final TiStore store;
  private List<ByteString> keys;
  private final int sizeInBytes;

  public BatchKeys(TiRegion region, TiStore store, List<ByteString> keysInput, int sizeInBytes) {
    this.region = region;
    this.store = store;
    this.keys = new ArrayList<>();
    this.keys.addAll(keysInput);
    this.sizeInBytes = sizeInBytes;
  }

  public List<ByteString> getKeys() {
    return keys;
  }

  public void setKeys(List<ByteString> keys) {
    this.keys = keys;
  }

  public TiRegion getRegion() {
    return region;
  }

  public TiStore getStore() {
    return store;
  }

  public int getSizeInBytes() {
    return sizeInBytes;
  }

  public float getSizeInKB() {
    return ((float) sizeInBytes) / 1024;
  }
}
