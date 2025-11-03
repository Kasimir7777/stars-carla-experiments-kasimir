/*
 * Copyright 2025 The STARS Carla Experiments Authors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tools.aqua.stars.carla.experiments

import java.io.File
import kotlinx.serialization.json.Json
import tools.aqua.stars.core.metric.serialization.SerializableFailedMonitorsResult
import tools.aqua.stars.core.metric.serialization.SerializableResult
import tools.aqua.stars.core.metric.serialization.SerializableTSCOccurrenceResult
import tools.aqua.stars.core.metric.serialization.tsc.SerializableTSCNode

fun main() {

    val pathMonitors = "serialized-results/100v50w_with_walkers_ignore_lights100/failed-monitors/layer 1+2+4.json"
    val pathTsc = "serialized-results/100v50w_with_walkers_ignore_lights100/valid-tsc-instances-per-tsc/layer 1+2+4.json"
  val failedMonitorsResult: SerializableFailedMonitorsResult =
      loadSerializableResult(pathMonitors)
  val failedMonitors =
      failedMonitorsResult.value.map { result ->
        Triple(result.segmentIdentifier, result.monitorLabel, result.tscInstance)
      }

  val validInstancesResult: SerializableTSCOccurrenceResult =
      loadSerializableResult(
          pathTsc)

  val validInstances =
      validInstancesResult.value.flatMap { result ->
        result.segmentIdentifiers.map { identifier -> identifier to result.tscInstance }
      }

  val parsed =
      validInstances.map { (key, value) ->
        // Extract the first float inside "Segment[(...s.."
        val segment = key.substringAfter("Segment[(").substringBefore("s").toFloat()

        // Extract the "from ..." part and get map name and seed
        val fromFull = key.substringAfter("from ").substringBefore(" with")
        val from = fromFull.substringAfter("Maps_").substringBefore("_seed")
        val seed = fromFull.substringAfter("_seed").toIntOrNull() ?: 0

        // Extract entity
        val entity = key.substringAfter("entity ").substringBefore(" ")

        SegmentIdentifier(segment, from, seed, entity, key) to value
      }

  val sorted = parsed.sortedBy { it.first }

  val seenNodes = mutableSetOf<SerializableTSCNode>()

  val sortedInstancesWithUniqueCounts =
      sorted.mapIndexed { index, (segmentIdentifier, tscInstance) ->
        seenNodes.add(tscInstance)
        TSCInstanceAndMonitorResultsWithCount(
            segmentIdentifier,
            tscInstance,
            emptyList(),
            index + 1,
            seenNodes.size,
            0,
            0,
            possibleTSCInstances = 360,
            possibleFailedMonitors = 15)
      }

  var failedMonitorsCount = 0
  val withMonitors =
      sortedInstancesWithUniqueCounts.map { tscInstanceAndMonitorResultsWithCount ->
        val failedMonitorsForIdentifier =
            failedMonitors.filter { (monitorSegmentIdentifier, _, monitorTscInstance) ->
              tscInstanceAndMonitorResultsWithCount.segmentIdentifier.originalSegmentIdentifier ==
                  monitorSegmentIdentifier &&
                  tscInstanceAndMonitorResultsWithCount.tscInstance == monitorTscInstance
            }
        failedMonitorsCount += failedMonitorsForIdentifier.size
        tscInstanceAndMonitorResultsWithCount.failedMonitors =
            failedMonitorsForIdentifier.map { it.second }
        tscInstanceAndMonitorResultsWithCount.failedMonitorsCount = failedMonitorsCount
      }

  val seenMonitors = mutableSetOf<String>()
  sortedInstancesWithUniqueCounts.forEach { tscInstanceAndMonitorResultsWithCount ->
    seenMonitors.addAll(tscInstanceAndMonitorResultsWithCount.failedMonitors)
    tscInstanceAndMonitorResultsWithCount.uniqueFailedMonitorsCount = seenMonitors.size
  }

  writeResultsToCsv(sortedInstancesWithUniqueCounts, "serialized-results/monitors_and_instances.csv")

  val s = ""
}

fun writeResultsToCsv(results: List<TSCInstanceAndMonitorResultsWithCount>, outputPath: String) {
  val file = File(outputPath)

    val slicedResults = results.filterIndexed({index, _ -> index % 100 == 0 || index == 0 || index == results.size -1})


  // CSV header
  file.printWriter().use { out ->
    out.println(
        "tscInstanceCount,tscInstanceCountInPercent,failedMonitorsCountInPercent")

    // Each row
    slicedResults.forEach { result ->
      out.println(
          listOf(
                  result.tscInstanceCount,
                  (result.uniqueTSCInstancesCount.toFloat() / result.possibleTSCInstances * 100)
                      .toInt(),
                  (result.uniqueFailedMonitorsCount.toFloat() / result.possibleFailedMonitors * 100)
                      .toInt(),
              )
              .joinToString(","))
    }
  }

  println("✅ CSV written to: ${file.absolutePath}")
}

val json = Json {
  prettyPrint = true
  ignoreUnknownKeys = true
}

inline fun <reified T : SerializableResult> loadSerializableResult(filePath: String): T {
  val jsonString = File(filePath).readText()
  return json.decodeFromString<T>(jsonString)
}

data class SegmentIdentifier(
    val segment: Float,
    val from: String,
    val seed: Int,
    val entity: String,
    val originalSegmentIdentifier: String
) : Comparable<SegmentIdentifier> {
  override fun compareTo(other: SegmentIdentifier): Int {
    return compareValuesBy(
        this,
        other,
        SegmentIdentifier::seed,
        SegmentIdentifier::segment,
        SegmentIdentifier::entity,
        SegmentIdentifier::from)
  }
}

data class TSCInstanceAndMonitorResultsWithCount(
    val segmentIdentifier: SegmentIdentifier,
    val tscInstance: SerializableTSCNode,
    var failedMonitors: List<String>,
    val tscInstanceCount: Int,
    val uniqueTSCInstancesCount: Int,
    var uniqueFailedMonitorsCount: Int,
    var failedMonitorsCount: Int,
    val possibleTSCInstances: Int,
    val possibleFailedMonitors: Int
)
