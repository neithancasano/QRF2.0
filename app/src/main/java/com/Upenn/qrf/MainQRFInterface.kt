package com.Upenn.qrf

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSerializer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Environment
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import com.google.firebase.storage.FirebaseStorage
import java.io.File

import android.net.Uri;
import android.text.Spannable
import android.util.Log
import com.jcraft.jsch.ChannelSftp

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.FileInputStream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import android.content.Context
import android.content.Intent
import com.google.android.material.snackbar.Snackbar
import java.io.FileOutputStream

import android.provider.Settings

import android.content.pm.ActivityInfo
import android.widget.ScrollView

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private lateinit var handler: Handler
private lateinit var runnable: Runnable

class MainQRFInterface : AppCompatActivity() {

    private lateinit var websocketListener: WebsocketListener
    private lateinit var viewModel: MainViewModel
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    private var mediaRecorder: MediaRecorder? = null
    private lateinit var outputFile: String

    var readyToReceive = false
    var globalJsonString = ""
    var recFeedbackFlag = false

    private var isRecording = false
    private var startButtonClicked = false

    // fr: ELAPSED TIME
    private lateinit var recordingTimerHandler: Handler
    private lateinit var recordingRunnable: Runnable
    private var recordingStartTime: Long = 0

    data class MasterLogs(
        var from: String,
        var software: String,
        var timestamp: String,
        var eventID: String,
        var student: String,
        var trigger: String,
        var reviewer: String,
        var end: String,
        var feedbackTXT: String,
        var feedbackREC: String
    )

