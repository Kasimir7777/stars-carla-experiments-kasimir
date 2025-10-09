/*
 * Copyright 2023-2025 The STARS Carla Experiments Authors
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

import kotlin.math.abs
import kotlin.math.sign
import tools.aqua.stars.core.evaluation.BinaryPredicate.Companion.predicate
import tools.aqua.stars.core.evaluation.PredicateContext
import tools.aqua.stars.core.evaluation.UnaryPredicate
import tools.aqua.stars.core.evaluation.UnaryPredicate.Companion.predicate
import tools.aqua.stars.data.av.dataclasses.*
import tools.aqua.stars.logic.kcmftbl.*
import kotlin.math.*

// region predicates/formulas

/** The [Block] of [Vehicle] v has less than 6 vehicles in it. */
val hasLowTrafficDensity =
    predicate("hasLowTrafficDensity", Vehicle::class) { ctx, v ->
      !(hasMidTrafficDensity.holds(ctx, v) || hasHighTrafficDensity.holds(ctx, v))
    }

/** The [Block] of [Vehicle] v has between 6 and 15 vehicles in it. */
val hasMidTrafficDensity =
    predicate("hasMidTrafficDensity", Vehicle::class) { _, v ->
      minPrevalence(v, 0.6) { v -> v.tickData.vehiclesInBlock(v.lane.road.block).size in 6..15 }
    }

/** The [Block] of [Vehicle] v has more than 15 vehicles in it. */
val hasHighTrafficDensity =
    predicate("hasHighTrafficDensity", Vehicle::class) { _, v ->
      minPrevalence(v, 0.6) { v -> v.tickData.vehiclesInBlock(v.lane.road.block).size > 15 }
    }

/** [Vehicle] v changes its lange at least once. */
val changedLane =
    predicate("changedLane", Vehicle::class) { _, v ->
      eventually(v) { v0 ->
        eventually(v0) { v1 -> v0.lane.road == v1.lane.road && v0.lane != v1.lane }
      }
    }

/** The [Vehicle]s v0 and v1 are on the same [Road]. */
val onSameRoad =
    predicate("onSameRoad", Vehicle::class to Vehicle::class) { _, v0, v1 -> v0.lane.road == v1.lane.road }

/** [Vehicle] v0 is on the same road as [Vehicle] v1 but drives on the other direction. */
val oncoming =
    predicate("oncoming", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      eventually(v0, v1) { v0, v1 ->
        onSameRoad.holds(ctx, v0, v1) && v0.lane.laneId.sign != v1.lane.laneId.sign
      }
    }

/** [Vehicle] v is mostly in a junction. */
val isInJunction =
    predicate("isInJunction", Vehicle::class) { _, v -> minPrevalence(v, 0.8) { v -> v.lane.road.isJunction } }

/** [Vehicle] v is mostly on a single lane. */
val isOnSingleLane =
    predicate("isOnSingleLane", Vehicle::class) { ctx, v ->
      !isInJunction.holds(ctx, v) &&
          minPrevalence(v, 0.8) {
            v.lane.road.lanes.filter { v.lane.laneId.sign == it.laneId.sign }.size == 1
          }
    }

/** [Vehicle] v is mostly on a multi-lane. */
val isOnMultiLane =
    predicate("isOnMultiLane", Vehicle::class) { ctx, v ->
      !isInJunction.holds(ctx, v) && !isOnSingleLane.holds(ctx, v)
    }

typealias ExperimentPredicateContext =
    PredicateContext<Actor, TickData, Segment, TickDataUnitSeconds, TickDataDifferenceSeconds>

/** The daytime was mostly [Daytime.Sunset]. */
fun ExperimentPredicateContext.sunset(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d -> d.daytime == Daytime.Sunset }

/** The daytime was mostly [Daytime.Noon]. */
fun ExperimentPredicateContext.noon(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d -> d.daytime == Daytime.Noon }

/** The weather was mostly [WeatherType.Clear]. */
fun ExperimentPredicateContext.weatherClear(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d -> d.weather.type == WeatherType.Clear }

/** The weather was mostly [WeatherType.Cloudy]. */
fun ExperimentPredicateContext.weatherCloudy(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d -> d.weather.type == WeatherType.Cloudy }

/** The weather was mostly [WeatherType.Wet]. */
fun ExperimentPredicateContext.weatherWet(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d -> d.weather.type == WeatherType.Wet }

/** The weather was mostly [WeatherType.WetCloudy]. */
fun ExperimentPredicateContext.weatherWetCloudy(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d ->
      d.weather.type == WeatherType.WetCloudy
    }

/** The weather was mostly [WeatherType.SoftRainy]. */
fun ExperimentPredicateContext.weatherSoftRain(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d ->
      d.weather.type == WeatherType.SoftRainy
    }

/** The weather was mostly [WeatherType.MidRainy]. */
fun ExperimentPredicateContext.weatherMidRain(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d ->
      d.weather.type == WeatherType.MidRainy
    }

/** The weather was mostly [WeatherType.HardRainy]. */
fun ExperimentPredicateContext.weatherHardRain(): Boolean =
    minPrevalence(this.segment.tickData.first(), 0.6) { d ->
      d.weather.type == WeatherType.HardRainy
    }

