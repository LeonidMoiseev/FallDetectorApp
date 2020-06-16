package com.thenightlion.mbs.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.google.android.gms.location.*
import com.thenightlion.mbs.R
import com.thenightlion.mbs.ui.MainActivity
import com.thenightlion.mbs.utils.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

const val MIN_THRESHOLD = 4.0
const val MAX_THRESHOLD = 30.0

class FallDetectorService : Service(), SensorEventListener {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var sensorManager: SensorManager
    private lateinit var mySensor: Sensor
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var x: Double = 0.0
    private var y: Double = 0.0
    private var z: Double = 0.0
    private var sqrtAcceleration: Double = 0.0
    private var minThresholdPassed = false
    private var maxThresholdPassed = false
    private var fallDetected = false
    private var startTimeFreeFall = 0L
    private var lastTimeFreeFall = 0L
    private var diff = 0L
    private var counter = 0

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            x = event.values[0].toDouble()
            y = event.values[1].toDouble()
            z = event.values[2].toDouble()

            fallDetectorAlgorithm()
        }
    }

    private fun fallDetectorAlgorithm() {
        sqrtAcceleration= sqrt(x.pow(2) + y.pow(2) + z.pow(2))

        if (sqrtAcceleration < MIN_THRESHOLD && !minThresholdPassed)
        {
            minThresholdPassed = true
            startTimeFreeFall = System.currentTimeMillis()
            Toast.makeText(this,"free fall to ground $sqrtAcceleration",Toast.LENGTH_SHORT).show()
        }

        if (minThresholdPassed)
        {
            counter++
            if (sqrtAcceleration >= MAX_THRESHOLD && !maxThresholdPassed) {
                lastTimeFreeFall = System.currentTimeMillis()
                diff = lastTimeFreeFall - startTimeFreeFall
                Toast.makeText(this,"diff:$diff\n $sqrtAcceleration",Toast.LENGTH_LONG).show()

                if (diff in 100..5000)
                {
                    maxThresholdPassed = true

                    if(maxThresholdPassed && minThresholdPassed) {

                        fallDetected = true

                        val timer = object : CountDownTimer(5000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                if (millisUntilFinished < 4000 && sqrtAcceleration !in 8.0..11.0) {
                                    fallDetected = false
                                }
                            }

                            override fun onFinish() {
                                if (fallDetected) {

                                    getLastLocation()
                                    //App.instance.sharedPreferencesUtils.putString("fall", "yes")
                                    //Toast.makeText(applicationContext,"fall detected $sqrtAcceleration",Toast.LENGTH_LONG).show()

                                    x = 0.0
                                    y = 0.0
                                    z = 0.0
                                    minThresholdPassed = false
                                    maxThresholdPassed = false
                                }
                            }
                        }
                        timer.start()
                    }
                }
            }
        }

        if (counter > 10)
        {
            counter = 0
            minThresholdPassed = false
            maxThresholdPassed = false
        }
    }

    private fun sendSMS(phoneNo: String?, msg: String?) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNo, null, msg, null, null)
            Toast.makeText(this, "Message Sent", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Toast.makeText(this, "Error Sent SMS", Toast.LENGTH_LONG).show()
            ex.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (isLocationEnabled()) {
            mFusedLocationClient.lastLocation.addOnCompleteListener { task ->
                val location: Location? = task.result
                if (location == null) {
                    requestNewLocationData()
                } else {
                    showLocation(location.latitude.toString(), location.longitude.toString())
                }
            }
        } else {
            Toast.makeText(this, "Включите геолокацию на вашем телефоне", Toast.LENGTH_LONG).show()
            /*val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)*/
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            showLocation(mLastLocation.latitude.toString(), mLastLocation.longitude.toString())
        }
    }

    private fun showLocation(latitude: String, longitude: String) {
        val geoCoder = Geocoder(this, Locale.getDefault())
        val addresses = geoCoder.getFromLocation(latitude.toDouble(), longitude.toDouble(), 1)
        val address = addresses[0].getAddressLine(0)
        /*Toast.makeText(
            this,
            "Широта: $latitude\nДолгота:$longitude",
            Toast.LENGTH_LONG
        ).show()*/
        //Toast.makeText(this, address, Toast.LENGTH_LONG).show()

        sendSMS(App.instance.sharedPreferencesUtils.getString("phoneNumber"), address)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    @SuppressLint("DefaultLocale")
    override fun onCreate() {
        super.onCreate()
        log("The service has been created".toUpperCase())
        val notification = createNotification()
        startForeground(1, notification)

        sensorManager = getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
        mySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, mySensor, SensorManager.SENSOR_DELAY_NORMAL)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("DefaultLocale")
    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        )
        log("The service has been destroyed".toUpperCase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("WakelockTimeout")
    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting its task", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // we're starting a loop in a coroutine
        GlobalScope.launch(Dispatchers.IO) {
            while (isServiceStarted) {
                launch(Dispatchers.IO) {
                    pingFakeServer()
                }
                delay(1 * 60 * 1000)
            }
            log("End of the loop for the service")
        }
    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    @SuppressLint("SimpleDateFormat", "HardwareIds")
    private fun pingFakeServer() {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.mmmZ")
        val gmtTime = df.format(Date())

        val deviceId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        val json =
            """
                {
                    "deviceId": "$deviceId",
                    "createdAt": "$gmtTime"
                }
            """
        try {
            Fuel.post("https://jsonplaceholder.typicode.com/posts")
                .jsonBody(json)
                .response { _, _, result ->
                    val (bytes, error) = result
                    if (bytes != null) {
                        log("[response bytes] ${String(bytes)}")
                    } else {
                        log("[response error] ${error?.message}")
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // depending on the Android API that we're dealing with we will have
        // to use a specific method to create the notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).let {
                it.description = "Endless Service channel"
                it.enableLights(true)
                it.lightColor = Color.RED
                it.enableVibration(true)
                it.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                it
            }
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, 0)
            }

        val builder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(
                this,
                notificationChannelId
            ) else Notification.Builder(this)

        return builder
            .setContentTitle("Детектор падения активен")
            .setContentText("Нажмите чтобы вернуться в приложение")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            .setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }
}