    data class Data(
        var from: String,
        var software: String,
        var timestamp: String,
        var eventID: String,
        var student: String,
        var trigger: String,
        var masterlogs: MasterLogs,
        var username: String,
        var readytoreceive: String
    )
    data class MyData(
        var event: String,
        var data: Data
    )

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_qrfinterface)

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) // force set portrait orientation only

        // fr: ELAPSED TIME Initialize the Handler and Runnable
        recordingTimerHandler = Handler(Looper.getMainLooper())
        recordingRunnable = object : Runnable {
            override fun run() {
                val elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                val btnRecord: Button = findViewById(R.id.btnRecord)
                btnRecord.text = "⏹️ Tap to Stop Recording... ${minutes}:${seconds.toString().padStart(2, '0')}"
                recordingTimerHandler.postDelayed(this, 1000)
            }
        }

        setupRecordingButton()

        val tvCurrentlyLoggedIn = findViewById<TextView>(R.id.tvCurrentlyLoggedIn)
        val tvStudentInfoAndDetails = findViewById<TextView>(R.id.tvStudentInfoAndDetails)
        val btnDisconnectManually = findViewById<Button>(R.id.btnDisconnectManually)
        val btnNext = findViewById<Button>(R.id.btnNext)
        val edtFeedback = findViewById<EditText>(R.id.edtFeedback)
        // val LLayout1 = findViewById<LinearLayout>(R.id.LLayout1)
        val btnReconnect = findViewById<Button>(R.id.btnReconnect)
        val spnSoftwarePicker = findViewById<Spinner>(R.id.spnSoftwarePicker)
        val tvWaiting = findViewById<TextView>(R.id.tvWaiting)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnSkip = findViewById<Button>(R.id.btnSkip)
        val edtOverride = findViewById<EditText>(R.id.edtOverride)
        val edtOther = findViewById<EditText>(R.id.edtOther)
        val SVLayout1 = findViewById<ScrollView>(R.id.SVLayout1)

        // load the pem file
        copyPemFileToInternalStorage()

        // access variables from previous intent

        val intent = intent
        /*
        if (intent != null) {
            val username = intent.getStringExtra("username")
            if (username != null) {
                tvCurrentlyLoggedIn.text = "Connected as: " + username
            }
        }
        */

        // LLayout1.visibility = View.INVISIBLE
        SVLayout1.visibility = View.INVISIBLE
        btnReconnect.visibility = View.INVISIBLE

        val text = "Welcome!\n\nPress the ready button\n to start receiving triggers."
        val spannableString = SpannableString(text)

        // Apply a style to "Queue Empty"
        val queueEmptySpan = StyleSpan(Typeface.BOLD) // Make "Queue Empty" bold
        val colorSpan = ForegroundColorSpan(Color.rgb(133, 36, 36)) // Change color to dark red
        spannableString.setSpan(queueEmptySpan, 0, "Welcome!".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        spannableString.setSpan(colorSpan, 0, "Welcome!".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        val sizeSpan = RelativeSizeSpan(1.2f) // Increase size by 50%
        spannableString.setSpan(sizeSpan, 0, "Welcome!".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

        tvWaiting.text = spannableString

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        websocketListener = WebsocketListener(viewModel)

        webSocket = okHttpClient.newWebSocket(createRequest(), websocketListener)

        // viewModel.socketStatus.observe checks if phone is connected to websocket or not

        btnReconnect.text = "Reconnecting..."
        viewModel.socketStatus.observe(this, Observer {

            val username = intent.getStringExtra("username")
            tvCurrentlyLoggedIn.text = if (it) "Connected as: " + username else "・・・"

            if (it){
                btnDisconnectManually.text = "Connected ↻"
                btnDisconnectManually.setBackgroundColor(Color.rgb(18,92,126))
            }

            if (!it && startButtonClicked && btnStart.visibility != View.VISIBLE){
                reconnectWebSocket()
            }


            // go to the main QRF Interface
            /*
            if (it) {
                val intent = Intent(this, MainQRFInterface::class.java)
                startActivity(intent)
            }
            */

        })

        data class MasterLogs(
            var from: String,
            var software: String,
            var timestamp: String,
            var eventID: String,
            var student: String,
            var trigger: String,
            var reviewer: String,
            var end: String,
            var feedbackTXT: String,
            var feedbackREC: String
        )

        data class Data(
            var from: String,
            var software: String,
            var timestamp: String,
            var eventID: String,
            var student: String,
            var trigger: String,
            var masterlogs: MasterLogs,
            var username: String,
            var readytoreceive: String
        )

        data class MyData(
            var event: String,
            var data: Data
        )

        // viewModel.message.observe listens for incoming message. 2nd var in pair is the actual message
        // var globalJsonString = ""
        viewModel.message.observe(this, Observer{
            /*
            text += "${if (it.first) "You: " else "Other: "} ${it.second}\n"

            tvMessage.text = text
            if (it.second.startsWith("whimc")){
                tvReceiver.text = "NEITHAN";
            }
            */
            if (readyToReceive) {

                val selectedSoftware = spnSoftwarePicker.selectedItem.toString()
                val jsonString = it.second
                globalJsonString = it.second

                try {
                    val myData = Gson().fromJson(globalJsonString, MyData::class.java)

                    // val selectedSoftware = spnSoftwarePicker.selectedItem.toString()
                    if (myData.data.software == selectedSoftware &&
                        myData.data.from == "dispatcher" &&
                        myData.data.masterlogs.reviewer == intent.getStringExtra("username").toString()) {

                        //==========================================================================
                        // Spannable String for trigger output
                        //==========================================================================

                        val student = myData.data.student
                        val trigger = myData.data.trigger
                        val timestamp = myData.data.timestamp
                        val eventID = myData.data.eventID
                        val masterEventID = myData.data.masterlogs.eventID

                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val date = Date(timestamp.toLong())  // Ensure your timestamp is in milliseconds
                        val formattedDate = sdf.format(date)

                        // Construct the whole text
                        val fullText = "\n$trigger\n\nTrigger ID: $masterEventID\nUsername: $student\n🕓: $formattedDate\n"

                        // Create a SpannableString from the full text
                        val spannable = SpannableString(fullText)

                        // Apply styles
                        // Make the 'Trigger' larger
                        val triggerIndex = fullText.indexOf(trigger)
                        spannable.setSpan(RelativeSizeSpan(1.1f), triggerIndex, triggerIndex + trigger.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                        // Make 'Trigger ID' and 'Username' bold
                        val triggerLabelIndex = fullText.indexOf("Trigger ID:")
                        spannable.setSpan(StyleSpan(Typeface.BOLD), triggerLabelIndex, triggerLabelIndex + "Trigger ID:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                        val usernameLabelIndex = fullText.indexOf("Username:")
                        spannable.setSpan(StyleSpan(Typeface.BOLD), usernameLabelIndex, usernameLabelIndex + "Username:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                        /*
                        val timestampLabelIndex = fullText.indexOf("Timestamp:")
                        spannable.setSpan(StyleSpan(Typeface.BOLD), timestampLabelIndex, timestampLabelIndex + "Timestamp:".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        */

                        // Set the styled text to TextView
                        tvStudentInfoAndDetails.text = spannable

                        //==========================================================================
                        // /Spannable String for trigger output
                        //==========================================================================

                        // tvStudentInfoAndDetails.text =
                        //    "${myData.data.student + ' ' + myData.data.trigger + ' ' + myData.data.eventID + ':' + myData.data.masterlogs.eventID}"

                        // LLayout1.visibility = View.VISIBLE
                        SVLayout1.visibility = View.VISIBLE
                        readyToReceive = false
                    } else {
                        // Handle other cases
                        readyToReceive = true
                        Handler(Looper.getMainLooper()).postDelayed({

                            val text = "Queue Empty"
                            //val text = "Queue Empty\n\nAnother reviewer\naccepted the last\navailable trigger.\n\nWe'll let you know\nwhen another\nbecomes available."
                            val spannableString = SpannableString(text)

                            // Apply a style to "Queue Empty"
                            val queueEmptySpan = StyleSpan(Typeface.BOLD) // Make "Queue Empty" bold
                            val colorSpan = ForegroundColorSpan(
                                Color.rgb(
                                    133,
                                    36,
                                    36
                                )
                            ) // Change color to dark red
                            spannableString.setSpan(
                                queueEmptySpan,
                                0,
                                "Queue Empty".length,
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            )
                            spannableString.setSpan(
                                colorSpan,
                                0,
                                "Queue Empty".length,
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            )
                            val sizeSpan = RelativeSizeSpan(1.2f) // Increase size by 50%
                            spannableString.setSpan(
                                sizeSpan,
                                0,
                                "Queue Empty".length,
                                Spanned.SPAN_INCLUSIVE_INCLUSIVE
                            )

                            tvWaiting.text = spannableString

                            btnStart.visibility = View.INVISIBLE
                        }, 2000)
                    }
                } catch (e: Exception) {
                    // Handle JSON parsing error
                }


            }

        })


        btnDisconnectManually.setOnClickListener {

            // Send message to dispatcher first (so this reviewer gets taken off the available reviewers list)

            var gson = Gson()
            var myData = gson.fromJson("{'event':'new_message','data':{'from':'devicedisconnect', 'readytoreceive':'no','username':'" + intent.getStringExtra("username").toString() + "','software':'" + spnSoftwarePicker.selectedItem.toString() + "'}}", MyData::class.java)

            // serialize data here
            val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
                Gson().toJsonTree(src)
            }
            gson = GsonBuilder()
                .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
                .create()
            val compactJsonString = gson.toJson(myData)

            webSocket?.send(compactJsonString.toString())
            viewModel.setMessage(Pair(true, compactJsonString.toString()))

            // Then close connection

            webSocket?.close(1000, "Cancelled Manually")
            btnDisconnectManually.visibility = View.INVISIBLE
            btnReconnect.visibility = View.VISIBLE
            tvCurrentlyLoggedIn.text = "・・・"
        }

        btnReconnect.setOnClickListener {
            webSocket = okHttpClient.newWebSocket(createRequest(), websocketListener)
            btnDisconnectManually.visibility = View.VISIBLE
            btnReconnect.visibility = View.INVISIBLE

            readyToReceive = true;

            var gson = Gson()
            var myData = gson.fromJson("{'event':'new_message','data':{'from':'deviceready', 'readytoreceive':'yes','username':'" + intent.getStringExtra("username").toString() + "','software':'" + spnSoftwarePicker.selectedItem.toString() + "'}}", MyData::class.java)

            // serialize data here
            val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
                Gson().toJsonTree(src)
            }
            gson = GsonBuilder()
                .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
                .create()
            val compactJsonString = gson.toJson(myData)

            webSocket?.send(compactJsonString.toString())
            viewModel.setMessage(Pair(true, compactJsonString.toString()))

        }

        btnStart.setOnClickListener {

            readyToReceive = true;
            startButtonClicked = true

            var gson = Gson()
            var myData = gson.fromJson("{'event':'new_message','data':{'from':'deviceready', 'readytoreceive':'yes','username':'" + intent.getStringExtra("username").toString() + "','software':'" + spnSoftwarePicker.selectedItem.toString() + "'}}", MyData::class.java)

            // serialize data here
            val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
                Gson().toJsonTree(src)
            }
            gson = GsonBuilder()
                .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
                .create()
            val compactJsonString = gson.toJson(myData)

            webSocket?.send(compactJsonString.toString())
            viewModel.setMessage(Pair(true, compactJsonString.toString()))


        }

        btnNext.setOnClickListener {
            // if (edtFeedback.text.toString().isNotEmpty()){
                val btnRecord: Button = findViewById(R.id.btnRecord)
                if(isRecording) {
                    // Stop recording
                    btnRecord.text = "🔴 Tap to Record Feedback"
                    stopRecording()
                    isRecording = false
                }

                readyToReceive = true

                var gson = Gson()
                var myData = gson.fromJson(globalJsonString, MyData::class.java)

                myData.data.from = "device"
                myData.data.masterlogs.reviewer = intent.getStringExtra("username").toString()

                // OLD Feedback texts
                // myData.data.masterlogs.feedbackTXT = edtFeedback.text.toString()

                // New feedback text we're also trying to pass override and other person in conversation data
                // Start with feedback text
                var feedbackText = edtFeedback.text.toString()

                // Append override and other fields if they contain text
                val overrideText = edtOverride.text.toString()
                val otherText = edtOther.text.toString()

                if (overrideText.isNotEmpty()) {
                    feedbackText += "\nOverride: $overrideText"
                }

                if (otherText.isNotEmpty()) {
                    feedbackText += "\nOther: $otherText"
                }

                myData.data.masterlogs.feedbackTXT = feedbackText

                if (recFeedbackFlag == true) {
                    // myData.data.masterlogs.feedbackREC = myData.data.eventID + ".3gp"
                    myData.data.masterlogs.feedbackREC = myData.data.eventID + ".mp4"
                    recFeedbackFlag = false
                }
                else
                    myData.data.masterlogs.feedbackREC = "No audio feedback"

                myData.data.masterlogs.end = System.currentTimeMillis().toString()

                myData.data.masterlogs.eventID = myData.data.eventID

                // serialize data here
                val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
                    Gson().toJsonTree(src)
                }
                gson = GsonBuilder()
                    .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
                    .create()
                val compactJsonString = gson.toJson(myData)

                webSocket?.send(compactJsonString.toString())
                viewModel.setMessage(Pair(true, compactJsonString.toString()))

                val text = "Queue Empty"
                val spannableString = SpannableString(text)

                // Apply a style to "Queue Empty"
                val queueEmptySpan = StyleSpan(Typeface.BOLD) // Make "Queue Empty" bold
                val colorSpan = ForegroundColorSpan(Color.rgb(133, 36, 36)) // Change color to dark red
                spannableString.setSpan(queueEmptySpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                spannableString.setSpan(colorSpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                val sizeSpan = RelativeSizeSpan(1.2f) // Increase size by 50%
                spannableString.setSpan(sizeSpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

                tvWaiting.text = spannableString
                btnStart.visibility = View.INVISIBLE
                edtFeedback.setText("")

                // LLayout1.visibility = View.INVISIBLE
                SVLayout1.visibility = View.INVISIBLE

                edtOverride.setText("")
                edtOther.setText("")
                /*
                webSocket?.send(edtFeedback.text.toString())
                viewModel.setMessage(Pair(true, edtFeedback.text.toString()))

                tvWaiting.text = "Feedback sent!\nWaiting for next trigger."
                edtFeedback.setText("")

                LLayout1.visibility = View.INVISIBLE;
                 */
            /*
            }
            else{
                Toast.makeText(this, "can't send blank message", Toast.LENGTH_LONG).show()
            }
            */


        }

        btnSkip.setOnClickListener {

                readyToReceive = true

                var gson = Gson()
                var myData = gson.fromJson(globalJsonString, MyData::class.java)

                myData.data.from = "device"
                myData.data.masterlogs.reviewer = intent.getStringExtra("username").toString()
                myData.data.masterlogs.feedbackTXT = "skipped"
                myData.data.masterlogs.feedbackREC = "skipped"
                myData.data.masterlogs.end = System.currentTimeMillis().toString()

                myData.data.masterlogs.eventID = myData.data.eventID

                // serialize data here
                val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
                    Gson().toJsonTree(src)
                }
                gson = GsonBuilder()
                    .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
                    .create()
                val compactJsonString = gson.toJson(myData)

                webSocket?.send(compactJsonString.toString())
                viewModel.setMessage(Pair(true, compactJsonString.toString()))

                val text = "Queue Empty"
                val spannableString = SpannableString(text)

                // Apply a style to "Queue Empty"
                val queueEmptySpan = StyleSpan(Typeface.BOLD) // Make "Queue Empty" bold
                val colorSpan = ForegroundColorSpan(Color.rgb(133, 36, 36)) // Change color to dark red
                spannableString.setSpan(queueEmptySpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                spannableString.setSpan(colorSpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                val sizeSpan = RelativeSizeSpan(1.2f) // Increase size by 50%
                spannableString.setSpan(sizeSpan, 0, "Queue Empty".length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)

                tvWaiting.text = spannableString
                btnStart.visibility = View.INVISIBLE;
                edtFeedback.setText("")

                // LLayout1.visibility = View.INVISIBLE
                SVLayout1.visibility = View.INVISIBLE

                edtOverride.setText("")
                edtOther.setText("")            
                /*
                webSocket?.send(edtFeedback.text.toString())
                viewModel.setMessage(Pair(true, edtFeedback.text.toString()))

                tvWaiting.text = "Feedback sent!\nWaiting for next trigger."
                edtFeedback.setText("")

                LLayout1.visibility = View.INVISIBLE;
                 */

        }

        /*
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)
        }
        */

        checkPermissions()

        /*
        val btnRecord: Button = findViewById(R.id.btnRecord)
        btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    btnRecord.text = "⏹️ Release to Stop Recording..."
                    startRecording()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    btnRecord.text = "🔴 Record Feedback"
                    stopRecording()
                    true
                }
                else -> false
            }
        }
        */

        val btnRecord: Button = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            if (!isRecording) {
                // Start recording
                btnRecord.text = "⏹️ Tap to Stop Recording..."
                startRecording()
                isRecording = true
            } else {
                // Stop recording
                btnRecord.text = "🔴 Tap to Record Feedback"
                stopRecording()
                isRecording = false
            }
        }

        /*
        val btnRecord: Button = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            if (checkPermissions()) {
                toggleRecording()
            } else {
                Toast.makeText(this, "Permissions are required to record audio.", Toast.LENGTH_SHORT).show()
            }
        }
        */

    }
    
    override fun onPause() {
        super.onPause()
        disconnectManually()
    }

    override fun onStop() {
        super.onStop()
        disconnectManually()
    }

    override fun onResume() {
        super.onResume()
        if (startButtonClicked) reconnectWebSocket()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectManually()
    }

    private fun createRequest(): Request {
        val webSocketUrl = "wss://free.blr2.piesocket.com/v3/qrfchannel?api_key=asdffdFIjgKLDdZJ0zwoKpzn5ydd7Y&notify_self=1"
        // val webSocketUrl = "wss://free.blr2.piesocket.com/v3/qrfinstance?api_key=asdfddfgKLDdZJ0zwoKpzn5ydd7Y&notify_self=1"
        return Request.Builder()
            .url(webSocketUrl)
            .build()
    }

    private fun reconnectWebSocket() {
        webSocket = okHttpClient.newWebSocket(createRequest(), websocketListener)

        findViewById<Button>(R.id.btnDisconnectManually).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnReconnect).visibility = View.INVISIBLE

        readyToReceive = true;

        var gson = Gson()
        var myData = gson.fromJson("{'event':'new_message','data':{'from':'deviceready', 'readytoreceive':'yes','username':'" + intent.getStringExtra("username").toString() + "','software':'" + findViewById<Spinner>(R.id.spnSoftwarePicker).selectedItem.toString() + "'}}", MyData::class.java)

        // serialize data here
        val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
            Gson().toJsonTree(src)
        }
        gson = GsonBuilder()
            .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
            .create()
        val compactJsonString = gson.toJson(myData)

        webSocket?.send(compactJsonString.toString())
        viewModel.setMessage(Pair(true, compactJsonString.toString()))
    }

    private fun disconnectManually() {
        // Code that was inside your btnDisconnectManually click listener
        var gson = Gson()
        var myData = gson.fromJson("{'event':'new_message','data':{'from':'devicedisconnect', 'readytoreceive':'no','username':'" + intent.getStringExtra("username").toString() + "','software':'" + findViewById<Spinner>(R.id.spnSoftwarePicker).selectedItem.toString() + "'}}", MyData::class.java)

        // serialize data here
        val compactJsonSerializer = JsonSerializer<Any> { src, _, _ ->
            Gson().toJsonTree(src)
        }
        gson = GsonBuilder()
            .registerTypeAdapter(MyData::class.java, compactJsonSerializer)
            .create()
        val compactJsonString = gson.toJson(myData)

        webSocket?.send(compactJsonString.toString())
        viewModel.setMessage(Pair(true, compactJsonString.toString()))

        // Then close connection
        webSocket?.close(1000, "Cancelled Manually")
        findViewById<Button>(R.id.btnDisconnectManually).visibility = View.INVISIBLE
        findViewById<Button>(R.id.btnReconnect).visibility = View.VISIBLE
    }


    private fun checkPermissions() {
        val permissionsNeeded = listOf(
            Manifest.permission.RECORD_AUDIO,
            // Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val permissionsToRequest = permissionsNeeded.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 111)
            Log.d("PermissionsNeithan", "Requesting permissions: ${permissionsToRequest.joinToString()}")
        } else {
            Log.d("PermissionsNeithan", "All permissions already granted")
            Toast.makeText(this, "All app permissions granted.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111) {
            // Convert grantResults from IntArray to a List<Int> for safe zipping
            val resultsList = grantResults.toList()

            // Zip permissions (Array<out String>) with resultsList (List<Int>)
            val permissionResults = permissions.zip(resultsList)

            // Filter to find denied permissions
            val deniedPermissions = permissionResults.filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first } // Extract the permission names

            if (deniedPermissions.isNotEmpty()) {
                // Log and show toast if there are any denied permissions
                if (deniedPermissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    showSettingsSnackbar()
                } else {
                    val message = "Permissions denied: ${deniedPermissions.joinToString()}. Cannot record."
                    Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
                }

                // Toast.makeText(this, "Permissions denied: ${deniedPermissions.joinToString()}. Cannot record.", Toast.LENGTH_LONG).show()
            } else {
                // All permissions were granted
                Log.d("Permissions", "All requested permissions have been granted")
                Toast.makeText(this, "All app permissions granted.", Toast.LENGTH_SHORT).show()
                // Here you could initiate further actions that depend on these permissions
            }
        }
    }

    private fun showSnackbar(message: String) {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, message, Snackbar.LENGTH_INDEFINITE)
            .setAction("OK") { }
            .show()
    }

    private fun showSettingsSnackbar() {
        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "Permission denied. You can enable it in app settings.", Snackbar.LENGTH_INDEFINITE)
            .setAction("Settings") {
                openAppSettings()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }
    /*
    private fun checkPermissions(): Boolean {
        val permissionsRequired = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val allPermissionsGranted = permissionsRequired.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissionsRequired, 111)
        }

        return allPermissionsGranted
    }
    */
    /*
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 111) { // Make sure the request code matches
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All requested permissions have been granted
                // toggleRecording()
                Toast.makeText(this, "All recorded permissions have been granted.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions denied. Cannot record.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    */

    // fr: ELAPSED TIME
    private fun setupRecordingButton() {
        val btnRecord: Button = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener {
            if (!isRecording) {
                startRecording()
            } else {
                stopRecording()
            }
        }
    }

    private fun toggleRecording() {
        val btnRecord: Button = findViewById(R.id.btnRecord)
        if (!isRecording) {
            // Start recording
            btnRecord.text = "⏹️ Tap to Stop Recording..."
            startRecording()
            isRecording = true
        } else {
            // Stop recording
            btnRecord.text = "🔴 Tap to Record Feedback"
            stopRecording()
            isRecording = false
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)  // Set container format to MP4
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)  // Set audio encoding to AAC

            val myData = Gson().fromJson(globalJsonString, MyData::class.java)
            val eventID = myData.data.eventID

            /*
            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            outputFile = File(downloadsFolder, "$eventID.mp4").absolutePath  // Save as .mp4
            setOutputFile(outputFile)
            */

            val appExternalFilesDir = getExternalFilesDir(null)
            outputFile = File(appExternalFilesDir, "$eventID.mp4").absolutePath  // Save as .mp4
            setOutputFile(outputFile)
            Log.d("NeithanFiles", outputFile)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // fr: ELAPSED TIME
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        recordingTimerHandler.post(recordingRunnable) // Start timer
    }

    /*
    private fun startRecording() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            val myData = Gson().fromJson(globalJsonString, MyData::class.java)
            val eventID = myData.data.eventID

            val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            // outputFile = File(downloadsFolder, "qrf_FeedbackREC.3gp").absolutePath
            outputFile = File(downloadsFolder, eventID + ".3gp").absolutePath
            setOutputFile(outputFile)

            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    */

    private fun stopRecording() {
        mediaRecorder?.run {
            stop()
            release()
        }
        mediaRecorder = null

        // fr: ELAPSED TIME
        isRecording = false
        recordingTimerHandler.removeCallbacks(recordingRunnable) // Stop timer
        val btnRecord: Button = findViewById(R.id.btnRecord)
        btnRecord.text = "🔴 Tap to Record Feedback"

        // After stopping the recording, upload the file in the background
        Log.d("UploadFile", "Attempting to upload file.")

        recFeedbackFlag = true

        CoroutineScope(Dispatchers.IO).launch {
            uploadFileSFTP(outputFile)
        }
    }

    private fun copyPemFileToInternalStorage() {
        val pemInputStream = resources.openRawResource(R.raw.qrf)  // replace your_pem_file_name with the actual file name without extension
        val pemFile = File(filesDir, "qrf.pem")

        pemInputStream.use { input ->
            FileOutputStream(pemFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun uploadFileSFTP(filePath: String) {
        val privateKeyPath = File(filesDir, "qrf.pem").absolutePath  // Path to your PEM file in internal storage

        try {
            val jsch = JSch()
            jsch.addIdentity(privateKeyPath)

            val server = "52.220.75.253"  // Your server IP
            val user = "bitnami"  // Your server username
            val port = 22

            val session = jsch.getSession(user, server, port)
            session.setConfig("StrictHostKeyChecking", "no")  // For testing purposes

            session.connect()

            val channel = session.openChannel("sftp") as ChannelSftp
            channel.connect()
            Log.d("UploadFile", "channel connected")

            val myData = Gson().fromJson(globalJsonString, MyData::class.java)
            val eventID = myData.data.eventID

            // channel.put(filePath, "/opt/bitnami/wordpress/minihoster/qrf/" + eventID + ".3gp")  // Remote path where the file will be saved
            channel.put(filePath, "/opt/bitnami/wordpress/minihoster/qrf/" + eventID + ".mp4")  // Remote path where the file will be saved

            channel.disconnect()
            session.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun uploadAudioToFirebaseStorage() {
        val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val outputFile = File(downloadsFolder, "qrf_FeedbackREC.3gp").absolutePath

        val file = Uri.fromFile(File(outputFile))
        val storageReference = FirebaseStorage.getInstance().reference.child("audio/${file.lastPathSegment}")

        storageReference.putFile(file)
            .addOnSuccessListener {
                // Use the reference to get the download URL
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    // Now you have the download URL
                    Toast.makeText(this, "Upload Successful: $downloadUrl", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                // Handle any errors
                Toast.makeText(this, "Upload failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }
}


