package com.thenightlion.mbs.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneNumberFormattingTextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.thenightlion.mbs.R
import com.thenightlion.mbs.service.*
import com.thenightlion.mbs.utils.App
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dialog_phone_number_setting.view.*


const val PHONE_NUMBER_KEY: String = "phoneNumber"
const val SERVICE_STATUS_KEY: String = "serviceStatus"
const val REQUEST_CODE_PERMISSION_SEND_SMS = 1
const val REQUEST_CODE_PERMISSION_GPS = 42

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var dialogPhoneSetting: AlertDialog
    private lateinit var currentNumberPhone: String
    private lateinit var sensorManager: SensorManager
    private var ax: Double = 0.0
    private var ay: Double = 0.0
    private var az: Double = 0.0

    private lateinit var checkStart: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        updateCheckStartAndNumberPhone()

        if (checkStart == "started") {
            startSensor()
            btnStartOrStopDetector.text = "Стоп"
            btnStartOrStopDetector.background = getDrawable(R.drawable.button_background_stop)
        }

        btnPhoneNumberSetting.setOnClickListener { dialogPhoneSetting() }
        btnStartOrStopDetector.setOnClickListener {
            updateCheckStartAndNumberPhone()
            if (currentNumberPhone != "null") {
                if (App.getInstance().permissionsUtils.checkPermissionSendSMS()) {
                    if (App.getInstance().permissionsUtils.checkPermissionLocation()) {
                        if (isLocationEnabled()) {
                            if (checkStart == "stopped" || checkStart == "null") {
                                startServiceAndSensor()
                            } else if (checkStart == "started") {
                                stopServiceAndSensor()
                            }
                        } else Toast.makeText(this, "Включите геолокацию на вашем телефоне", Toast.LENGTH_LONG).show()
                    } else requestPermissionLocation()
                } else startRequestPermission(
                    arrayOf(Manifest.permission.SEND_SMS),
                    REQUEST_CODE_PERMISSION_SEND_SMS
                )
            } else Toast.makeText(this, "Настройте номер телефона", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateCheckStartAndNumberPhone() {
        checkStart = App.instance.sharedPreferencesUtils.getString(SERVICE_STATUS_KEY)
        currentNumberPhone = App.instance.sharedPreferencesUtils.getString(PHONE_NUMBER_KEY)
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, FallDetectorService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            startService(it)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            REQUEST_CODE_PERMISSION_SEND_SMS -> {
                requestPermissionLocation()
            }
            REQUEST_CODE_PERMISSION_GPS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (isLocationEnabled()) {
                        startServiceAndSensor()
                    } else Toast.makeText(this, "Включите геолокацию на вашем телефоне", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Отказано в доступе к разрешениям", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    private fun startServiceAndSensor() {
        actionOnService(Actions.START)
        startSensor()
        App.instance.sharedPreferencesUtils.putString(SERVICE_STATUS_KEY, "started")
        btnStartOrStopDetector.text = "Стоп"
        btnStartOrStopDetector.background = getDrawable(R.drawable.button_background_stop)
    }

    private fun stopServiceAndSensor() {
        actionOnService(Actions.STOP)
        stopSensor()
        App.instance.sharedPreferencesUtils.putString(SERVICE_STATUS_KEY, "stopped")
        btnStartOrStopDetector.text = "Старт"
        btnStartOrStopDetector.background = getDrawable(R.drawable.button_background_start)
    }

    private fun startRequestPermission(permission: Array<String?>, code: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permission, code)
        }
    }

    private fun requestPermissionLocation() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_CODE_PERMISSION_GPS
        )
    }

    private fun isLocationEnabled(): Boolean {
        var locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("InflateParams")
    private fun dialogPhoneSetting() {
        val builderDialogPhoneNumber: AlertDialog.Builder = AlertDialog.Builder(this)
        val viewDialogPhoneNumber: View =
            layoutInflater.inflate(R.layout.dialog_phone_number_setting, null)
        builderDialogPhoneNumber.setView(viewDialogPhoneNumber)
        dialogPhoneSetting = builderDialogPhoneNumber.create()

        viewDialogPhoneNumber.etPhoneNumber.addTextChangedListener(PhoneNumberFormattingTextWatcher())

        viewDialogPhoneNumber.btnCancel.setOnClickListener { dialogPhoneSetting.dismiss() }
        viewDialogPhoneNumber.btnAccept.setOnClickListener {
            if (viewDialogPhoneNumber.etPhoneNumber.text.length == 15) {
                saveNumber(viewDialogPhoneNumber.etPhoneNumber.text.toString().trim())
                dialogPhoneSetting.dismiss()
            } else Toast.makeText(this, "Неверный формат номера", Toast.LENGTH_SHORT).show()
        }

        dialogPhoneSetting.show()

        if (currentNumberPhone != "null") viewDialogPhoneNumber.etPhoneNumber.setText(
            currentNumberPhone
        )
    }

    private fun saveNumber(numberStr: String) {
        val number = getOnlyNumerics(numberStr)
        App.instance.sharedPreferencesUtils.putString(PHONE_NUMBER_KEY, number)
        currentNumberPhone = App.instance.sharedPreferencesUtils.getString(PHONE_NUMBER_KEY)
        Toast.makeText(this, "Номер сохранён", Toast.LENGTH_SHORT).show()
    }

    private fun getOnlyNumerics(str: String?): String? {
        if (str == null) {
            return null
        }
        val strBuff = StringBuffer()
        var c: Char
        for (element in str) {
            c = element
            if (Character.isDigit(c) || c == '.') {
                strBuff.append(c)
            }
        }
        return strBuff.toString()
    }

    private fun startSensor() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun stopSensor() {
        sensorManager.unregisterListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        )
        tv_ax.text = ""
        tv_ay.text = ""
        tv_az.text = ""
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            ax = event.values[0].toDouble()
            ay = event.values[1].toDouble()
            az = event.values[2].toDouble()
            tv_ax.text = "aX: ${"%.2f".format(ax)}"
            tv_ay.text = "aY: ${"%.2f".format(ay)}"
            tv_az.text = "aZ: ${"%.2f".format(az)}"
        }
    }
}
