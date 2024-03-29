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

package org.tikv.common.expression;

import static org.tikv.common.types.MySQLType.*;

import java.util.HashMap;
import java.util.Map;
import org.tikv.common.types.MySQLType;

public class TypeBlocklist extends Blocklist {
  private static final Map<MySQLType, String> typeToMySQLMap = initialTypeMap();

  public TypeBlocklist(String typesString) {
    super(typesString);
  }

  private static HashMap<MySQLType, String> initialTypeMap() {
    HashMap<MySQLType, String> map = new HashMap<>();
    map.put(TypeDecimal, "decimal");
    map.put(TypeTiny, "tinyint");
    map.put(TypeShort, "smallint");
    map.put(TypeLong, "int");
    map.put(TypeFloat, "float");
    map.put(TypeDouble, "double");
    map.put(TypeNull, "null");
    map.put(TypeTimestamp, "timestamp");
    map.put(TypeLonglong, "bigint");
    map.put(TypeInt24, "mediumint");
    map.put(TypeDate, "date");
    map.put(TypeDuration, "time");
    map.put(TypeDatetime, "datetime");
    map.put(TypeYear, "year");
    map.put(TypeNewDate, "date");
    map.put(TypeVarchar, "varchar");
    map.put(TypeJSON, "json");
    map.put(TypeNewDecimal, "decimal");
    map.put(TypeEnum, "enum");
    map.put(TypeSet, "set");
    map.put(TypeTinyBlob, "tinytext");
    map.put(TypeMediumBlob, "mediumtext");
    map.put(TypeLongBlob, "longtext");
    map.put(TypeBlob, "text");
    map.put(TypeVarString, "varString");
    map.put(TypeString, "string");
    return map;
  }

  public boolean isUnsupportedType(MySQLType sqlType) {
    return isUnsupported(typeToMySQLMap.getOrDefault(sqlType, ""));
  }
}