/** There is a [Vehicle] between the two [Vehicle]s v0 and v1. */
val soBetween =
    predicate("soBetween", Vehicle::class to Vehicle::class) { _, v0, v1 ->
      v1.tickData.vehicles
          .filter { it.id != v0.id && it.id != v1.id }
          .any { vx ->
            (v0.lane.uid == vx.lane.uid || v1.lane.uid == vx.lane.uid) &&
                (!(v0.lane.uid == vx.lane.uid) || (v0.positionOnLane < vx.positionOnLane)) &&
                (!(v1.lane.uid == vx.lane.uid) || (v1.positionOnLane > vx.positionOnLane))
          }
    }

/** [Vehicle] v0 is behind [Vehicle] v1 on the same [Lane]. */
val behind =
    predicate("behind", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        /*if (v0.id == 47 && v1.id == 53) {
            println("v0: " + v0.id + " v1: " + v1.id + " behind stuff: v0 lane id: " + v0.lane.uid + " v1 lane id: " + v1.lane.uid + " v0 position: " + v0.positionOnLane + " v1 position on lane: " + v1.positionOnLane)
            println("result: " + (((v0.lane.uid == v1.lane.uid && v0.positionOnLane < v1.positionOnLane) ||
                    v0.lane.successorLanes.any { it.lane.uid == v1.lane.uid }) &&
                    !soBetween.holds(ctx, v0, v1)))
        }*/

      ((v0.lane.uid == v1.lane.uid && v0.positionOnLane < v1.positionOnLane) ||
          v0.lane.successorLanes.any { it.lane.uid == v1.lane.uid }) &&
          !soBetween.holds(ctx, v0, v1)
    }

/** [Vehicle] v0 follows [Vehicle] v1 for at least 30 seconds. */
val follows =
    predicate("follows", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      eventually(v0, v1) { v0, v1 ->
        globally(v0, v1, TickDataDifferenceSeconds(0.0) to TickDataDifferenceSeconds(30.0)) { v0, v1
          ->
          behind.holds(ctx, v0, v1)
        } &&
            eventually(
                v0, v1, TickDataDifferenceSeconds(30.0) to TickDataDifferenceSeconds(31.0)) { _, _
                  ->
                  true
                }
      }
    }

/** There is a speed limit of 90mph. */
val mphLimit90 =
    predicate("mphLimit90", Vehicle::class) { _, v ->
      eventually(v) { v -> v.lane.speedAt(v.positionOnLane) == 90.0 }
    }

/** There is a speed limit of 60mph. */
val mphLimit60 =
    predicate("mphLimit60", Vehicle::class) { _, v ->
      eventually(v) { v -> v.lane.speedAt(v.positionOnLane) == 60.0 }
    }

/** There is a speed limit of 30mph. */
val mphLimit30 =
    predicate("mphLimit30", Vehicle::class) { _, v ->
      eventually(v) { v -> v.lane.speedAt(v.positionOnLane) == 30.0 }
    }

/** [Actor] a0 and [Actor] a1 are on the same lane. */
val onSameLane = predicate("onSameLane", Actor::class to Actor::class) { _, a1, a2 -> a1.lane.uid == a2.lane.uid }

/**
 * pedestrian p is on the same lane es vehicle, v and v is driving towards p with a distance of < 10
 * meters.
 */
val inReach =
    predicate("inReach", Pedestrian::class to Vehicle::class) { ctx, p, v ->
      onSameLane.holds(ctx, p, v) && (p.positionOnLane - v.positionOnLane) in 0.0..10.0
    }

/**
 * true if at any one time stamp in the future there exists a pedestrian that crosses the lane right
 * before v.
 */
val pedestrianCrossed =
    predicate("pedestrianCrossed", Vehicle::class) { ctx, v ->
      eventually(v) { v -> v.tickData.pedestrians.any { p -> inReach.holds(ctx, p, v) } }
    }

/** v0/v1 driving on the same road into the same direction. */
val sameDirection =
    predicate("sameDirection", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      onSameRoad.holds(ctx, v0, v1) && v0.lane.laneId.sign == v1.lane.laneId.sign
    }

/** v0/v1 driving in the same direction on the same road with position on lane diff max 2.0 m. */
val besides =
    predicate("besides", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      sameDirection.holds(ctx, v0, v1) && abs(v1.positionOnLane - v0.positionOnLane) <= 2.0
    }

/** v0/v1 driving in the same direction on the same road with position on lane diff max 2.0 m. */
val besidesAnyDirection =
    predicate("besides", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        onSameRoad.holds(ctx, v0, v1) && abs(v1.positionOnLane - v0.positionOnLane) <= 2.0
    }

/** v0/v1 driving in the same direction on the same road with v0 more than 2.0 m behind v1. */
val isBehind =
    predicate("isBehind", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      sameDirection.holds(ctx, v0, v1) && (v0.positionOnLane + 2.0) < v1.positionOnLane
    }

/** v0/v1 driving in the same direction on the same road with v0 more than 2.0 m behind v1. */
val isBehindAnyDirection =
    predicate("isBehind", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        onSameRoad.holds(ctx, v0, v1) && (v0.positionOnLane + 2.0) < v1.positionOnLane
    }

