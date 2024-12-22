package com.example.genelek2

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Arrays


class TerminalFragment : Fragment(), ServiceConnection, SerialListener {
    private enum class Connected {
        False, Pending, True
    }

    private var deviceAddress: String? = null
    private var service: SerialService? = null

    private lateinit var receiveText: TextView
    private lateinit var sendText: TextView
    private var hexWatcher: TextUtil.HexWatcher? = null

    private var connected = Connected.False
    private var initialStart = true
    private var hexEnabled = false
    private var pendingNewline = false
    private var newline: String = TextUtil.newline_crlf

    /*
     * Lifecycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        retainInstance = true
        deviceAddress = requireArguments().getString("device")
    }

    override fun onDestroy() {
        if (connected != Connected.False) disconnect()
        requireActivity().stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (service != null) service?.attach(this)
        else requireActivity().startService(
            Intent(
                activity,
                SerialService::class.java
            )
        ) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    override fun onStop() {
        if (service != null && !requireActivity().isChangingConfigurations) service!!.detach()
        super.onStop()
    }

    @Suppress("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        requireActivity().bindService(
            Intent(getActivity(), SerialService::class.java),
            this, Context.BIND_AUTO_CREATE
        )
    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(this)
        } catch (ignored: Exception) {
        }
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        if (initialStart && service != null) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        service = (binder as SerialService.SerialBinder).service
        service?.attach(this)
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { this.connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        service = null
    }

    /*
     * UI
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_terminal, container, false)
        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans
        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance())

        sendText = view.findViewById<TextView>(R.id.send_text)
        hexWatcher = TextUtil.HexWatcher(sendText)
        hexWatcher?.enable(hexEnabled)
        sendText.addTextChangedListener(hexWatcher)
        sendText.setHint(if (hexEnabled) "HEX mode" else "")

        val sendBtn = view.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? ->
            send(
                sendText.getText().toString()
            )
        }
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.hex).setChecked(hexEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.backgroundNotification)
                .setChecked(service != null && service!!.areNotificationsEnabled())
        } else {
            menu.findItem(R.id.backgroundNotification).setChecked(true)
            menu.findItem(R.id.backgroundNotification).setEnabled(false)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.clear) {
            receiveText!!.text = ""
            return true
        } else if (id == R.id.newline) {
            val newlineNames = resources.getStringArray(R.array.newline_names)
            val newlineValues = resources.getStringArray(R.array.newline_values)
            val pos = Arrays.asList(*newlineValues).indexOf(newline)
            val builder = AlertDialog.Builder(activity)
            builder.setTitle("Newline")
            builder.setSingleChoiceItems(newlineNames, pos) { dialog: DialogInterface, item1: Int ->
                newline = newlineValues[item1]
                dialog.dismiss()
            }
            builder.create().show()
            return true
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled
            sendText?.text = ""
            hexWatcher?.enable(hexEnabled)
            sendText!!.hint = if (hexEnabled) "HEX mode" else ""
            item.setChecked(hexEnabled)
            return true
        } else if (id == R.id.backgroundNotification) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!service!!.areNotificationsEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
                } else {
                    showNotificationSettings()
                }
            }
            return true
        } else {
            return super.onOptionsItemSelected(item)
        }
    }

    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            status("connecting...")
            connected = Connected.Pending
            val socket = SerialSocket(requireActivity().applicationContext, device)
            service?.connect(socket)
        } catch (e: Exception) {
            onSerialConnectionError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service?.disconnect()
    }

    private fun send(str: String) {
        Log.d("SendFunction", "send() called with str: $str")

        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            Log.d("SendFunction", "Connection status: Not connected")
            return

        }
        try {
            val msg: String
            val data: ByteArray
            if (hexEnabled) {
                Log.d("SendFunction", "Hex mode enabled")
                val sb = StringBuilder()

                val hexString = TextUtil.fromHexString(str)
                Log.d("SendFunction", "Converted input to hex: ${hexString?.contentToString()}")

                TextUtil.toHexString(sb, hexString)
                TextUtil.toHexString(sb, newline.toByteArray())
                //msg = sb.toString()
                msg = String(sb)
                data = TextUtil.fromHexString(msg)
                Log.d("SendFunction", "Hex string with newline: $msg")
                Log.d("SendFunction", "Hex data to send: ${data.contentToString()}")
                //Log.d("SendFunction", "Nothing sent.")
            } else {
                Log.d("SendFunction", "Plain text mode enabled")
                msg = str
                data = (str + newline).toByteArray()
                Log.d("SendFunction", "Data to send: ${data.contentToString()}")
            }
            val spn = SpannableStringBuilder(msg + '\n')
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            Log.d("SendFunction", "Formatted text appended to receiveText")
            receiveText.append(spn)

            Log.d("SendFunction", "Calling service?.write with data: ${data.contentToString()}")
            service?.write(data)
        } catch (e: Exception) {

            Log.e("SendFunction", "Error in send(): ${e.message}", e)
            onSerialIOError(e)
        }
    }

    private fun receive(datas: ArrayDeque<ByteArray>) {

        if (datas.isEmpty()) {
            Log.w("ReceiveFunction", "No data received.")
            return
        }

        val spn = SpannableStringBuilder()

        for (data in datas) {
            Log.d("ReceiveFunction", "Raw data: ${data.contentToString()}")

            if (hexEnabled) {
                val hexString = data?.let { TextUtil.toHexString(it) }
                spn.append(hexString).append('\n')
                Log.d("ReceiveFunction", "Hex string: $hexString")
            } else {
                val msg = java.lang.String(data)
                Log.d("ReceiveFunction", "Converted message: $msg")
                if (newline == TextUtil.newline_crlf && msg.isNotEmpty()) {
                    // don't show CR as ^M if directly before LF
                    var msg = data?.let { String(it, Charsets.UTF_8) } ?: ""
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf)

                    // special handling if CR and LF come in separate fragments

                    if (pendingNewline && msg[0] == '\n') {

                        if (spn.length >= 2) {
                            spn.delete(spn.length - 2, spn.length)
                        } else {
                            val edt = receiveText?.editableText
                            if (edt != null && edt.length >= 2) edt.delete(
                                edt.length - 2,
                                edt.length
                            )
                        }

                    }
                    pendingNewline = msg[msg.length - 1] == '\r'
                }
                spn.append(TextUtil.toCaretString(msg, newline.isNotEmpty()))
            }
        }

        Log.d("ReceiveFunction", "Final processed string: $spn")

        /*
        android.os.Handler(Looper.getMainLooper()).post {
            receiveText?.append(spn) ?: Log.e("ReceiveFunction", "receiveText is null!")
        }

         */

