package org.fdroid.fdroid.privileged

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

const val REQUEST_CODE = 42069

class RequestPermissionActivity : Activity() {
    private lateinit var label: TextView
    private val requestPermissionResultListener =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                grantResult
            )
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        label = TextView(this)
        setContentView(label)

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        requestPermission()
    }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        if (requestCode != REQUEST_CODE)
            return
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            showPermissionRationaleAlertDialog()
        } else {
            finish()
        }
    }

    private val permissionRationaleDialogClickListener =
        DialogInterface.OnClickListener { _, button ->
            when (button) {
                DialogInterface.BUTTON_POSITIVE -> {
                    Shizuku.requestPermission(REQUEST_CODE)
                }
                DialogInterface.BUTTON_NEGATIVE -> {
                    finish()
                }
            }
        }

    private fun showPermissionRationaleAlertDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_name))
            .setMessage(getString(R.string.permission_rationale))
            .setPositiveButton("OK", permissionRationaleDialogClickListener)
            .setNegativeButton("Cancel", permissionRationaleDialogClickListener)
            .show()
    }

    private fun requestPermission() {
        if (Shizuku.isPreV11()) {
            finish()
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            finish()
            return
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            showPermissionRationaleAlertDialog()
            return
        } else {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}