package com.example.testapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.databinding.ActivityMainBinding
import java.io.OutputStream
import java.lang.Exception
import java.net.Socket

class MainActivity : AppCompatActivity(), View.OnClickListener {

	private lateinit var binding: ActivityMainBinding

	private var ip: String? = null
	private var port: Int = 0
	private var alreadyConnectToServer: Boolean = false
	private var communicationDescriptor: Socket? = null
	private var writeStream: OutputStream? = null
	private var timeInterval: Int = 5

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		this.binding.button1.setOnClickListener(this)
		this.binding.buttonTimeSet.setOnClickListener(this)
	}

	override fun onClick(v: View?) {
		when (v?.id) {
			R.id.button1 -> {
				if (alreadyConnectToServer) {
					Toast.makeText(this, "Success on connecting to server.", Toast.LENGTH_SHORT)
						.show()
				} else {
					val inputText = this.binding.editText.text.toString()
					//Toast.makeText(this,inputText,Toast.LENGTH_SHORT).show()
					checkAddressAvailable(inputText)
					this.binding.editText.setText("")
				}
			}
			R.id.button_timeSet -> {
				if(this.binding.editTextNumber.text.toString()==""){
					Toast.makeText(
						this,
						"Number needed!",
						Toast.LENGTH_SHORT
					).show()
					return
				}
				timeInterval = this.binding.editTextNumber.text.toString().toInt()
				Toast.makeText(
					this,
					"Success on setting time interval to: " + timeInterval.toString(),
					Toast.LENGTH_SHORT
				).show()
				this.binding.editTextNumber.setText("")
			}
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
		tryConnectToServer()
	}

	private fun tryConnectToServer() = try {
		communicationDescriptor = Socket(ip, port)
		writeStream = communicationDescriptor?.getOutputStream()
		alreadyConnectToServer = true
		Toast.makeText(this, "Success on connecting to server.", Toast.LENGTH_SHORT).show()
	} catch (e: Exception) {
		Log.e("MainActivity", e.toString())
		Toast.makeText(
			this,
			"Failed to connect to server: " + e.toString(),
			Toast.LENGTH_SHORT
		).show()
	}
}