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

package org.tikv.common.apiversion;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.stream.Collectors;
import org.tikv.common.util.Pair;
import org.tikv.kvproto.Kvrpcpb.KvPair;
import org.tikv.kvproto.Kvrpcpb.Mutation;
import org.tikv.kvproto.Metapb;
import org.tikv.kvproto.Pdpb;

public interface RequestKeyCodec {
  ByteString encodeKey(ByteString key);

  default List<ByteString> encodeKeys(List<ByteString> keys) {
    return keys.stream().map(this::encodeKey).collect(Collectors.toList());
  }

  default List<Mutation> encodeMutations(List<Mutation> mutations) {
    return mutations
        .stream()
        .map(mut -> Mutation.newBuilder().mergeFrom(mut).setKey(encodeKey(mut.getKey())).build())
        .collect(Collectors.toList());
  }

  ByteString decodeKey(ByteString key);

  default KvPair decodeKvPair(KvPair pair) {
    return KvPair.newBuilder().mergeFrom(pair).setKey(decodeKey(pair.getKey())).build();
  }

  default List<KvPair> decodeKvPairs(List<KvPair> pairs) {
    return pairs.stream().map(this::decodeKvPair).collect(Collectors.toList());
  }

  Pair<ByteString, ByteString> encodeRange(ByteString start, ByteString end);

  ByteString encodePdQuery(ByteString key);

  Pair<ByteString, ByteString> encodePdQueryRange(ByteString start, ByteString end);

  Metapb.Region decodeRegion(Metapb.Region region);

  default List<Pdpb.Region> decodePdRegions(List<Pdpb.Region> regions) {
    return regions
        .stream()
        .map(
            r ->
                Pdpb.Region.newBuilder()
                    .mergeFrom(r)
                    .setRegion(this.decodeRegion(r.getRegion()))
                    .build())
        .collect(Collectors.toList());
  }
}