/** v0/v1 driving at speeds over 10 mph. */
val bothOver10MPH =
    predicate("bothOver10MPH", Vehicle::class to Vehicle::class) { _, v0, v1 ->
      v0.effVelocityInMPH > 10 && v1.effVelocityInMPH > 10
        //true
    }

/**
 * v0 is behind v1 then besides v1 and then in front of v1. Both vehicles move with more than 10 mph
 */
val overtaking =
    predicate("overtaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      eventually(v0, v1) { v0, v1 ->
        isBehind.holds(ctx, v0, v1) &&
            bothOver10MPH.holds(ctx, v0, v1) &&
            next(v0, v1) { v0, v1 ->
              until(
                  v0,
                  v1,
                  phi1 = { v0, v1 ->
                    isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                  },
                  phi2 = { v0, v1 ->
                    besides.holds(ctx, v0, v1) &&
                        bothOver10MPH.holds(ctx, v0, v1) &&
                        next(v0, v1) { v0, v1 ->
                          until(
                              v0,
                              v1,
                              phi1 = { v0, v1 ->
                                besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                              },
                              phi2 = { v0, v1 ->
                                isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                              })
                        }
                  })
            }
      }
    }

/** [Vehicle] v was overtaking by at least one other [Vehicle]. */
val hasOvertaken =
    predicate("hasOvertaken", Vehicle::class) { ctx, v ->
      v.tickData.vehicles.any { v1 -> overtaking.holds(ctx, v, v1) }
    }

/** [Vehicle] v0 is on a right [Lane] of [Vehicle] v1. */
val rightOf =
    predicate("rightOf", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      besides.holds(ctx, v0, v1) && abs(v0.lane.laneId) > abs(v1.lane.laneId)
    }

/** [Vehicle] v0 overtook [Vehicle] v1 on the right. */
val rightOvertaking =
    predicate("rightOvertaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      eventually(v0, v1) { v0, v1 ->
        isBehind.holds(ctx, v0, v1) &&
            bothOver10MPH.holds(ctx, v0, v1) &&
            next(v0, v1) { v0, v1 ->
              until(
                  v0,
                  v1,
                  phi1 = { v0, v1 ->
                    isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                  },
                  phi2 = { v0, v1 ->
                    rightOf.holds(ctx, v0, v1) &&
                        bothOver10MPH.holds(ctx, v0, v1) &&
                        next(v0, v1) { v0, v1 ->
                          until(
                              v0,
                              v1,
                              phi1 = { v0, v1 ->
                                rightOf.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                              },
                              phi2 = { v0, v1 ->
                                isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                              })
                        }
                  })
            }
      }
    }

/** [Vehicle] v has not overtaken another [Vehicle] on the right. */
val noRightOvertaking =
    predicate("noRightOvertaking", Vehicle::class) { ctx, v ->
      v.tickData.vehicles.all { v1 -> !rightOvertaking.holds(ctx, v, v1) }
    }

/** [Vehicle] v has stopped. */
val stopped = predicate("stopped", Vehicle::class) { _, v -> v.effVelocityInMPH < 1.8 }

/** [Vehicle] v has stopped at the end of its [Road]. */
val stopAtEnd =
    predicate("stopAtEnd", Vehicle::class) { ctx, v ->
      eventually(v) { v1 -> isAtEndOfRoad.holds(ctx, v1) && stopped.holds(ctx, v1) }
    }

/** [Vehicle] v0 has passed the contact point of the crossing [Lane] of [Vehicle] v1. */
val passedContactPoint =
    predicate("passedContactPoint", Vehicle::class to Vehicle::class) { _, v0, v1 ->
      v0.lane.contactPointPos(v1.lane)?.let { it < v0.positionOnLane } == true
    }

/** [Vehicle] v0 has yielded to [Vehicle] v1. */
val hasYielded =
    predicate("hasYielded", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
      until(
          v0,
          v1,
          phi1 = { v0, v1 -> !passedContactPoint.holds(ctx, v0, v1) },
          phi2 = { v0, v1 -> passedContactPoint.holds(ctx, v1, v0) })
    }

/** [Vehicle] v always had a speed lower than the allowed speed limit. */
val obeyedSpeedLimit =
    predicate("obeyedSpeedLimit", Vehicle::class) { _, v ->
      globally(v) { v -> (v.effVelocityInMPH) <= v.lane.speedAt(v.positionOnLane) }
    }

val obeyed110SpeedLimit =
    predicate("obeyed110SpeedLimit", Vehicle::class) { _, v ->
        globally(v) { v -> (v.effVelocityInMPH) <= v.lane.speedAt(v.positionOnLane) * 1.1 }
    }

val obeyed130SpeedLimit =
    predicate("obeyed130SpeedLimit", Vehicle::class) { _, v ->
        globally(v) { v -> (v.effVelocityInMPH) <= v.lane.speedAt(v.positionOnLane) * 1.3 }
    }

