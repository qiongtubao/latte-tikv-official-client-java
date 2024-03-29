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

package org.tikv.util;

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.tikv.common.MockRegionManager;
import org.tikv.common.codec.Codec.IntegerCodec;
import org.tikv.common.codec.CodecDataOutput;
import org.tikv.common.key.RowKey;
import org.tikv.common.key.RowKey.DecodeResult.Status;
import org.tikv.common.util.RangeSplitter;
import org.tikv.kvproto.Coprocessor.KeyRange;

public class RangeSplitterTest {

  private static KeyRange keyRange(Long s, Long e) {
    ByteString sKey = ByteString.EMPTY;
    ByteString eKey = ByteString.EMPTY;
    if (s != null) {
      CodecDataOutput cdo = new CodecDataOutput();
      IntegerCodec.writeLongFully(cdo, s, true);
      sKey = cdo.toByteString();
    }

    if (e != null) {
      CodecDataOutput cdo = new CodecDataOutput();
      IntegerCodec.writeLongFully(cdo, e, true);
      eKey = cdo.toByteString();
    }

    return KeyRange.newBuilder().setStart(sKey).setEnd(eKey).build();
  }

  private static KeyRange keyRangeByHandle(long tableId, Long s, Long e) {
    return keyRangeByHandle(tableId, s, Status.EQUAL, e, Status.EQUAL);
  }

  private static KeyRange keyRangeByHandle(long tableId, Long s, Status ss, Long e, Status es) {
    ByteString sKey = shiftByStatus(handleToByteString(tableId, s), ss);
    ByteString eKey = shiftByStatus(handleToByteString(tableId, e), es);

    return KeyRange.newBuilder().setStart(sKey).setEnd(eKey).build();
  }

  private static ByteString shiftByStatus(ByteString v, Status s) {
    switch (s) {
      case EQUAL:
        return v;
      case LESS:
        return v.substring(0, v.size() - 1);
      case GREATER:
        return v.concat(ByteString.copyFrom(new byte[] {1, 0}));
      default:
        throw new IllegalArgumentException("Only EQUAL,LESS,GREATER allowed");
    }
  }

  private static ByteString handleToByteString(long tableId, Long k) {
    if (k != null) {
      return RowKey.toRowKey(tableId, k).toByteString();
    }
    return ByteString.EMPTY;
  }

  @Test
  public void splitRangeByRegionTest() {
    MockRegionManager mgr =
        new MockRegionManager(
            ImmutableList.of(keyRange(null, 30L), keyRange(30L, 50L), keyRange(50L, null)));
    RangeSplitter s = RangeSplitter.newSplitter(mgr);
    List<RangeSplitter.RegionTask> tasks =
        s.splitRangeByRegion(
            ImmutableList.of(
                keyRange(0L, 40L), keyRange(41L, 42L), keyRange(45L, 50L), keyRange(70L, 1000L)));

    assertEquals(tasks.get(0).getRegion().getId(), 0);
    assertEquals(tasks.get(0).getRanges().size(), 1);
    KeyRange range = tasks.get(0).getRanges().get(0);
    assertEquals(tasks.get(0).getRanges().get(0), keyRange(0L, 30L));

    assertEquals(tasks.get(1).getRegion().getId(), 1);
    assertEquals(tasks.get(1).getRanges().get(0), keyRange(30L, 40L));
    assertEquals(tasks.get(1).getRanges().get(1), keyRange(41L, 42L));
    assertEquals(tasks.get(1).getRanges().get(2), keyRange(45L, 50L));
    assertEquals(tasks.get(1).getRanges().size(), 3);

    assertEquals(tasks.get(2).getRegion().getId(), 2);
    assertEquals(tasks.get(2).getRanges().size(), 1);
    assertEquals(tasks.get(2).getRanges().get(0), keyRange(70L, 1000L));
  }

