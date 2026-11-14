package com.vastutalks.app.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vastutalks.app.data.ai.CompassDirection
import com.vastutalks.app.data.ai.PhotoFacing
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** What this phone can use to tell which way the camera faces. */
enum class HeadingSensor {
    /** A real compass (magnetometer): the heading is known straight away. */
    COMPASS,
    /** No compass, but a gyroscope: turns are tracked once the caller says which way one direction is. */
    GYRO,
    /** Neither: the caller has to say which way the camera faces. */
    NONE
}

/**
 * Live heading of the phone's back camera, in degrees clockwise from
 * (magnetic) North; null until the first reading, on a phone that can't
 * sense direction, or on a gyroscope-only phone until a reference
 * direction has been set with [CameraHeadingSensor.setDirection].
 *
 * Uses the direction the back camera looks, so it works whether the
 * phone is held upright or tilted; when it's lying nearly flat (camera
 * pointing at the floor) it falls back to where the top of the phone
 * points, which is the way the person holding it is facing.
 */
@Composable
fun rememberCameraHeading(): State<Float?> {
    val pose = rememberCameraPose()
    return remember { derivedStateOf { pose.value?.heading } }
}

/**
 * [heading] as for [rememberCameraHeading]; [isUpright] is true while the
 * phone is held up straight (camera looking roughly level, not at the
 * floor or ceiling), as wanted for photographing a wall. [source] is
 * [PhotoFacing.Source.COMPASS] or [PhotoFacing.Source.TRACKED].
 */
data class CameraPose(val heading: Float, val isUpright: Boolean, val source: PhotoFacing.Source)

@Composable
fun rememberCameraPose(): State<CameraPose?> {
    val context = LocalContext.current
    DisposableEffect(context) {
        CameraHeadingSensor.acquire(context)
        onDispose { CameraHeadingSensor.release() }
    }
    return CameraHeadingSensor.pose
}

/** Which kind of direction sensing this phone has. */
@Composable
fun rememberHeadingSensor(): HeadingSensor {
    val context = LocalContext.current
    return remember(context) { CameraHeadingSensor.kindOf(context) }
}

/**
 * One shared listener for every screen showing the camera's direction.
 *
 * Phones without a compass fall back to the gyroscope-based
 * TYPE_GAME_ROTATION_VECTOR, whose zero is some arbitrary direction, so
 * its readings only mean something once the caller has said which way
 * the camera faces ([setDirection]). That reference is dropped whenever
 * the listener stops, since the sensor may pick a new zero when it
 * starts again.
 */
object CameraHeadingSensor {
    private var sensors: SensorManager? = null
    private var sensor: Sensor? = null
    private var kind: HeadingSensor? = null
    private var users = 0

    /** Heading straight from the sensor: true heading for a compass, arbitrary zero for a gyroscope. */
    private val raw = mutableStateOf<CameraPose?>(null)

    /** Degrees to add to a gyroscope reading to get the real heading. */
    private var offset by mutableStateOf<Float?>(null)

    val pose: State<CameraPose?> = derivedStateOf {
        val r = raw.value ?: return@derivedStateOf null
        when (kind) {
            HeadingSensor.COMPASS -> r
            HeadingSensor.GYRO -> offset?.let { r.copy(heading = wrap(r.heading + it), source = PhotoFacing.Source.TRACKED) }
            else -> null
        }
    }

    /** True on a gyroscope-only phone once [setDirection] has given it a reference. */
    val hasReference: Boolean get() = offset != null

    fun kindOf(context: Context): HeadingSensor {
        kind?.let { return it }
        val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val compass = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val gyro = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        sensors = manager
        sensor = compass ?: gyro
        return when {
            compass != null -> HeadingSensor.COMPASS
            gyro != null -> HeadingSensor.GYRO
            else -> HeadingSensor.NONE
        }.also { kind = it }
    }

    /**
     * Tells a gyroscope-only phone that the camera is facing [direction]
     * right now; turns from here on are tracked from it. Picked before
     * the sensor's first reading, it applies to that reading.
     */
    fun setDirection(direction: CompassDirection) {
        val r = raw.value
        if (r == null) pending = direction else offset = wrap(direction.degrees - r.heading)
    }

    private var pending: CompassDirection? = null

    internal fun acquire(context: Context) {
        kindOf(context)
        if (users++ == 0) {
            val s = sensor ?: return
            sensors?.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        }
    }

    internal fun release() {
        if (users == 0) return
        if (--users == 0) {
            sensors?.unregisterListener(listener)
            raw.value = null
            offset = null
            pending = null
        }
    }

    private val rotation = FloatArray(9)
    // Smoothed as a unit vector so 359° → 1° doesn't average to 180°.
    private var east = 0f
    private var north = 0f

    private val listener = object : SensorEventListener {
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
            if (raw.value == null) {
                east = sin(angle); north = cos(angle)
            } else {
                east += (sin(angle) - east) * SMOOTHING
                north += (cos(angle) - north) * SMOOTHING
            }
            raw.value = CameraPose(
                heading = wrap(Math.toDegrees(atan2(east, north).toDouble()).toFloat()),
                // Device Z's Up component: near 0 when the phone stands upright.
                isUpright = abs(rotation[8]) < UPRIGHT_LIMIT,
                source = PhotoFacing.Source.COMPASS
            )
            pending?.let { pending = null; setDirection(it) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun wrap(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
}

private const val SMOOTHING = 0.15f
/** sin of ~20°: how far the camera may tip up or down and still count as level. */
private const val UPRIGHT_LIMIT = 0.34f