val obeyed150SpeedLimit =
    predicate("obeyed150SpeedLimit", Vehicle::class) { _, v ->
        globally(v) { v -> (v.effVelocityInMPH) <= v.lane.speedAt(v.positionOnLane) * 1.5 }
    }

/** [Vehicle] v has a red light on its [Lane]. */
val hasRedLight =
    predicate("hasRedLight", Vehicle::class) { _, v ->
        //println("tick: " + v.tickData.currentTick + " lane: + " + v.lane.toString() + " traffic lights: " + v.lane.trafficLights.map { light -> "light id: " + light.id + " state: " + light.getStateInTick(v.tickData) })
        v.lane.trafficLights.any { staticTrafficLight ->
            staticTrafficLight.getStateInTick(v.tickData) == TrafficLightState.Red
        }
      /*v.lane.successorLanes.any { contactLaneInfo ->
          println("tick: " + v.tickData.currentTick + " lane: + " + contactLaneInfo.toString() + " traffic lights: " + contactLaneInfo.lane.trafficLights.map { light -> "light id: " + light.id + " state: " + light.getStateInTick(v.tickData) })
        contactLaneInfo.lane.trafficLights.any { staticTrafficLight ->
          staticTrafficLight.getStateInTick(v.tickData) == TrafficLightState.Red
        }
      }*/
    }

/** [Vehicle] v has a red light on its [Lane] and v is close to the traffic light. */
val hasRelevantRedLight =
    predicate("hasRelevantRedLight", Vehicle::class) { ctx, v ->
      eventually(v) { v -> hasRedLight.holds(ctx, v) && isAtEndOfRoad.holds(ctx, v) }
    }

/** [Vehicle] v has crossed a red light. */
val didCrossRedLight =
    predicate("didCrossRedLight", Vehicle::class) { ctx, v ->
      eventually(v) { v1 ->
          //println("tick: " + v1.tickData.currentTick + " successor lanes: " + v1.lane.successorLanes.toString() + " has red light: " + hasRedLight.holds(ctx, v1) + " has rel redlight: " + hasRelevantRedLight.holds(ctx, v1) + " end of road: " + isAtEndOfRoad.holds(ctx, v1) + " effvelocity in  kmph: " + v.effVelocityInKmPH)
          //println("result: " + (hasRelevantRedLight.holds(ctx, v1) && isAtEndOfRoad.holds(ctx, v1) && v.effVelocityInKmPH >= 5))
          //hasRelevantRedLight.holds(ctx, v1) && next(v1) { v2 -> v1.lane.road != v2.lane.road }
          hasRelevantRedLight.holds(ctx, v1) && isAtEndOfRoad.holds(ctx, v1) && v.effVelocityInKmPH >= 5
      }
    }

/** [Vehicle] v is located in the last 3 meters of its [Lane]. */
val isAtEndOfRoad =
    predicate("isAtEndOfRoad", Vehicle::class) { _, v -> v.positionOnLane >= v.lane.laneLength - 3.0 }

/** [Vehicle] v has a stop sign at the end of its [Lane]. */
val hasStopSign = predicate("hasStopSign", Vehicle::class) { _, v -> eventually(v) { v -> v.lane.hasStopSign } }

/** [Vehicle] v has a yield sign at the end of its [Lane]. */
val hasYieldSign = predicate("hasYieldSign", Vehicle::class) { _, v -> eventually(v) { v -> v.lane.hasYieldSign } }

/** [Vehicle] v0 must yield to [Vehicle] v1. */
val mustYield =
    predicate("mustYield", Vehicle::class to Vehicle::class) { _, v0, v1 ->
      eventually(v0, v1) { v0, v1 -> v0.lane.yieldLanes.any { it.lane == v1.lane } }
    }

/** [Vehicle] v made a right turn. */
val makesRightTurn =
    predicate("makesRightTurn", Vehicle::class) { _, v -> minPrevalence(v, 0.8) { v -> v.lane.isTurningRight } }

/** [Vehicle] v made a left turn. */
val makesLeftTurn =
    predicate("makesLeftTurn", Vehicle::class) { _, v -> minPrevalence(v, 0.8) { v -> v.lane.isTurningLeft } }

/** [Vehicle] v made no turn. */
val makesNoTurn =
    predicate("makesNoTurn", Vehicle::class) { _, v -> minPrevalence(v, 0.8) { v -> v.lane.isStraight } }

fun distanceToLaneCenter(v:Vehicle):Double {
    val laneMidpoint = v.lane.laneMidpoints.find {it.distanceToStart == v.positionOnLane}
    if (laneMidpoint != null) {
        val locationOfCenter = laneMidpoint.location;
        val locationOfVehicle = v.location;
        return sqrt((locationOfCenter.x - locationOfVehicle.x) * (locationOfCenter.x - locationOfVehicle.x) + (locationOfCenter.y - locationOfVehicle.y) * (locationOfCenter.y - locationOfVehicle.y))
    }
    throw RuntimeException("could not find laneMidpoint to calculate distanceToLaneCenter")
}


/**
 * In the city: 1 sec of driving at the current speed
 */
fun minDistanceToLeadingVehicle(v:Vehicle):Double {
    return v.effVelocityInMPerS;
}


