/*
 * Copyright 2020 TiKV Project Authors.
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

package org.tikv.txn;

import org.tikv.kvproto.Kvrpcpb;

// ttl > 0: lock is not resolved
//
// <p>ttl = 0 && commitTS = 0: lock is deleted
//
// <p>ttl = 0 && commitTS > 0: lock is committed
public class TxnStatus {
  private long ttl;
  private long commitTS;
  private Kvrpcpb.Action action;

  public TxnStatus() {
    this.ttl = 0L;
    this.commitTS = 0L;
    this.action = Kvrpcpb.Action.UNRECOGNIZED;
  }

  public TxnStatus(long ttl) {
    this.ttl = ttl;
    this.commitTS = 0L;
    this.action = Kvrpcpb.Action.UNRECOGNIZED;
  }

  public TxnStatus(long ttl, long commitTS) {
    this.ttl = ttl;
    this.commitTS = commitTS;
    this.action = Kvrpcpb.Action.UNRECOGNIZED;
  }

  public TxnStatus(long ttl, long commitTS, Kvrpcpb.Action action) {
    this.ttl = ttl;
    this.commitTS = commitTS;
    this.action = action;
  }

  public long getTtl() {
    return ttl;
  }

  public void setTtl(long ttl) {
    this.ttl = ttl;
  }

  public long getCommitTS() {
    return commitTS;
  }

  public void setCommitTS(long commitTS) {
    this.commitTS = commitTS;
  }

  public boolean isCommitted() {
    return ttl == 0 && commitTS > 0;
  }

  public Kvrpcpb.Action getAction() {
    return action;
  }

  public void setAction(Kvrpcpb.Action action) {
    this.action = action;
  }
}
