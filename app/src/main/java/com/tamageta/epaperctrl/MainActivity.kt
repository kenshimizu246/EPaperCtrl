package com.tamageta.epaperctrl

import android.Manifest
import android.Manifest.permission
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes.ERROR_GATT_WRITE_REQUEST_BUSY
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private val SCAN_PERIOD: Long = 10000
    private val PERMISSION_REQUEST_CODE = 1
    private var isScanning = false
    private val scanHandler = Handler(Looper.getMainLooper())

    private val SERVICE_NAME = "XIAOESP32C6_EPaper"

    private var myGatt: BluetoothGatt? = null
    private var myChara: BluetoothGattCharacteristic? = null

    private val uuidService = "ad0cb4c2-d2c4-4d54-ae5b-487ed497482c"
    private val uuidCharSwitch = "287848e7-2c0e-43b2-8c6c-656b04fda6f5"
    private val serviceUUID = UUID.fromString(uuidService)
    private val characteristicUUID = UUID.fromString(uuidCharSwitch)
    // private val charaUuidIMU = UUID.fromString(uuidCharIMU)


    private lateinit var textViewStatus: TextView
    private lateinit var editTextLine0: EditText
    private lateinit var editTextLine1: EditText
    private lateinit var buttonUpdate: Button
    private lateinit var buttonScan: Button

    private val viewScope = CoroutineScope(Job() + Dispatchers.Main)

    @RequiresPermission(allOf = [
        permission.BLUETOOTH_SCAN,
        permission.BLUETOOTH_CONNECT
    ])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

        // Text
        textViewStatus = findViewById(R.id.status_text_view)
        editTextLine0 = findViewById(R.id.editTxtLine0)
        editTextLine1 = findViewById(R.id.editTxtLine1)
        buttonScan = findViewById(R.id.scan_button)
        buttonUpdate = findViewById(R.id.btnUpdate)

        textViewStatus.setBackgroundColor(Color.RED)
        buttonUpdate.isEnabled = false

       if(bluetoothAdapter != null){
           bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner!!
           buttonScan.setOnClickListener {
               if (isScanning) stopBleScan() else startBleScan()
           }
           buttonUpdate.setOnClickListener{
               doUpdate()
           }
       }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @RequiresPermission(allOf = [
            permission.BLUETOOTH_SCAN,
            permission.BLUETOOTH_CONNECT
        ])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val deviceName = result.scanRecord?.deviceName
            textViewStatus.text = "onScanResult..."
            Log.i("MainActivity", "Device Name: ${deviceName}")
            if(result.scanRecord?.deviceName == SERVICE_NAME){
                //result.device.connectGatt(this, false, mGattCallback)
//                serviceUuids = result.scanRecord?.serviceUuids
                myGatt = result.device.connectGatt(applicationContext, false, bluetoothGattCallback)
                val service: BluetoothGattService? = myGatt?.getService(serviceUUID)
                myChara = service?.getCharacteristic(characteristicUUID)
                Log.i("MainActivity", "onScanResult for ${deviceName}")
                stopBleScan()

            }
        }
    }
    
    private fun scanLeDevice() {
        textViewStatus.text = "scanLeDevice..."
        if (ActivityCompat.checkSelfPermission(
                this,
                permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // needs to add BLUETOOTH_ADVERTISE
            ActivityCompat.requestPermissions(  this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, permission.BLUETOOTH_CONNECT, permission.BLUETOOTH_SCAN, permission.BLUETOOTH_ADVERTISE),
                PERMISSION_REQUEST_CODE)
            return
        }
        bluetoothLeScanner?.let { scanner ->
            if (!isScanning) { // Stops scanning after a pre-defined scan period.
                scanHandler.postDelayed({
                    isScanning = false
                    scanner.stopScan(leScanCallback)
                    println("stopScan")
                }, SCAN_PERIOD)
                isScanning = true
                scanner.startScan(leScanCallback)
                setScanning(true)
            } else {
                isScanning = false
                scanner.stopScan(leScanCallback)
            }
        }
    }

    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    fun startBleScan(){
        scanLeDevice()
    }

    @RequiresPermission(permission.BLUETOOTH_SCAN)
    private fun stopBleScan() {
        scanHandler.removeCallbacksAndMessages(null)
        if (isScanning) {
            bluetoothLeScanner?.stopScan(leScanCallback)
            setScanning(false)
            if (textViewStatus.text == "Scanning...") {
                textViewStatus.text = "No device found"
                textViewStatus.setBackgroundColor(Color.RED)
            }
        }
    }

    private fun setScanning(scanning: Boolean) {
        isScanning = scanning
        buttonScan.text = if (scanning) "Stop Scan" else getString(R.string.btn_scan)
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val scope = CoroutineScope(Job() + Dispatchers.Main)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("MainActivity", "onConnectionStateChange.STATE_CONNECTED!")
                // successfully connected to the GATT Server
                scope.launch {
                    withContext(Dispatchers.Main) {
                        textViewStatus.text = "Connected on ConnectionStateChange..."
                        textViewStatus.setBackgroundColor(Color.GREEN)
                        buttonUpdate.isEnabled = true
                    }
                }
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("MainActivity", "onConnectionStateChange.STATE_DISCONNECTED!")
                // disconnected from the GATT Server
                scope.launch {
                    withContext(Dispatchers.Main) {
                        textViewStatus.text = "Disconnected on ConnectionStateChange..."
                        textViewStatus.setBackgroundColor(Color.YELLOW)
                        buttonUpdate.isEnabled = false
                    }
                }
            }
        }

        @RequiresPermission(allOf = [
            permission.BLUETOOTH_SCAN,
            permission.BLUETOOTH_CONNECT
        ])
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service: BluetoothGattService? = gatt?.getService(serviceUUID)
                myGatt = gatt
                myChara = service?.getCharacteristic(characteristicUUID)
                Log.i("MainActivity", "onServicesDiscovered!")
                stopBleScan()
            }
        }
    }

    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    private fun doUpdate(){
        val text0 = editTextLine0.text.toString()
        val text1 = editTextLine1.text.toString()

        Log.d("doSetText-0", "Text: $text0")
        val cmd0 = byteArrayOf(0x02) + byteArrayOf(0x00) + text0.toByteArray(Charsets.UTF_8)
        sendLedCommand(cmd0)

        Log.d("doSetText-1", "Text: $text1")
        val cmd1 = byteArrayOf(0x02) + byteArrayOf(0x01) + text1.toByteArray(Charsets.UTF_8)
        sendLedCommand(cmd1)

        sendLedCommand(byteArrayOf(0x01))
    }

    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    private fun sendLedCommand(data: ByteArray) {
        if(myGatt == null){
            textViewStatus.text= "bluetoothGatt is null!"
        }
        val gatt = myGatt ?: return
        if(myChara == null){
            textViewStatus.text= "switchCharacteristic is null!"
        }
        val chara = myChara ?: return

        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            var cnt = 0
            do {
                var r = gatt.writeCharacteristic(chara, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                Log.d("sendLedCommand", "Return: $r")
                Thread.sleep(100)
                if(cnt++ < 100){
                    break;
                }
            }while(r == ERROR_GATT_WRITE_REQUEST_BUSY)
        } else {
            TODO("VERSION.SDK_INT < TIRAMISU")
        }
        Log.d("sendLedCommand", "Return: $r")
    }

    @RequiresPermission(permission.BLUETOOTH_SCAN)
    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()
    }
}