fun distanceBetweenTwoLocations(l0:Location, l1:Location):Double {
    val distance = sqrt((l0.x - l1.x).pow(2) + (l0.y - l1.y).pow(2));
    return distance;
}

/** [Vehicle] v0 and [Vehicle] v1 collided */
val noCollisions =
    predicate("noCollisions", Vehicle::class to Vehicle::class) {ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            lateralDistance(v0, v1) > 0 || longitudinalDistance(v0, v1) > 0;
        }
    }

/** [Vehicle] v0 and [Vehicle] v1 collided */
val collision =
    predicate("collisions", Vehicle::class to Vehicle::class) {ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            /*println("tick: " + v0.tickData.currentTick + " lateral distance: " + lateralDistance(v0, v1) + " longitudinal distance: " + longitudinalDistance(v0, v1))
            println("result: " + (lateralDistance(v0, v1) <= 0 && longitudinalDistance(v0, v1) <= 0))*/
            lateralDistance(v0, v1) <= 0 && longitudinalDistance(v0, v1) <= 0;
        }
    }

fun lateralDistance(v0:Vehicle, v1:Vehicle):Double {
    val laneMidpoint = v0.lane.laneMidpoints.find { it.distanceToStart == v0.positionOnLane }
    if (laneMidpoint != null) {
        // calculate orthogonal vector to the street. Basically use rotation Matrix and then scalar product
        val streetYaw = laneMidpoint.rotation.yaw;
        val orthogonalStreetX = cos(2 * PI * streetYaw / 360 + PI/2);
        val orthogonalStreetY = sin(2 * PI * streetYaw / 360 + PI/2);
        // Projection of distance between the two cars on the lateral axis (so orthogonal to the street)
        val distanceBetweenCarsX = v0.location.x - v1.location.x;
        val distanceBetweenCarsY = v0.location.y - v1.location.y;
        val lateralDistanceCarCenter =
            abs((orthogonalStreetX * distanceBetweenCarsX + orthogonalStreetY * distanceBetweenCarsY) / sqrt(
                orthogonalStreetX * orthogonalStreetX + orthogonalStreetY * orthogonalStreetY
            ));
        //TODO: use bounding box to subtract exact widths of cars instead of average
        return abs((lateralDistanceCarCenter - 1.8).coerceAtLeast(0.0))
    }
    return Double.MAX_VALUE
}

fun longitudinalDistance(v0:Vehicle, v1:Vehicle):Double {
    val laneMidpoint = v0.lane.laneMidpoints.find { it.distanceToStart == v0.positionOnLane }
    if (laneMidpoint != null) {
        // vector parallel to the street.
        val streetYaw = laneMidpoint.rotation.yaw;
        val streetX = cos(2 * PI * streetYaw / 360);
        val streetY = sin(2 * PI * streetYaw / 360);
        // Projection of distance between the two cars on the longitudinal axis (so parallel to the street)
        val distanceBetweenCarsX = v0.location.x - v1.location.x;
        val distanceBetweenCarsY = v0.location.y - v1.location.y;
        val longitudinalDistanceCarCenter =
            abs((streetX * distanceBetweenCarsX + streetY * distanceBetweenCarsY) / sqrt(
                streetX * streetX + streetY * streetY
            ));
        //TODO: use bounding box to subtract exact length of cars instead of average
        return abs((longitudinalDistanceCarCenter - 5).coerceAtLeast(0.0))
    }
    throw RuntimeException("could not find laneMidpoint to calculate distanceToLaneCenter")
}

/**
 * if a car is overtaking another car, the minimal lateral distance must be kept.
 */
val keepsLateralDistanceWhileOvertaking =
    predicate("Keeps lateral distance while overtaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            !overtaking.holds(ctx, v0, v1) || enoughLateralDistance.holds(ctx, v0, v1)
        }
    }

/**
 * if a car is overtaking another car, the minimal lateral distance must be kept.
 */
/*val lateralDistanceWhileOvertakingTooSmall =
    predicate("Lateral distance while overtaking is too small", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            if (hasOvertaken.holds(ctx, v0)) {
                val v0at0: Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v0.id } as Vehicle
                val v1at0: Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v1.id } as Vehicle
                *//*if (v0.id == 138 && v1.id == 150) {
                    val v0at0 = ctx.segment.tickData.first().entities.find { e -> e.id == v0.id }
                    val v1at0 = ctx.segment.tickData.first().entities.find { e -> e.id == v1.id }
                    if (v0at0 is Vehicle && v1at0 is Vehicle) {
                        println("tick: " + v0.tickData.currentTick + " v0 and speed: " + v0.id + " " + v0.effVelocityInMPH + " v1: " + v1.id + " " + v0.effVelocityInMPH + " is during overtaking: " + isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) + " overtaking: " + overtaking.holds(ctx, v0, v1) + " has overtaken: " + hasOvertaken.holds(ctx, v0) + " directly besides: " + directlyBesides(v0, v1) + " longitudinal distance: " + longitudinalDistance(v0, v1) + " lateral distance < 1: " + (lateralDistance(v0, v1) < 1))
                        println("result: " + (isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && directlyBesides(v0, v1) && lateralDistance(v0, v1) < 1))
                    }

                }*//*
                isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && directlyBesides(
                    v0,
                    v1
                ) && lateralDistance(v0, v1) < 1
            }
            false
        }
    }*/

