package com.example.testapp

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.SocketException
import kotlin.Exception
import kotlin.concurrent.thread
import java.net.Socket as Socket

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding

	private var ip: String? = null
	private var port: Int = 0
	private var alreadyConnectToServer: Boolean = false
	private var communicationDescriptor: Socket? = null
	private var writeStream: OutputStream? = null
	private var timeInterval: Long = 5000
	private val timeToQuitInterval:Int=2000
	private var lastPressedTime:Long=0
	private var keepRunning:Boolean=true
	private lateinit var context: Context
	private lateinit var locationManager:LocationManager
	private val permissionList= listOf(
		android.Manifest.permission.INTERNET,
		android.Manifest.permission.ACCESS_FINE_LOCATION,
		android.Manifest.permission.ACCESS_COARSE_LOCATION,
		android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
	)
	companion object {
		const val LOCATION_REQUEST_CODE = 1
		const val LISTENER_REQUEST_CODE = 2
	}
	private var locationListener: LocationListener = object : LocationListener {
		override fun onLocationChanged(location: Location) {
			Log.i("MainActivity", "onLocationChanged: 经纬度发生变化")
		}

		override fun onProviderDisabled(provider: String) {
			Log.i("MainActivity", "onProviderDisabled: ")
		}

		override fun onProviderEnabled(provider: String) {
			Log.i("MainActivity", "onProviderEnabled: ")
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		locationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		context=this
		initUI()
		PermissionUtil.requestPermission(LOCATION_REQUEST_CODE,permissionList,this)
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
					Toast.makeText(this, "没有权限", Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	private fun initUI(){
		binding.button1.setOnClickListener{
			if (alreadyConnectToServer) {
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
				if(alreadyConnectToServer){
					Toast.makeText(this,"已开启！",Toast.LENGTH_SHORT).show()
					keepRunning=true
					CoroutineScope(Dispatchers.IO).launch {
						while (keepRunning){
							try {
								if(!isLocationServiceOpen()){
									withContext(Dispatchers.Main){
										AlertDialog.Builder(context).apply {
											setTitle("连接失败")
											setMessage("请打开定位服务！")
											setCancelable(false)
											setNegativeButton("OK"){ _, _ ->}
											show()
										}
										keepRunning=false
										binding.switch1.toggle()
									}
									break
								}
								sendData(getLocationInfo())
								delay(timeInterval)
							}
							catch (a:SocketException){
								var reConnectTimes=5
								while (reConnectTimes>0) try {
									communicationDescriptor = Socket(ip, port)
									writeStream = communicationDescriptor?.getOutputStream()
									break
								}
								catch (c:SocketException){
									delay(1000)
									reConnectTimes--
									Log.e("MainActivity","尝试重连中")
									if (reConnectTimes==0){
										withContext(Dispatchers.Main){
											AlertDialog.Builder(context).apply {
												setTitle("连接失败")
												setMessage(a.toString())
												setCancelable(false)
												setNegativeButton("OK"){ _, _ ->}
												show()
											}
											alreadyConnectToServer=false
											keepRunning=false
											binding.textView.text = "未连接"
											binding.switch1.toggle()
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
				Toast.makeText(this,"已关闭！",Toast.LENGTH_SHORT).show()
			}
		}
		binding.buttonCancelConnection.setOnClickListener {
			if(alreadyConnectToServer){
				communicationDescriptor?.close()
				writeStream?.close()
				alreadyConnectToServer=false
				keepRunning=false
				binding.switch1.toggle()
				Toast.makeText(this,"已关闭连接！",Toast.LENGTH_SHORT).show()
				binding.textView.text = "未连接"
			}
			else{
				Toast.makeText(this,"请先开启一个连接",Toast.LENGTH_SHORT).show()
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		if(alreadyConnectToServer){
			writeStream?.close()
			communicationDescriptor?.close()
		}
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
					alreadyConnectToServer = true
				}
				binding.textView.text = "已连接"
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
			finish()
			onDestroy()
		}
	}

	private fun sendData(targetString: String?){
		val tmp= "m/$targetString"
		val messagesToSend=tmp.toByteArray()
		writeStream?.write(messagesToSend)
		//writeStream?.flush()
	}

	private fun isLocationServiceOpen(): Boolean {
		val gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
		val network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
		return gps || network
	}

	private fun getLocationInfo() : String{
		//判断是否开启位置服务，没有则跳转至设置来开启
		var returnString=""
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
				returnString="Latitude:${it.latitude}/Longitude:${it.longitude}"
			}
			//（四）若所支持的provider获取到的位置均为空，则开启连续定位服务
			if (betterLocation == null) {
				for (provider in locationManager.getProviders(true)) {
					locationMonitor(provider)
				}
				Log.i("MainActivity", "getLocationInfo: 获取到的经纬度均为空，已开启连续定位监听")
				return "No located present"
			}
		} else {
			Log.e("MainActivity","请跳转到系统设置中打开定位服务")
			return "No located present"
		}
		return returnString
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
	private fun locationMonitor(provider: String) {
		if (PermissionUtil.requestPermission(LISTENER_REQUEST_CODE,permissionList,this)) {
			try {
				thread {
					Looper.prepare()
					locationManager.requestLocationUpdates(
						provider,
						60000.toLong(),        //超过1分钟则更新位置信息
						8.toFloat(),        //位置超过8米则更新位置信息
						locationListener
					)
					Looper.loop()
				}
			}
			catch (e:SecurityException){
				Log.e("MainActivity","false")
			}
		}
	}
}