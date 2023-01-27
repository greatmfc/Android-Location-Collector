package com.example.LocationCollector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.LocationCollector.databinding.ActivitySftBinding
import kotlinx.coroutines.*
import java.io.*
import java.net.Socket
import java.util.Arrays

class SftActivity : AppCompatActivity() {
	private lateinit var binding:ActivitySftBinding
	private var isFileChosen:Boolean=false
	private var fileToSend:String=""
	private lateinit var context: Context
	private var connectDescriptor: Socket?=null
	private var connectStream:OutputStream?=null
	private var currentIsConnected=false
	private val maxArraySize=200000000

	private val requestDataLauncher = registerForActivityResult(ActivityResultContracts
		.StartActivityForResult()){
		result->
		run {
			if (result.resultCode == RESULT_OK) {
				fileToSend=Uri.decode(result.data?.data.toString()).substringAfterLast(':')
				Log.d("SftActivity",fileToSend)
				isFileChosen=true
				binding.textFileToSend.text=fileToSend
				binding.textFileToSend.setAutoSizeTextTypeWithDefaults(
					TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM)
				Toast.makeText(this, "Press the button again to send file.", Toast.LENGTH_SHORT)
					.show()
			}
			else{
				Toast.makeText(this, "Need to choose file!", Toast.LENGTH_SHORT).show()
				Log.e("SftActivity","Fail from intent")
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivitySftBinding.inflate(layoutInflater)
		setContentView(binding.root)
		context=this
		initUI()
		testConnection(MainActivity.ip,MainActivity.port)
	}

	override fun onDestroy() {
		super.onDestroy()
		connectStream?.close()
		connectDescriptor?.close()
	}

	private fun initUI(){
		binding.buttonGetFIle.setOnClickListener{
			if(binding.editTextFileToGet.text.toString()==""){
				Toast.makeText(this, "Input file name to get!", Toast.LENGTH_SHORT).show()
			}
			else{
				val fileToGet=binding.editTextFileToGet.text.toString()
				binding.editTextFileToGet.text.clear()
				toGetFile(fileToGet)
			}
		}
		binding.buttonSendFile.setOnClickListener{
			if(isFileChosen){
				if(fileToSend!=""){
					toSendFile(fileToSend)
				}
				else{
					Toast.makeText(this, "Please select a file first!", Toast.LENGTH_SHORT).show()
				}
			}
			else{
				Log.d("SftActivity","buttonSendFile pressed.")
				val intent=Intent(Intent.ACTION_GET_CONTENT)
				intent.type = "*/*"
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false)
				intent.addCategory(Intent.CATEGORY_OPENABLE)
				requestDataLauncher.launch(intent)
			}
		}
		binding.buttonCancelSelected.setOnClickListener{
			isFileChosen=false
			binding.textFileToSend.text="File Chose To Send"
		}
	}

	private fun toGetFile(name:String){
		Log.d("SftActivity","toGetFile is called")
		try{
			if(name.indexOf('/')!=-1){
				throw IllegalArgumentException("Argument contains illegal character '/' .")
			}
			val requestMessage="g/$name"
			if(!currentIsConnected){
				throw RuntimeException("Server is not connected.")
			}
			CoroutineScope(Dispatchers.IO).launch {
				try {
					connectStream?.write(requestMessage.toByteArray())
					val reader =
						DataInputStream(connectDescriptor?.getInputStream())
					val msgBuffer = ByteArray(64)
					reader.read(msgBuffer, 0, 64)
					val messageGet = msgBuffer.decodeToString()
					Log.d("Sft", messageGet)
					val sizeOfFile =
						messageGet.substringAfterLast('/').replace("[^0-9]*".toRegex(), "")
							.toInt()
					Log.d("Sft", "$sizeOfFile")
					if (messageGet[0] != '0') {
						connectStream?.write("1".toByteArray())
						val parent = Environment.getExternalStorageDirectory().path.toString()
						val child = "$parent/sft"
						File(child).mkdirs()
						val fileInstance = File("$child/$name")
						fileInstance.createNewFile()
						val outputFIleWriter = FileOutputStream(fileInstance)
						delay(100)
						if(sizeOfFile < maxArraySize){
							val bufferForFile = ByteArray(sizeOfFile)
							var ret=0
							var bytesLeft:Int=sizeOfFile
							while(true){
								ret+=reader.read(bufferForFile,ret,bytesLeft)
								bytesLeft=sizeOfFile-ret
								if(bytesLeft<=0) break
							}
							outputFIleWriter.write(bufferForFile)
						}
						else{
							val bufferForFile = ByteArray(maxArraySize)
							var ret=0
							var bytesWritten=ret
							while(true){
								var currentReturn=0
								while(ret<(maxArraySize-200000)) {
									currentReturn=reader.read(bufferForFile, ret, maxArraySize-ret)
									if (currentReturn<=0) break
									ret+=currentReturn
									if(ret+bytesWritten>=sizeOfFile) break
								}
								if(currentReturn<=0) break
								outputFIleWriter.write(bufferForFile,0,ret)
								bytesWritten+=ret
								if(bytesWritten>=sizeOfFile) break
								Arrays.fill(bufferForFile,0)
								ret=0
							}
						}
						outputFIleWriter.close()
					}
					withContext(Dispatchers.Main) {
						if (messageGet[0] != '0') {
							Toast.makeText(context, "Success on file fetching!", Toast.LENGTH_SHORT)
								.show()
						} else {
							Toast.makeText(
								context,
								"No requested file found in target server.",
								Toast.LENGTH_SHORT
							).show()
						}
					}
				}
				catch (e:Exception) {
					withContext(Dispatchers.Main) {
						AlertDialog.Builder(context).apply {
							setTitle("Error in file getting")
							setMessage(e.toString())
							setCancelable(false)
							setNegativeButton("OK") { _, _ -> }
							show()
						}
					}
					connectStream?.close()
				}
			}
		}
		catch (e:Exception) {
			AlertDialog.Builder(this).apply {
				setTitle("Error in argument processing")
				setMessage(e.toString())
				setCancelable(false)
				setNegativeButton("OK") { _, _ -> }
				show()
			}
		}
	}

	private fun toSendFile(name: String){
		Log.d("SftActivity","toSendFile is called")
		try {
			if (!Environment.isExternalStorageManager()) {
				val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
				intent.data = Uri.parse("package:$packageName")
				startActivity(intent)
			}
			val completePath=Environment.getExternalStorageDirectory().path.toString()+"/$name"
			val file=File(completePath)
			Log.d("SftActivity",completePath)
			val reader=BufferedInputStream(file.inputStream())
			val fileSize=file.length().toInt()
			var sizeOutOfBound=false
			val buf:ByteArray
			if(fileSize>maxArraySize){
				buf=ByteArray(maxArraySize)
				sizeOutOfBound=true
			}
			else {
				buf = ByteArray(fileSize)
			}
			val preMessage="f/${file.name}/${file.length()}"
			if(!currentIsConnected) throw RuntimeException("Server is not connected.")
			CoroutineScope(Dispatchers.IO).launch{
				try {
					connectStream?.write(preMessage.toByteArray())
					connectDescriptor?.getInputStream()?.read()
					var ret: Int
					if (sizeOutOfBound) {
						while (true) {
							ret = reader.read(buf,0,maxArraySize)
							if (ret <= 0) break
							connectStream?.write(buf,0,ret)
							Arrays.fill(buf, 0)
						}
					} else {
						reader.read(buf)
						connectStream?.write(buf)
					}
					withContext(Dispatchers.Main) {
						Toast.makeText(context, "Send file to server success!", Toast.LENGTH_SHORT)
							.show()
					}
				}
				catch (e:Exception){
					withContext(Dispatchers.Main) {
						AlertDialog.Builder(context).apply {
							setTitle("Error in file sending")
							setMessage(e.toString())
							setCancelable(false)
							setNegativeButton("OK") { _, _ -> }
							show()
						}
					}
				}
			}
		}
		catch (e:Exception){
			AlertDialog.Builder(this).apply {
				setTitle("Error in file sending")
				setMessage(e.toString())
				setCancelable(false)
				setNegativeButton("OK") { _, _ -> }
				show()
			}
		}
	}

	private fun testConnection(ip:String?,port:Int){
		if(!currentIsConnected) {
			CoroutineScope(Dispatchers.IO).launch {
				try {
					connectDescriptor = Socket(ip, port)
					connectStream = connectDescriptor?.getOutputStream()
					currentIsConnected = true
				}
				catch (e:Exception){
					withContext(Dispatchers.Main) {
						AlertDialog.Builder(context).apply {
							setTitle("Error while trying to connect")
							setMessage(e.toString())
							setCancelable(false)
							setNegativeButton("OK") { _, _ -> }
							show()
						}
					}

				}
			}
		}
	}
}