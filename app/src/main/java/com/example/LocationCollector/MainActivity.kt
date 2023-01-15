package com.example.LocationCollector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.LocationCollector.databinding.ActivityMainBinding
import com.huawei.hms.hmsscankit.ScanUtil
import com.huawei.hms.ml.scan.HmsScan
import com.huawei.hms.ml.scan.HmsScanAnalyzerOptions
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.net.SocketException
import java.util.*

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding

	private var timeInterval: Long = 5000
	private val timeToQuitInterval:Int=2000
	private var lastPressedTime:Long=0
	private var keepRunning:Boolean=true
	private lateinit var context: Context
	private lateinit var locationManager:LocationManager
	var isConnected: Boolean = false
	var communicationDescriptor: Socket? = null
	var writeStream: OutputStream? = null
	private val permissionList= listOf(
		android.Manifest.permission.INTERNET,
		android.Manifest.permission.ACCESS_FINE_LOCATION,
		android.Manifest.permission.ACCESS_COARSE_LOCATION,
		android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
		android.Manifest.permission.READ_EXTERNAL_STORAGE,
		android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
		//android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
	)
	companion object {
		const val LOCATION_REQUEST_CODE = 1
		const val LISTENER_REQUEST_CODE = 2
		const val CAMERA_REQUEST_CODE = 3
		var ip: String? = null
		var port: Int = 0
	}
	private var gpsListener: LocationListener = object : LocationListener {
		override fun onLocationChanged(location: Location) {
			Log.i("MainActivity", "onLocationChanged(${location.provider}): 经纬度发生变化")
		}

		override fun onProviderDisabled(provider: String) {
			if(provider==LocationManager.GPS_PROVIDER){
				CoroutineScope(Dispatchers.Main).launch {
					AlertDialog.Builder(context).apply {
						setTitle("获取失败")
						setMessage("请打开定位服务开关！")
						setCancelable(false)
						setNegativeButton("OK"){ _, _ ->}
						show()
					}
				}
			}
			Log.i("MainActivity", "onProviderDisabled: $provider")
		}

		override fun onProviderEnabled(provider: String) {
			Log.i("MainActivity", "onProviderEnabled: $provider")
		}
	}

	private var networkListener: LocationListener = object : LocationListener {
		override fun onLocationChanged(location: Location) {
			Log.i("MainActivity", "onLocationChanged(${location.provider}): 经纬度发生变化")
		}

		override fun onProviderDisabled(provider: String) {
			if(provider==LocationManager.GPS_PROVIDER){
				CoroutineScope(Dispatchers.Main).launch {
					AlertDialog.Builder(context).apply {
						setTitle("获取失败")
						setMessage("请打开定位服务开关！")
						setCancelable(false)
						setNegativeButton("OK"){ _, _ ->}
						show()
					}
				}
			}
			Log.i("MainActivity", "onProviderDisabled: $provider")
		}

		override fun onProviderEnabled(provider: String) {
			Log.i("MainActivity", "onProviderEnabled: $provider")
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		locationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		context=this
		PermissionUtil.requestPermission(LOCATION_REQUEST_CODE,permissionList,this)
		locationMonitor(LocationManager.NETWORK_PROVIDER,networkListener)
		locationMonitor(LocationManager.GPS_PROVIDER,gpsListener)
		initUI()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		when(requestCode){
			LOCATION_REQUEST_CODE->{
				if (PermissionUtil.verifyResult(grantResults, this)) {
					getLocationInfo()
				} else {
					Toast.makeText(this, "没有位置权限", Toast.LENGTH_SHORT).show()
				}
			}
			CAMERA_REQUEST_CODE->{
				ScanUtil.startScan(this,0x01,HmsScanAnalyzerOptions.Creator().setHmsScanTypes
					(HmsScan.ALL_SCAN_TYPE).create())
			}
		}
	}

	@SuppressLint("SimpleDateFormat")
	private fun initUI(){
		binding.button1.setOnClickListener{
			if (isConnected) {
				Toast.makeText(this,
					"Already connecting to server!", Toast.LENGTH_SHORT)
					.show()
			} else {
				val inputText = this.binding.editText.text.toString()
				checkAddressAvailable(inputText)
			}
			this.binding.editText.setText("")
		}

		binding.buttonTimeSet.setOnClickListener {
			if(this.binding.editTextNumber.text.toString()==""){
				Toast.makeText(
					this,
					"Number needed!",
					Toast.LENGTH_SHORT
				).show()
			}
			else{
				timeInterval = this.binding.editTextNumber.text
					.toString().toLong()*1000
				Toast.makeText(
					this,
					"Success on setting time interval to: "
							+ timeInterval.toString(),
					Toast.LENGTH_SHORT
				).show()
				this.binding.editTextNumber.setText("")
			}
		}

		binding.switch1.setOnCheckedChangeListener { buttonView, isChecked ->
			if(isChecked){
				if(isConnected){
					Toast.makeText(this,"已开启！",Toast.LENGTH_SHORT).show()
					keepRunning=true
					CoroutineScope(Dispatchers.IO).launch {
						while (keepRunning){
							try {
								if(!isLocationServiceOpen()){
									withContext(Dispatchers.Main){
										keepRunning=false
										binding.switch1.toggle()
									}
									break
								}
								delay(timeInterval)
								var finalText=SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
									.format(Date())
								finalText= "$finalText ${getLocationInfo()}"
								sendData(finalText)
								withContext(Dispatchers.Main){
									binding.lastGPSContent.setAutoSizeTextTypeWithDefaults(TextView
										.AUTO_SIZE_TEXT_TYPE_UNIFORM)
									binding.lastGPSContent.text = finalText
								}
							}
							catch (a:SocketException){
								val exceptionMessage=a.message.toString()
								Log.e("MainActivity",exceptionMessage)
								if(exceptionMessage=="Broken pipe" ||
									exceptionMessage=="Connection reset") {
									withContext(Dispatchers.Main) {
										AlertDialog.Builder(context).apply {
											setTitle("连接失败")
											setMessage(a.toString())
											setCancelable(false)
											setNegativeButton("OK") { _, _ -> }
											show()
										}
										setStatusOff()
										binding.textView.text = "未连接"
									}
									break
								}
								if(!keepRunning) break
								var reConnectTimes=5
								while (reConnectTimes>0) try {
									communicationDescriptor = Socket(ip, port)
									writeStream = communicationDescriptor?.getOutputStream()
									break
								}
								catch (d:SocketException){
									delay(1000)
									reConnectTimes--
									Log.e("MainActivity","尝试重连中")
									if (reConnectTimes==0){
										withContext(Dispatchers.Main){
											AlertDialog.Builder(context).apply {
												setTitle("重连失败")
												setMessage(d.toString())
												setCancelable(false)
												setNegativeButton("OK"){ _, _ ->}
												show()
											}
											setStatusOff()
											binding.textView.text = "未连接"
										}
									}
								}
							}
						}
					}
				}
				else{
					Toast.makeText(this,"请先测试服务器连通性！",Toast.LENGTH_SHORT).show()
					buttonView.toggle()
				}
			}
			else{
				keepRunning=false
				CoroutineScope(Dispatchers.IO).cancel()
				Toast.makeText(this,"已关闭！",Toast.LENGTH_SHORT).show()
			}
		}

		binding.buttonCancelConnection.setOnClickListener {
			if(isConnected){
				setStatusOff()
				CoroutineScope(Dispatchers.IO).cancel()
				Toast.makeText(this,"已关闭连接！",Toast.LENGTH_SHORT).show()
				binding.textView.text = "未连接"
			}
			else{
				Toast.makeText(this,"请先开启一个连接",Toast.LENGTH_SHORT).show()
			}
		}

		binding.buttonSendText.setOnClickListener {
			if(isConnected) {
				if(this.binding.editTextTextWantToSend.text.toString()=="") {
					Toast.makeText(this, "请输入要发送的文本！", Toast.LENGTH_SHORT).show()
				}
				else{
					val tmp="${SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
						.format(Date())} (${this.binding.editTextTextWantToSend.text}) " +
							getLocationInfo()
					this.binding.editTextTextWantToSend.setText("")
					CoroutineScope(Dispatchers.Main).launch {
						try {
							withContext(Dispatchers.IO){
								sendData(tmp)
							}
							Toast.makeText(context,"发送成功！",Toast.LENGTH_SHORT).show()
						} catch (e:Exception) {
							Log.e("MainActivity", e.toString())
							AlertDialog.Builder(context).apply {
								setTitle("发送失败")
								setMessage(e.toString())
								setCancelable(false)
								setNegativeButton("OK"){ _, _ ->}
								show()
							}
							setStatusOff()
							binding.textView.text = "未连接"
						}
					}
				}
			}
			else{
				Toast.makeText(this,"请先开启一个连接",Toast.LENGTH_SHORT).show()
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		if(isConnected){
			writeStream?.close()
			communicationDescriptor?.close()
		}
		locationManager.removeUpdates(gpsListener)
		locationManager.removeUpdates(networkListener)
	}

	private fun checkAddressAvailable(addr: String?) {
		addr?.find { it == ':' } ?: return Toast.makeText(
			this,
			"No port number is found.",
			Toast.LENGTH_SHORT
		).show()
		val list = addr.split(':')
		ip = list[0]
		port = list[1].toInt()
		CoroutineScope(Dispatchers.Main).launch {
			try {
				withContext(Dispatchers.IO) {
					communicationDescriptor = Socket(ip, port)
					writeStream = communicationDescriptor?.getOutputStream()
					isConnected = true
				}
				binding.textView.text = "服务器连接状态：已连接"
				Toast.makeText(context,
					"Success on connecting to server.", Toast
						.LENGTH_SHORT)
					.show()
			} catch (e: Exception) {
				Log.e("MainActivity", e.toString())
				AlertDialog.Builder(context).apply {
					setTitle("连接失败")
					setMessage(e.toString())
					setCancelable(false)
					setNegativeButton("OK"){ _, _ ->}
					show()
				}
			}
		}
	}

	override fun onBackPressed() {
		if (System.currentTimeMillis()-lastPressedTime>timeToQuitInterval){
			Toast.makeText(this,"再按一次退出",
				Toast.LENGTH_SHORT).show()
			lastPressedTime=System.currentTimeMillis()
		}
		else{
			if(isConnected){
				writeStream?.close()
				communicationDescriptor?.close()
			}
			finish()
		}
	}

	private fun sendData(targetString: String?){
		val tmp= "n/$targetString"
		writeStream?.write(tmp.toByteArray())
	}

	private fun isLocationServiceOpen(): Boolean {
		val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
		val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
		return gps && network
	}

	private fun getLocationInfo() : String{
		//判断是否开启位置服务，没有则跳转至设置来开启
		if (isLocationServiceOpen()) {
			//获取所有支持的provider
			val providers = locationManager.getProviders(true)
			//用来存储最优的结果
			var betterLocation: Location? = null
			for (provider in providers) {
				try {
					val location = locationManager.getLastKnownLocation(provider)
					location?.let {
						Log.i("MainActivity", "$provider 精度为：${it.accuracy}")
						if (betterLocation == null) {
							betterLocation = it
						} else {
							//因为半径等于精度，所以精度越低代表越准确
							if (it.accuracy < betterLocation!!.accuracy)
								betterLocation = it
						}
					}
					if (location == null) {
						Log.i("MainActivity", "$provider 获取到的位置为null")
					}
				}
				catch (e:SecurityException){
					Log.e("MainActivity","false")
				}
			}
			betterLocation?.let {
				Log.i("MainActivity", "精度最高的获取方式：${it.provider} 经度：${it.longitude}  纬度：${it.latitude}")
				return "[${it.latitude},${it.longitude}]"
			}
			//（四）若所支持的provider获取到的位置均为空，则开启连续定位服务
			if (betterLocation == null) {
				Log.i("MainActivity", "getLocationInfo: 获取到的经纬度均为空，已开启连续定位监听")
			}
		} else {
			Log.e("MainActivity","请跳转到系统设置中打开定位服务")
		}
		return "No location present"
	}

	object PermissionUtil {
		fun requestPermission(requestCode: Int, permissionList: List<String>, context: Context): Boolean {
			//没有同意需要申请的权限
			val requestPermissionList = mutableListOf<String>()
			for (permission in permissionList) {
				if (ContextCompat.checkSelfPermission(
						context,
						permission
					) != PackageManager.PERMISSION_GRANTED
				) {
					requestPermissionList.add(permission)
					//没有获取到的权限则添加进去
				}
			}
			return if (requestPermissionList.size > 0) {
				ActivityCompat.requestPermissions(
					context as Activity,
					requestPermissionList.toTypedArray(),
					requestCode
				)
				false
			} else {
				true
			}
		}

		fun verifyResult(grantResults: IntArray, context: Context): Boolean {
			if (grantResults.isNotEmpty()) {
				for (result in grantResults) {
					if (result != PackageManager.PERMISSION_GRANTED) {
						Toast.makeText(context, "必须同意所有权限才能使用该功能", Toast.LENGTH_SHORT).show()
						return false
					}
				}
				return true
			} else {
				Toast.makeText(context, "发生未知错误", Toast.LENGTH_SHORT).show()
				return false
			}
		}
	}

	private fun locationMonitor(provider: String, listener: LocationListener) {
		if (PermissionUtil.requestPermission(LISTENER_REQUEST_CODE,permissionList,this)) {
			try {
				locationManager.requestLocationUpdates(
					provider,
					10000.toLong(),        //超过15秒钟则更新位置信息
					1.toFloat(),        //位置超过1米则更新位置信息
					listener
				)
				Log.i("MainActivity","locationMonitor of $provider")
			}
			catch (e:SecurityException){
				Log.e("MainActivity","false")
			}
		}
		else{
			Toast.makeText(this, "监听失败", Toast.LENGTH_SHORT).show()
		}
	}

	private fun setStatusOff(){
		communicationDescriptor?.close()
		writeStream?.close()
		isConnected=false
		keepRunning=false
		if(binding.switch1.isChecked){
			binding.switch1.toggle()
		}
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.main,menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		when(item.itemId){
			R.id.startScan->
				ActivityCompat.requestPermissions(this,
					arrayOf<String?>(android.Manifest.permission.CAMERA,
					android.Manifest.permission.READ_EXTERNAL_STORAGE), CAMERA_REQUEST_CODE)
			R.id.startUpdate->{
				if(isConnected){
					CoroutineScope(Dispatchers.IO).launch {
						try {
							writeStream?.write("u/${context.packageManager.getPackageInfo(context
								.packageName,0).versionName}"
								.toByteArray())
							val buffer=communicationDescriptor?.getInputStream()
							val output=openFileOutput("file",Context.MODE_PRIVATE)
							buffer.use {
								it?.read()?.let { it1 -> output.write(it1) }
							}
						}
						catch (e:Exception){
							withContext(Dispatchers.Main){
								Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
							}
						}
					}
				}
				else{
					Toast.makeText(this, "请先连接到服务器！", Toast.LENGTH_SHORT).show()
				}
			}
			R.id.about->{
				AlertDialog.Builder(context).apply {
					setTitle("About")
					setMessage("Android Location Collector\n" +
							"Developed by greatmfc.")
					setCancelable(false)
					setNegativeButton("OK"){ _, _ ->}
					show()
				}
			}
			R.id.sft->{
				val intent=Intent(this,SftActivity::class.java)
				startActivity(intent)
			}
		}
		return true
	}

	@SuppressLint("SimpleDateFormat")
	@Deprecated("Deprecated in Java")
	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (resultCode != RESULT_OK || data == null) {
			Toast.makeText(this, "No available data detected!", Toast.LENGTH_SHORT).show()
			return
		}
		if (requestCode == 0x01) {
			val obj: HmsScan? = data.getParcelableExtra(ScanUtil.RESULT)
			if (obj != null && obj.originalValue[0]=='/') {
				if(isConnected){
					val tmp="${SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
						.format(Date())} (${obj.originalValue.substringAfter('/')}) " +
							getLocationInfo()
					CoroutineScope(Dispatchers.IO).launch {
						try {
							sendData(tmp)
						}
						catch (e:Exception){
							withContext(Dispatchers.Main){
								Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
							}
						}
					}
					Toast.makeText(this, "发送成功！", Toast.LENGTH_SHORT).show()
				}
				else{
					Toast.makeText(this, "请先连接到服务器！", Toast.LENGTH_SHORT).show()
				}
			}
			else{
				Toast.makeText(this, "无效地点请重新扫描！", Toast.LENGTH_SHORT).show()
			}
		}
	}

}