        CoroutineScope(Dispatchers.Main).launch {
            receiveText!!.append(spn) //?: Log.e("ReceiveFunction", "receiveText is null!")
        }
        //receiveText!!.append(spn)
    }

    private fun status(str: String) {
        val spn = SpannableStringBuilder(str + '\n')
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorStatusText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText?.append(spn)
    }

    /*
     * starting with Android 14, notifications are not shown in notification bar by default when App is in background
     */
    private fun showNotificationSettings() {
        val intent = Intent()
        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS")
        intent.putExtra("android.provider.extra.APP_PACKAGE", requireActivity().packageName)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (permissions.contentEquals(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !service!!.areNotificationsEnabled()) showNotificationSettings()
    }

    /*
     * SerialListener
     */
    override fun onSerialConnected() {
        status("connected")
        connected = Connected.True
    }



    override fun onSerialConnectionError(e: Exception?) {
        //TODO("Not yet implemented")

        if (e != null) {
            status("connection failed: " + e.message)
        }
        disconnect()

    }

    override fun onSerialDataRead(data: ByteArray?) {
        //TODO("Not yet implemented")

        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
        receive(datas)

    }

    override fun onSerialDatasRead(datas: ArrayDeque<ByteArray?>?) {
        //TODO("Not yet implemented")
        val received: ArrayDeque<ByteArray> = datas?.filterNotNull()?.let { ArrayDeque(it) } ?: ArrayDeque()
        receive(received)
        //val receivedData = datas?.filterNotNull()
        //receivedData?.let { receive(it) }
        //receive(datas)
    }

    override fun onSerialIOError(e: Exception?) {
        //TODO("Not yet implemented")
        /*
        if (e != null) {
            status("connection lost: " + e.message)
        }
        disconnect()

         */
    }



    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialConnect() {
        //TODO("Not yet implemented")
    }


    override fun onSerialRead(data: ByteArray) {
        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
        receive(datas)
    }

    fun onSerialRead(datas: ArrayDeque<ByteArray>) {
        receive(datas)
    }




    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
    }


}