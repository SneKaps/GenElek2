package com.example.genelek2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
//import kotlinx.coroutines.flow.internal.NoOpContinuation.context
//import kotlin.coroutines.jvm.internal.CompletedContinuation.context


object BluetoothUtil {

    /**
     * sort by name, then address. sort named devices first
     */
    @SuppressLint("MissingPermission")
    fun compareTo(a: BluetoothDevice, b: BluetoothDevice): Int {
        val aValid = a.name != null && !a.name.isEmpty()
        val bValid = b.name != null && !b.name.isEmpty()
        if (aValid && bValid) {
            val ret = a.name.compareTo(b.name)
            if (ret != 0) return ret
            return a.address.compareTo(b.address)
        }
        if (aValid) return -1
        if (bValid) return +1
        return a.address.compareTo(b.address)
    }

    /**
     * Android 12 permission handling
     */
    private fun showRationaleDialog(fragment: Fragment, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(fragment.activity)
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title))
        builder.setMessage(fragment.getString(R.string.bluetooth_permission_grant))
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton("Continue", listener)
        builder.show()
    }

    private fun showSettingsDialog(fragment: Fragment) {
        val s = fragment.resources.getString(
            fragment.resources.getIdentifier(
                "@android:string/permgrouplab_nearby_devices",
                null,
                null
            )
        )
        val builder = AlertDialog.Builder(fragment.activity)
        builder.setTitle(fragment.getString(R.string.bluetooth_permission_title))
        builder.setMessage(
            String.format(
                fragment.getString(R.string.bluetooth_permission_denied),
                s
            )
        )
        builder.setNegativeButton("Cancel", null)
        builder.setPositiveButton(
            "Settings"
        ) { dialog: DialogInterface?, which: Int ->
            fragment.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                )
            )
        }
        builder.show()
    }

    fun hasPermissions(
        fragment: Fragment,
        requestPermissionLauncher: ActivityResultLauncher<String>,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true

        val missingPermissions =
            fragment.requireActivity().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

        val showRationale =
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)

        if (missingPermissions) {
            if (showRationale) {
                showRationaleDialog(
                    fragment
                ) { dialog: DialogInterface?, which: Int ->
                    requestPermissionLauncher.launch(
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                }
            } else {
                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            return false
        } else {
            return true
        }
    }

    fun onPermissionsResult(fragment: Fragment, granted: Boolean, cb: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val showRationale =
            fragment.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
        if (granted) {
            cb.invoke()
        } else if (showRationale) {
            showRationaleDialog(
                fragment
            ) { dialog: DialogInterface?, which: Int -> cb.invoke() }
        } else {
            showSettingsDialog(fragment)
        }
    }

}