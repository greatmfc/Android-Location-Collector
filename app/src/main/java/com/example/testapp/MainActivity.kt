package com.example.testapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.lang.Exception
import java.net.Socket as Socket

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding

	private var ip: String? = null
	private var port: Int = 0
	private var alreadyConnectToServer: Boolean = false
	private var communicationDescriptor: Socket? = null
	private var writeStream: OutputStream? = null
	private var timeInterval: Int = 5
	private val timeToQuitInterval:Int=2000
	private var lastPressedTime:Long=0
	private lateinit var context: Context

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		context=this
		initUI()
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
					.toString().toInt()
				Toast.makeText(
					this,
					"Success on setting time interval to: "
							+ timeInterval.toString(),
					Toast.LENGTH_SHORT
				).show()
				this.binding.editTextNumber.setText("")
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
				Toast.makeText(context,
					"Success on connecting to server.", Toast
						.LENGTH_SHORT)
					.show()
			} catch (e: Exception) {
				Log.e("MainActivity", e.toString())
				Toast.makeText(
					context,
					"Failed to connect to server: "
							+ e.toString(),
					Toast.LENGTH_SHORT
				).show()
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
		}
	}

}