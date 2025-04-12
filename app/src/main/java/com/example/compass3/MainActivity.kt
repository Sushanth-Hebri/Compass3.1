package com.example.compass3

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var currentDirection: Float = 0f

    private lateinit var database: FirebaseDatabase
    private lateinit var rootRef: DatabaseReference
    private lateinit var requestListener: ValueEventListener

    private var user1Tracking = false

    private lateinit var directionTextView: TextView
    private lateinit var urlEditText: EditText
    private lateinit var backgroundToggle: ToggleButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        directionTextView = findViewById(R.id.directionTextView)
        urlEditText = findViewById(R.id.urlEditText)
        backgroundToggle = findViewById(R.id.backgroundToggle)

        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        rootRef = database.reference

        // Firebase listener
        setupRequestListener()

        // Toggle button logic
        backgroundToggle.setOnCheckedChangeListener { _, isChecked ->
            val url = urlEditText.text.toString().trim()
            if (isChecked) {
                if (url.isNotEmpty()) {
                    val intent = Intent(this, CompassBackgroundService::class.java)
                    intent.putExtra("url", url)
                    startService(intent)
                } else {
                    backgroundToggle.isChecked = false
                }
            } else {
                stopService(Intent(this, CompassBackgroundService::class.java))
            }
        }
    }

    private fun setupRequestListener() {
        requestListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val objectName = child.key ?: continue
                    val isRequested = child.child("request_direction").getValue(Boolean::class.java) ?: false

                    if (objectName == "user1") {
                        user1Tracking = isRequested
                        Log.d("Compass", "User1 tracking mode: $user1Tracking")
                    } else if (isRequested) {
                        Log.d("Compass", "One-time direction request for: $objectName")
                        sendDirectionToObject(objectName)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Compass", "Listener cancelled", error.toException())
            }
        }

        rootRef.addValueEventListener(requestListener)
    }

    private fun sendDirectionToObject(objectName: String) {
        val objectRef = rootRef.child(objectName)
        objectRef.child("direction").setValue(currentDirection)
        objectRef.child("request_direction").setValue(false)
            .addOnSuccessListener {
                Log.d("Compass", "Direction sent to $objectName: ${currentDirection.roundToInt()}°")
            }
            .addOnFailureListener {
                Log.e("Compass", "Failed to update direction for $objectName", it)
            }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> System.arraycopy(it.values, 0, gravity, 0, 3)
                Sensor.TYPE_MAGNETIC_FIELD -> System.arraycopy(it.values, 0, geomagnetic, 0, 3)
            }

            val R = FloatArray(9)
            val I = FloatArray(9)

            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(R, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                currentDirection = (azimuth + 360) % 360

                directionTextView.text = "Current: ${currentDirection.roundToInt()}°"

                if (user1Tracking) {
                    rootRef.child("user1").child("direction").setValue(currentDirection)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        rootRef.removeEventListener(requestListener)
    }

    companion object {
        fun requestDirection(database: FirebaseDatabase, objectName: String) {
            database.reference.child(objectName).child("request_direction").setValue(true)
        }
    }
}