val lateralDistanceWhileOvertakingTooSmall =
    predicate("Lateral distance while overtaking is too small", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            isBehind.holds(ctx, v0, v1) &&
                    bothOver10MPH.holds(ctx, v0, v1) &&
                    next(v0, v1) { v0, v1 ->
                        until(
                            v0,
                            v1,
                            phi1 = { v0, v1 ->
                                isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                            },
                            phi2 = { v0, v1 ->
                                besides.holds(ctx, v0, v1) &&
                                        bothOver10MPH.holds(ctx, v0, v1) &&
                                        next(v0, v1) { v0, v1 ->
                                            until(
                                                v0,
                                                v1,
                                                phi1 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                },
                                                phi2 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1) && lateralDistance(v0, v1) < 1 &&
                                                    next(v0, v1) { v0, v1 ->
                                                        until(
                                                            v0,
                                                            v1,
                                                            phi1 = { v0, v1 ->
                                                                besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                            },
                                                            phi2 = { v0, v1 ->
                                                                isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                                                            }
                                                        )
                                                    }
                                                }
                                            )
                                        }
                            })
                    }
        }
    }

val obeysKeepRightRule =
    predicate("obeysKeepRightRule", Vehicle::class) { ctx, v ->
        globally(v) { v ->
            val drivingLanes = v.lane.road.lanes.filter { lane -> lane.laneType == LaneType.Driving }
            val absRightLaneId = if (v.lane.laneId < 0) abs(drivingLanes.map { l -> l.laneId }.min()) else abs(drivingLanes.map { l -> l.laneId }.max())
            v.tickData.vehicles.any { v1 -> overtaking.holds(ctx, v, v1) } || makesLeftTurn.holds(ctx, v) ||  abs(v.lane.laneId) >= absRightLaneId
        }
    }

val breaksKeepRightRule =
    predicate("breaksKeepRightRule", Vehicle::class) { ctx, v ->
        v.tickData.vehicles.none { v1 -> overtaking.holds(ctx, v, v1) } && !makesLeftTurn.holds(ctx, v) &&
        eventually(v) { v ->
            val drivingLanes = v.lane.road.lanes.filter { lane -> lane.laneType == LaneType.Driving }
            val absRightLaneId = if (v.lane.laneId < 0) abs(drivingLanes.map { l -> l.laneId }.min()) else abs(drivingLanes.map { l -> l.laneId }.max())
            abs(v.lane.laneId) < absRightLaneId
        }
    }


val drivesAtCenterOfLane =
    predicate("drivesAtCenterOfLane", Vehicle::class) { ctx, v ->
        globally(v) {
            v0 ->
            /*println("changed lane holds: " + changedLane.holds(ctx, v0) + " distance to lane center: " + distanceToLaneCenter(v0) + " distanceToLaneCenter(v) <= v0.lane.laneWidth / 4 " + (distanceToLaneCenter(v0) <= v.lane.laneWidth / 4) + " erlaubte toleranz: " + (v0.lane.laneWidth / 4))
            println("result: " + (changedLane.holds(ctx, v0) || distanceToLaneCenter(v) <= v0.lane.laneWidth / 4))*/
            changedLane.holds(ctx, v0) || distanceToLaneCenter(v) <= v0.lane.laneWidth / 4}
    }

val distanceToLeadingVehicleTooSmall =
    predicate("distanceToLeadingVehicleTooSmall", Vehicle::class) { ctx, v ->
        eventually(v) {
            v.tickData.vehicles.any { v1 ->
                if (behind.holds(ctx, v, v1)) longitudinalDistance(v, v1) < minDistanceToLeadingVehicle(v)
                else false
            }
        }
    }

val keepsDistanceToLeadingVehicle =
    predicate("keepsDistanceToLeadingVehicle", Vehicle::class) { ctx, v ->
        globally(v) {
            v.tickData.vehicles.all { v1 ->
                if (behind.holds(ctx, v, v1)) longitudinalDistance(v, v1) >= minDistanceToLeadingVehicle(v)
                else true
            }
        }
    }

val enoughLateralDistance =
    predicate("enoughLateralDistance", Vehicle::class to Vehicle::class) {ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            if (v0.id == 138 && v1.id == 150) {
                println("tick: " + v0.tickData.currentTick + " lateral distance: " + lateralDistance(v0, v1) + " longitudinal distance: " + longitudinalDistance(v0, v1))
            }

            lateralDistance(v0, v1) >= 1;
        }
    }

fun directlyBesides(v0: Vehicle, v1: Vehicle):Boolean {
    return v0.lane.road.id == v1.lane.road.id && longitudinalDistance(v0, v1) <= 0
}


