package com.Upenn.qrf

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

class MainActivity : AppCompatActivity() {

    private lateinit var websocketListener: WebsocketListener
    private lateinit var viewModel: MainViewModel
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null

    private val masterPassword = "qrf2024"

    /*
    val userCredentials = mapOf(
        "neithan" to "neithan",
        "ryan" to "ryan",
        "jaclyn" to "jaclyn",
        "luc" to "luc",
        "jack" to "jack",
        "geph" to "geph",
        "amanda" to "amanda"
    )
    */

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnConnect = findViewById<Button>(R.id.btnConnect);
        val btnDisconnect = findViewById<Button>(R.id.btnDisconnect);
        val tvMessage = findViewById<TextView>(R.id.tvMessage);
        val edtMessage = findViewById<EditText>(R.id.edtMessage);
        val btnSend = findViewById<Button>(R.id.btnSend);
        val edtUsername = findViewById<EditText>(R.id.edtUsername);
        val edtPassword = findViewById<EditText>(R.id.edtPassword);


        // additional code for QRF interaction
        val tvReceiver = findViewById<TextView>(R.id.tvReceiver);

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        websocketListener = WebsocketListener(viewModel)

        // viewModel.socketStatus.observe checks if phone is connected to websocket or not
        viewModel.socketStatus.observe(this, Observer {
            tvMessage.text = if (it) "Connected" else "Disconnected"

            // go to the main QRF Interface
            /*
            if (it) {
                val intent = Intent(this, MainQRFInterface::class.java)
                startActivity(intent)
            }
            */

        })

        var text = ""

        // viewModel.message.observe is the listener for incoming messages.
        // var it is composed of a pair comprising of a boolean and a string. The string is the data
        // passed

        viewModel.message.observe(this, Observer{
            text += "${if (it.first) "You: " else "Other: "} ${it.second}\n"
            tvMessage.text = text

            if (it.second.startsWith("whimc")){
                tvReceiver.text = "NEITHAN";
            }
        })

        fun isPasswordValid(password: String, masterPassword: String): Boolean {
            return password == masterPassword
        }

        /*
        fun areCredentialsValid(username: String, password: String, credentialsMap: Map<String, String>): Boolean {
            return credentialsMap[username]?.equals(password) ?: false
        }
        */

        /*
        btnConnect.setOnClickListener {
            // webSocket = okHttpClient.newWebSocket(createRequest(), websocketListener)
            if (areCredentialsValid(edtUsername.text.toString(),edtPassword.text.toString(), userCredentials)){
                val intent = Intent(this, MainQRFInterface::class.java)
                intent.putExtra("username", edtUsername.text.toString())
                startActivity(intent)
            }
            else {
                Toast.makeText(this, "Invalid credentials, please retry or contact QRF admin.", Toast.LENGTH_LONG).show()
            }
            /*
            val intent = Intent(this, MainQRFInterface::class.java)
            intent.putExtra("username", edtUsername.text.toString())
            startActivity(intent)
            */

        }
        */

        btnConnect.setOnClickListener {
            // webSocket = okHttpClient.newWebSocket(createRequest(), websocketListener)
            if (isPasswordValid(edtPassword.text.toString(), masterPassword)) {
                val intent = Intent(this, MainQRFInterface::class.java)
                intent.putExtra("username", edtUsername.text.toString())
                startActivity(intent)
            } else {
                Toast.makeText(this, "Invalid password, please retry or contact QRF admin.", Toast.LENGTH_LONG).show()
            }
            /*
            val intent = Intent(this, MainQRFInterface::class.java)
            intent.putExtra("username", edtUsername.text.toString())
            startActivity(intent)
            */
        }

        btnDisconnect.setOnClickListener {
            webSocket?.close(1000, "Cancelled Manually")
        }

        btnSend.setOnClickListener {
            if (edtMessage.text.toString().isNotEmpty()){
                webSocket?.send(edtMessage.text.toString())
                viewModel.setMessage(Pair(true, edtMessage.text.toString()))
            }
            else{
                Toast.makeText(this, "can't send blank message", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createRequest(): Request {
        val webSocketUrl = "wss://free.blr2.piesocket.com/v3/1?api_key=asdfasdfUFIjgKLDdZJ0zwoKpzn5ydd7Y&notify_self=1"
        /// val webSocketUrl = "wss://free.blr2.piesocket.com/v3/qrfinstance?api_key=asdfasdfCWUFIjgKLDdZJ0zwoKpzn5ydd7Y&notify_self=1"
        return Request.Builder()
            .url(webSocketUrl)
            .build()
    }
}
