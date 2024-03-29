/*
 * Copyright 2021 TiKV Project Authors.
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

package org.tikv.common.util;

import static org.tikv.common.key.Key.toRawKey;
import static org.tikv.common.util.KeyRangeUtils.formatByteString;
import static org.tikv.common.util.KeyRangeUtils.makeCoprocRange;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import java.io.Serializable;
import java.util.*;
import org.tikv.common.key.RowKey;
import org.tikv.common.pd.PDUtils;
import org.tikv.common.region.RegionManager;
import org.tikv.common.region.TiRegion;
import org.tikv.common.region.TiStore;
import org.tikv.common.region.TiStoreType;
import org.tikv.kvproto.Coprocessor.KeyRange;

public class RangeSplitter {
  private final RegionManager regionManager;

  private RangeSplitter(RegionManager regionManager) {
    this.regionManager = regionManager;
  }

  public static RangeSplitter newSplitter(RegionManager mgr) {
    return new RangeSplitter(mgr);
  }

  /**
   * Group by a list of handles by the handles' region, handles will be sorted.
   *
   * @param tableId Table id used for the handle
   * @param handles Handle list
   * @return {@code <Region, HandleList>} map
   */
  public Map<Pair<TiRegion, TiStore>, TLongArrayList> groupByAndSortHandlesByRegionId(
      long tableId, TLongArrayList handles) {
    TLongObjectHashMap<TLongArrayList> regionHandles = new TLongObjectHashMap<>();
    TLongObjectHashMap<Pair<TiRegion, TiStore>> idToRegionStorePair = new TLongObjectHashMap<>();
    Map<Pair<TiRegion, TiStore>, TLongArrayList> result = new HashMap<>();
    handles.sort();

    byte[] endKey = null;
    TiRegion curRegion = null;
    TLongArrayList handlesInCurRegion = new TLongArrayList();
    for (int i = 0; i < handles.size(); i++) {
      long curHandle = handles.get(i);
      RowKey key = RowKey.toRowKey(tableId, curHandle);
      if (endKey == null
          || (endKey.length != 0 && FastByteComparisons.compareTo(key.getBytes(), endKey) >= 0)) {
        if (curRegion != null) {
          regionHandles.put(curRegion.getId(), handlesInCurRegion);
          handlesInCurRegion = new TLongArrayList();
        }
        Pair<TiRegion, TiStore> regionStorePair =
            regionManager.getRegionStorePairByKey(ByteString.copyFrom(key.getBytes()));
        curRegion = regionStorePair.first;
        idToRegionStorePair.put(curRegion.getId(), regionStorePair);
        endKey = curRegion.getEndKey().toByteArray();
      }
      handlesInCurRegion.add(curHandle);
    }
    if (!handlesInCurRegion.isEmpty() && curRegion != null) {
      regionHandles.put(curRegion.getId(), handlesInCurRegion);
    }
    regionHandles.forEachEntry(
        (k, v) -> {
          Pair<TiRegion, TiStore> regionStorePair = idToRegionStorePair.get(k);
          result.put(regionStorePair, v);
          return true;
        });
    return result;
  }

  public List<RegionTask> splitAndSortHandlesByRegion(List<Long> ids, TLongArrayList handles) {
    Set<RegionTask> regionTasks = new HashSet<>();
    for (Long id : ids) {
      regionTasks.addAll(splitAndSortHandlesByRegion(id, handles));
    }
    return new ArrayList<>(regionTasks);
  }

  /**
   * Build region tasks from handles split by region, handles will be sorted.
   *
   * @param tableId Table ID
   * @param handles Handle list
   * @return A list of region tasks
   */
  private List<RegionTask> splitAndSortHandlesByRegion(long tableId, TLongArrayList handles) {
    // Max value for current index handle range
    ImmutableList.Builder<RegionTask> regionTasks = ImmutableList.builder();

    Map<Pair<TiRegion, TiStore>, TLongArrayList> regionHandlesMap =
        groupByAndSortHandlesByRegionId(tableId, handles);

    regionHandlesMap.forEach((k, v) -> createTask(0, v.size(), tableId, v, k, regionTasks));

    return regionTasks.build();
  }

  private void createTask(
      int startPos,
      int endPos,
      long tableId,
      TLongArrayList handles,
      Pair<TiRegion, TiStore> regionStorePair,
      ImmutableList.Builder<RegionTask> regionTasks) {
    List<KeyRange> newKeyRanges = new ArrayList<>(endPos - startPos + 1);
    long startHandle = handles.get(startPos);
    long endHandle = startHandle;
    for (int i = startPos + 1; i < endPos; i++) {
      long curHandle = handles.get(i);
      if (endHandle + 1 == curHandle) {
        endHandle = curHandle;
      } else {
        newKeyRanges.add(
            makeCoprocRange(
                RowKey.toRowKey(tableId, startHandle).toByteString(),
                RowKey.toRowKey(tableId, endHandle + 1).toByteString()));
        startHandle = curHandle;
        endHandle = startHandle;
      }
    }
    newKeyRanges.add(
        makeCoprocRange(
            RowKey.toRowKey(tableId, startHandle).toByteString(),
            RowKey.toRowKey(tableId, endHandle + 1).toByteString()));
    regionTasks.add(new RegionTask(regionStorePair.first, regionStorePair.second, newKeyRanges));
  }

  /**
   * Split key ranges into corresponding region tasks and group by their region id
   *
   * @param keyRanges List of key ranges
   * @param storeType Store type, null or TiKV for TiKV(leader), otherwise TiFlash(learner)
   * @return List of RegionTask, each task corresponds to a different region.
   */
  public List<RegionTask> splitRangeByRegion(List<KeyRange> keyRanges, TiStoreType storeType) {
    if (keyRanges == null || keyRanges.size() == 0) {
      return ImmutableList.of();
    }

    int i = 0;
    KeyRange range = keyRanges.get(i++);
    Map<Long, List<KeyRange>> idToRange = new HashMap<>(); // region id to keyRange list
    Map<Long, Pair<TiRegion, TiStore>> idToRegion = new HashMap<>();

    while (true) {
      Pair<TiRegion, TiStore> regionStorePair =
          regionManager.getRegionStorePairByKey(range.getStart(), storeType);

      if (regionStorePair == null) {
        throw new NullPointerException(
            "fail to get region/store pair by key " + formatByteString(range.getStart()));
      }
      TiRegion region = regionStorePair.first;
      idToRegion.putIfAbsent(region.getId(), regionStorePair);

      // both key range is close-opened
      // initial range inside PD is guaranteed to be -INF to +INF
      // Both keys are at right hand side and then always not -INF
      if (toRawKey(range.getEnd()).compareTo(toRawKey(region.getEndKey())) > 0) {
        // current region does not cover current end key
        KeyRange cutRange =
            KeyRange.newBuilder().setStart(range.getStart()).setEnd(region.getEndKey()).build();

        List<KeyRange> ranges = idToRange.computeIfAbsent(region.getId(), k -> new ArrayList<>());
        ranges.add(cutRange);

        // cut new remaining for current range
        range = KeyRange.newBuilder().setStart(region.getEndKey()).setEnd(range.getEnd()).build();
      } else {
        // current range covered by region
        List<KeyRange> ranges = idToRange.computeIfAbsent(region.getId(), k -> new ArrayList<>());
        ranges.add(range);
        if (i >= keyRanges.size()) {
          break;
        }
        range = keyRanges.get(i++);
      }
    }

    ImmutableList.Builder<RegionTask> resultBuilder = ImmutableList.builder();
    idToRange.forEach(
        (k, v) -> {
          Pair<TiRegion, TiStore> regionStorePair = idToRegion.get(k);
          resultBuilder.add(new RegionTask(regionStorePair.first, regionStorePair.second, v));
        });
    return resultBuilder.build();
  }

  /**
   * Split key ranges into corresponding region tasks and group by their region id
   *
   * @param keyRanges List of key ranges
   * @return List of RegionTask, each task corresponds to a different region.
   */
  public List<RegionTask> splitRangeByRegion(List<KeyRange> keyRanges) {
    return splitRangeByRegion(keyRanges, TiStoreType.TiKV);
  }

  public static class RegionTask implements Serializable {
    private final TiRegion region;
    private final TiStore store;
    private final List<KeyRange> ranges;
    private final String host;

    RegionTask(TiRegion region, TiStore store, List<KeyRange> ranges) {
      this.region = region;
      this.store = store;
      this.ranges = ranges;
      String host = null;
      try {
        host = PDUtils.addrToUri(store.getStore().getAddress()).getHost();
      } catch (Exception ignored) {
      }
      this.host = host;
    }

    public static RegionTask newInstance(TiRegion region, TiStore store, List<KeyRange> ranges) {
      return new RegionTask(region, store, ranges);
    }

    public TiRegion getRegion() {
      return region;
    }

    public TiStore getStore() {
      return store;
    }

    public List<KeyRange> getRanges() {
      return ranges;
    }

    public String getHost() {
      return host;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("Region [%s]", region));
      sb.append(" ");

      for (KeyRange range : ranges) {
        sb.append(
            String.format(
                "Range Start: [%s] Range End: [%s]",
                formatByteString(range.getStart()), formatByteString(range.getEnd())));
      }

      return sb.toString();
    }
  }
}