  @Test
  public void splitAndSortHandlesByRegionTest() {
    final long tableId = 1;
    List<Long> handles = new ArrayList<>();
    handles.add(1L);
    handles.add(5L);
    handles.add(4L);
    handles.add(3L);
    handles.add(10L);
    handles.add(2L);
    handles.add(100L);
    handles.add(101L);
    handles.add(99L);
    handles.add(88L);
    handles.add(-1L);
    handles.add(-255L);
    handles.add(-100L);
    handles.add(-99L);
    handles.add(-98L);
    handles.add(Long.MIN_VALUE);
    handles.add(8960L);
    handles.add(8959L);
    handles.add(19999L);
    handles.add(15001L);

    MockRegionManager mgr =
        new MockRegionManager(
            ImmutableList.of(
                keyRangeByHandle(tableId, null, Status.EQUAL, -100L, Status.EQUAL),
                keyRangeByHandle(tableId, -100L, Status.EQUAL, 10L, Status.GREATER),
                keyRangeByHandle(tableId, 10L, Status.GREATER, 50L, Status.EQUAL),
                keyRangeByHandle(tableId, 50L, Status.EQUAL, 100L, Status.GREATER),
                keyRangeByHandle(tableId, 100L, Status.GREATER, 9000L, Status.LESS),
                keyRangeByHandle(tableId, 0x2300L /*8960*/, Status.LESS, 16000L, Status.EQUAL),
                keyRangeByHandle(tableId, 16000L, Status.EQUAL, null, Status.EQUAL)));

    RangeSplitter s = RangeSplitter.newSplitter(mgr);
    List<RangeSplitter.RegionTask> tasks =
        new ArrayList<>(
            s.splitAndSortHandlesByRegion(
                ImmutableList.of(tableId),
                new TLongArrayList(handles.stream().mapToLong(t -> t).toArray())));
    tasks.sort(
        (l, r) -> {
          Long regionIdLeft = l.getRegion().getId();
          Long regionIdRight = r.getRegion().getId();
          return regionIdLeft.compareTo(regionIdRight);
        });

    // [-INF, -100): [Long.MIN_VALUE, Long.MIN_VALUE + 1), [-255, -254)
    assertEquals(tasks.get(0).getRegion().getId(), 0);
    assertEquals(tasks.get(0).getRanges().size(), 2);
    assertEquals(
        tasks.get(0).getRanges().get(0),
        keyRangeByHandle(tableId, Long.MIN_VALUE, Long.MIN_VALUE + 1));
    assertEquals(tasks.get(0).getRanges().get(1), keyRangeByHandle(tableId, -255L, -254L));

    // [-100, 10.x): [-100, -97), [-1, 0), [1, 6), [10, 11)
    assertEquals(tasks.get(1).getRegion().getId(), 1);
    assertEquals(tasks.get(1).getRanges().size(), 4);
    assertEquals(tasks.get(1).getRanges().get(0), keyRangeByHandle(tableId, -100L, -97L));
    assertEquals(tasks.get(1).getRanges().get(1), keyRangeByHandle(tableId, -1L, 0L));
    assertEquals(tasks.get(1).getRanges().get(2), keyRangeByHandle(tableId, 1L, 6L));
    assertEquals(tasks.get(1).getRanges().get(3), keyRangeByHandle(tableId, 10L, 11L));

    // [10.x, 50): empty
    // [50, 100.x): [88, 89) [99, 101)
    assertEquals(tasks.get(2).getRegion().getId(), 3);
    assertEquals(tasks.get(2).getRanges().size(), 2);
    assertEquals(tasks.get(2).getRanges().get(0), keyRangeByHandle(tableId, 88L, 89L));
    assertEquals(tasks.get(2).getRanges().get(1), keyRangeByHandle(tableId, 99L, 101L));

    // [100.x, less than 8960): [101, 102) [8959, 8960)
    assertEquals(tasks.get(3).getRegion().getId(), 4);
    assertEquals(tasks.get(3).getRanges().size(), 2);
    assertEquals(tasks.get(3).getRanges().get(0), keyRangeByHandle(tableId, 101L, 102L));
    assertEquals(tasks.get(3).getRanges().get(1), keyRangeByHandle(tableId, 8959L, 8960L));

    // [less than 8960, 16000): [9000, 9001), [15001, 15002)
    assertEquals(tasks.get(4).getRegion().getId(), 5);
    assertEquals(tasks.get(4).getRanges().size(), 2);
    assertEquals(tasks.get(4).getRanges().get(0), keyRangeByHandle(tableId, 8960L, 8961L));
    assertEquals(tasks.get(4).getRanges().get(1), keyRangeByHandle(tableId, 15001L, 15002L));

    // [16000, INF): [19999, 20000)
    assertEquals(tasks.get(5).getRegion().getId(), 6);
    assertEquals(tasks.get(5).getRanges().size(), 1);
    assertEquals(tasks.get(5).getRanges().get(0), keyRangeByHandle(tableId, 19999L, 20000L));
  }

  @Test
  public void groupByAndSortHandlesByRegionIdTest() {
    final long tableId = 1;
    List<Long> handles = new ArrayList<>();
    handles.add(1L);
    handles.add(5L);
    handles.add(4L);
    handles.add(3L);
    handles.add(10L);
    handles.add(11L);
    handles.add(12L);
    handles.add(2L);
    handles.add(100L);
    handles.add(101L);
    handles.add(99L);
    handles.add(88L);
    handles.add(-1L);
    handles.add(-255L);
    handles.add(-100L);
    handles.add(-99L);
    handles.add(-98L);
    handles.add(Long.MIN_VALUE);
    handles.add(8960L);
    handles.add(8959L);
    handles.add(19999L);
    handles.add(15001L);
    handles.add(99999999999L);
    handles.add(Long.MAX_VALUE);

    MockRegionManager mgr =
        new MockRegionManager(
            ImmutableList.of(
                keyRangeByHandle(tableId, null, Status.EQUAL, -100L, Status.EQUAL),
                keyRangeByHandle(tableId, -100L, Status.EQUAL, 10L, Status.GREATER),
                keyRangeByHandle(tableId, 10L, Status.GREATER, 50L, Status.EQUAL),
                keyRangeByHandle(tableId, 50L, Status.EQUAL, 100L, Status.GREATER),
                keyRangeByHandle(tableId, 100L, Status.GREATER, 9000L, Status.LESS),
                keyRangeByHandle(tableId, 0x2300L /*8960*/, Status.LESS, 16000L, Status.EQUAL),
                keyRangeByHandle(tableId, 16000L, Status.EQUAL, null, Status.EQUAL)));

    TLongObjectHashMap<TLongArrayList> result = new TLongObjectHashMap<>();
    RangeSplitter.newSplitter(mgr)
        .groupByAndSortHandlesByRegionId(
            tableId, new TLongArrayList(handles.stream().mapToLong(t -> t).toArray()))
        .forEach((k, v) -> result.put(k.first.getId(), v));
    assertEquals(2, result.get(0).size());
    assertEquals(10, result.get(1).size());
    assertEquals(2, result.get(2).size());
    assertEquals(3, result.get(3).size());
    assertEquals(2, result.get(4).size());
    assertEquals(2, result.get(5).size());
    assertEquals(3, result.get(6).size());
  }
}