fun isDuringOvertaking(tick:TickDataUnitSeconds, v0:Vehicle, v1:Vehicle, ctx:PredicateContext<Actor, TickData, Segment, TickDataUnitSeconds, TickDataDifferenceSeconds>) : Boolean {
    var start = v0.tickData.currentTick;
    var end = v0.tickData.currentTick;

    if (eventually(v0, v1) { v0, v1 ->
            isBehind.holds(ctx, v0, v1) &&
                    bothOver10MPH.holds(ctx, v0, v1) &&
                    next(v0, v1) { v0, v1 ->
                        until(
                            v0,
                            v1,
                            phi1 = { v0, v1 ->
                                directlyBesides(v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                            },
                            phi2 = { v0, v1 ->
                                start = v0.tickData.currentTick;
                                directlyBesides(v0, v1) &&
                                        bothOver10MPH.holds(ctx, v0, v1) &&
                                        next(v0, v1) { v0, v1 ->
                                            until(
                                                v0,
                                                v1,
                                                phi1 = { v0, v1 ->
                                                    directlyBesides(v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                },
                                                phi2 = { v0, v1 ->
                                                    end = v0.tickData.currentTick;
                                                    isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                                                })
                                        }
                            })
                    }

        }) {
        return start.tickSeconds < tick.tickSeconds && tick.tickSeconds < end.tickSeconds;
    } else {
        return false;
    }
}

val overtakingWithSpeedDifference =
    predicate("overtaking with speed difference", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            !isDuringOvertaking(v0.tickData.currentTick, v0, v1, ctx) || (v0.effVelocityInKmPH - v1.effVelocityInKmPH >= 10)
        }
    }

/*val overtakingWithLowSpeedDifference =
    predicate("overtaking with low speed difference", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            //for performance reason only do this if hasOvertaken holds
            if (hasOvertaken.holds(ctx, v0)) {
                val v0at0 : Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v0.id } as Vehicle
                val v1at0 : Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v1.id } as Vehicle
                *//*if (v0.id == 138 && v1.id == 144) {
                    println("tick: " + v0.tickData.currentTick + " v0: " + v0.id + " " + v0.effVelocityInMPH + " v1: " + v1.id + " " + v1.effVelocityInMPH + " has overtaken: " + hasOvertaken.holds(ctx, v0) + " during overtaking: " + isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) + " speed difference: " + abs(v0.effVelocityInKmPH - v1.effVelocityInKmPH))
                    println("is behind: " + isBehind.holds(ctx, v0, v1) + " besides: " + besides.holds(ctx, v0, v1) + " in front: " + isBehind.holds(ctx, v1, v0) + " both over 10mph: " + bothOver10MPH.holds(ctx, v0, v1))
                    println("result: " + (isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && (abs(v0.effVelocityInKmPH - v1.effVelocityInKmPH) < 10)))
                }*//*
                isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && (abs(v0.effVelocityInKmPH - v1.effVelocityInKmPH) < 10)
            }
            false
        }
    }*/

val overtakingWithLowSpeedDifference =
    predicate("overtaking with low speed difference", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            isBehind.holds(ctx, v0, v1) &&
                    bothOver10MPH.holds(ctx, v0, v1) &&
                    next(v0, v1) { v0, v1 ->
                        until(
                            v0,
                            v1,
                            phi1 = { v0, v1 ->
                                isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                            },
                            phi2 = { v0, v1 ->
                                besides.holds(ctx, v0, v1) &&
                                        bothOver10MPH.holds(ctx, v0, v1) &&
                                        next(v0, v1) { v0, v1 ->
                                            until(
                                                v0,
                                                v1,
                                                phi1 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                },
                                                phi2 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1) && (abs(v0.effVelocityInKmPH - v1.effVelocityInKmPH) < 10) &&
                                                            next(v0, v1) { v0, v1 ->
                                                                until(
                                                                    v0,
                                                                    v1,
                                                                    phi1 = { v0, v1 ->
                                                                        besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    },
                                                                    phi2 = { v0, v1 ->
                                                                        isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    }
                                                                )
                                                            }
                                                }
                                            )
                                        }
                            })
                    }
        }
    }

val noOpposingTrafficDuringOvertaking =
    predicate("no opposing traffic during overtaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            !isDuringOvertaking(v0.tickData.currentTick, v0, v1, ctx) || (v0.effVelocityInKmPH - v1.effVelocityInKmPH >= 10)
        }
    }

val opposingTrafficDuringOvertaking =
    predicate("Opposing traffic during overtaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            val v0at0 : Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v0.id } as Vehicle
            val v1at0 : Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v1.id } as Vehicle
            if (v0.id == 138 && v1.id == 150) {
                println("tick: " + v0.tickData.currentTick + " v0: " + v0.id + " v1: " + v1.id + " overtaking: " + isDuringOvertaking(v0.tickData.currentTick, v0, v1, ctx) + " opposing traffic: " + (v0.tickData.vehicles.any { v2 -> onSameLane.holds(ctx, v0, v2) && v1.lane.laneId.sign != v2.lane.laneId.sign}))
                println("is behind: " + isBehindAnyDirection.holds(ctx, v0, v1) + " v0 road pol: " + v0.lane.road.id + " " + v0.positionOnLane + " v1 road pol: " + v1.lane.road.id + " " + v1.positionOnLane + " besides: " + besidesAnyDirection.holds(ctx, v0, v1) + " longitudinal distance: " + longitudinalDistance(v0, v1) +  " in front: " + isBehind.holds(ctx, v1, v0) + " both over 10mph: " + bothOver10MPH.holds(ctx, v0, v1))
            }
            isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && v0.tickData.vehicles.any { v2 -> onSameLane.holds(ctx, v0, v2) && v1.lane.laneId.sign != v2.lane.laneId.sign}
        }
    }

