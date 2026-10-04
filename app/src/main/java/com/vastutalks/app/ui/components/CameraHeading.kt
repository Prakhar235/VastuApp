package com.vastutalks.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Live compass heading of the phone's back camera, in degrees clockwise
 * from magnetic North (null until the first reading, or if the phone
 * has no compass).
 *
 * Uses the direction the back camera looks, so it works whether the
 * phone is held upright or tilted; when it's lying nearly flat (camera
 * pointing at the floor) it falls back to where the top of the phone
 * points, which is the way the person holding it is facing.
 */
@Composable
fun rememberCameraHeading(): State<Float?> {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val rotation = FloatArray(9)
        // Smoothed as a unit vector so 359° → 1° doesn't average to 180°.
        var east = 0f
        var north = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                // Columns of [rotation] are the device axes in world (East, North, Up)
                // coordinates. The back camera looks along device -Z.
                var x = -rotation[2]
                var y = -rotation[5]
                if (hypot(x, y) < 0.35f) { // nearly flat: use device +Y instead
                    x = rotation[1]
                    y = rotation[4]
                }
                val angle = atan2(x, y)
                if (heading.value == null) {
                    east = sin(angle); north = cos(angle)
                } else {
                    east += (sin(angle) - east) * SMOOTHING
                    north += (cos(angle) - north) * SMOOTHING
                }
                heading.value = ((Math.toDegrees(atan2(east, north).toDouble()) + 360.0) % 360.0).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensor != null) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }
    return heading
}

private const val SMOOTHING = 0.15f