/*val opposingTrafficDuringOvertaking =
    predicate("Opposing traffic during overtaking", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            isBehind.holds(ctx, v0, v1) &&
                    bothOver10MPH.holds(ctx, v0, v1) &&
                    next(v0, v1) { v0, v1 ->
                        until(
                            v0,
                            v1,
                            phi1 = { v0, v1 ->
                                isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                            },
                            phi2 = { v0, v1 ->
                                besides.holds(ctx, v0, v1) &&
                                        bothOver10MPH.holds(ctx, v0, v1) &&
                                        next(v0, v1) { v0, v1 ->
                                            until(
                                                v0,
                                                v1,
                                                phi1 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                },
                                                phi2 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1) && v0.tickData.vehicles.any { v2 -> onSameLane.holds(ctx, v0, v2) && v1.lane.laneId.sign != v2.lane.laneId.sign} &&
                                                            next(v0, v1) { v0, v1 ->
                                                                until(
                                                                    v0,
                                                                    v1,
                                                                    phi1 = { v0, v1 ->
                                                                        besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    },
                                                                    phi2 = { v0, v1 ->
                                                                        isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    }
                                                                )
                                                            }
                                                }
                                            )
                                        }
                            })
                    }
        }
    }*/

val noAccelerationWhileBeingOvertaken =
    predicate("No acceleration while being overtaken", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        globally(v0, v1) { v0, v1 ->
            !isDuringOvertaking(v0.tickData.currentTick, v0, v1, ctx) || (next(v1) {v1NextTick ->
                (v1.effVelocityInKmPH >= v1NextTick.effVelocityInKmPH)
            })
        }
    }

/*val accelerationWhileBeingOvertaken =
    predicate("Acceleration while being overtaken", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            if (hasOvertaken.holds(ctx, v0)) {
                val v0at0: Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v0.id } as Vehicle
                val v1at0: Vehicle = ctx.segment.tickData.first().entities.find { e -> e.id == v1.id } as Vehicle
                *//*if (v0.id == 138 && v1.id == 144) {
                    println("tick: " + v0.tickData.currentTick + " v0: " + v0.id + " " + v0.effVelocityInMPH + " v1: " + v1.id + " " + v1.effVelocityInMPH + " has overtaken: " + hasOvertaken.holds(ctx, v0) + " during overtaking: " + isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) + " acceleration: " + (next(v1) {v1NextTick ->
                        (v1NextTick.effVelocityInKmPH - v1.effVelocityInKmPH > 3)}) + " acceleration: " + v1.effAccelerationInMPerSSquared)
                    println("is behind: " + isBehind.holds(ctx, v0, v1) + " besides: " + besides.holds(ctx, v0, v1) + " in front: " + isBehind.holds(ctx, v1, v0) + " both over 10mph: " + bothOver10MPH.holds(ctx, v0, v1))
                    println("result: " + (isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && (abs(v0.effVelocityInKmPH - v1.effVelocityInKmPH) < 10)))
                }*//*
                isDuringOvertaking(v0.tickData.currentTick, v0at0, v1at0, ctx) && (next(v1) { v1NextTick ->
                    (v1NextTick.effVelocityInKmPH - v1.effVelocityInKmPH > 3)
                })
            }
            false
        }
    }*/

val accelerationWhileBeingOvertaken =
    predicate("Acceleration while being overtaken", Vehicle::class to Vehicle::class) { ctx, v0, v1 ->
        eventually(v0, v1) { v0, v1 ->
            isBehind.holds(ctx, v0, v1) &&
                    bothOver10MPH.holds(ctx, v0, v1) &&
                    next(v0, v1) { v0, v1 ->
                        until(
                            v0,
                            v1,
                            phi1 = { v0, v1 ->
                                isBehind.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                            },
                            phi2 = { v0, v1 ->
                                besides.holds(ctx, v0, v1) &&
                                        bothOver10MPH.holds(ctx, v0, v1) &&
                                        next(v0, v1) { v0, v1 ->
                                            until(
                                                v0,
                                                v1,
                                                phi1 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                },
                                                phi2 = { v0, v1 ->
                                                    besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1) && (next(v1) { v1NextTick ->
                                                        (v1NextTick.effVelocityInKmPH - v1.effVelocityInKmPH > 3)}) &&
                                                            next(v0, v1) { v0, v1 ->
                                                                until(
                                                                    v0,
                                                                    v1,
                                                                    phi1 = { v0, v1 ->
                                                                        besides.holds(ctx, v0, v1) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    },
                                                                    phi2 = { v0, v1 ->
                                                                        isBehind.holds(ctx, v1, v0) && bothOver10MPH.holds(ctx, v0, v1)
                                                                    }
                                                                )
                                                            }
                                                }
                                            )
                                        }
                            })
                    }
        }
    }

// endregion
