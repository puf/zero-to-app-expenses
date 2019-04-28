package com.firebase.expensetracker

import android.app.Activity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*;
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask


class MainActivity : AppCompatActivity() {
    val RC_PHOTO_PICKER = 1
    val RC_SIGN_IN = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            invalidateOptionsMenu()
            if (auth.currentUser != null) {
                attachFirestoreListeners()
            }
        }

        fab.setOnClickListener { _ ->
            val getImageIntent = Intent(Intent.ACTION_GET_CONTENT)
            getImageIntent.type = "image/jpeg"
            startActivityForResult(getImageIntent, RC_PHOTO_PICKER)
        }

    }

    private fun attachFirestoreListeners() {
        val firestore = FirebaseFirestore.getInstance()
        val userDocRef = firestore.collection("users").document(getUserId())
        userDocRef.addSnapshotListener { documentSnapshot, _ ->
            if (documentSnapshot != null && documentSnapshot.exists()) {
                var text = formatAmount(documentSnapshot.get("user_cost"))
                if (!user_amount.text.equals(text)) {
                    showMessage("My cost was updated to " + text)
                    user_amount.text = text
                }

                text = formatAmount(documentSnapshot.get("team_cost"))
                if (!team_amount.text.equals(text)) {
                    showMessage("Team cost was updated to " + text)
                    team_amount.text = text
                }
            }
        }
        // Listen for documents with expense data
        userDocRef.collection("expenses")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { querySnapshot, e ->
                    if (e != null) showError("Error reading expenses", e)
                    querySnapshot?.forEach { doc ->
                        val data = doc!!.data!!
                        val text = formatAmount(data["item_cost"])
                        showMessage("Found amount ${text}")
                        amount.text = text
                    }
                }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            // Get a reference to the image the user selected
            val file = data?.data!!

            // Get a reference to the location where we'll store our receipts
            val expenseId = generateUniqueId()
            val filename = "receipts/${getUserId()}/${expenseId}"
            val storageRef = FirebaseStorage.getInstance().getReference(filename)

            // Upload file to Firebase Storage
            showMessage("Uploading receipt")
            storageRef.putFile(file).addOnSuccessListener {
                showMessage("Receipt uploaded, waiting for results...")
            }.addOnFailureListener{ error ->
                showMessage("Upload failed: ${error.message}")
                Log.e("TAG", "Upload failed", error)
            }
        }
        else if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                val user = FirebaseAuth.getInstance().currentUser!!
                showMessage("Signed in: ${user.displayName}")
            } else {
                showMessage(response?.error, Log.ERROR)
            }
        }
    }
    private fun onSignInButtonClicked() {
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(arrayListOf(
                                AuthUI.IdpConfig.EmailBuilder().build(),
                                AuthUI.IdpConfig.GoogleBuilder().build()))
                        .build(),
                RC_SIGN_IN)
    }

    private fun showMessage(msg: Any?, priority: Int = Log.INFO) {
        if (msg != null) {
            Snackbar.make(fab, msg.toString(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            Log.println(priority, "TAG", msg.toString())
        }
    }
    private fun showError(msg: Any?, tr: Throwable) {
        if (msg != null) {
            Snackbar.make(fab, msg.toString(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
        Log.e("TAG", msg.toString(), tr)
    }

    private fun showExpense(data: Map<String, Any>) {
        val text = formatAmount(data["item_cost"])
        showMessage("Found amount ${text}")
        amount.text = text
    }

    private fun getUserId() = FirebaseAuth.getInstance().currentUser?.uid ?: "yes"
    private fun roundAmount(amount: Any): Double = roundAmount(amount as Double)
    private fun roundAmount(amount: Double): Double = Math.round(amount * 100.0) / 100.0
    private fun formatAmount(amount: Any?): String = String.format("%10.2f", amount as Double)
    private fun generateUniqueId() = System.currentTimeMillis().toString()

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu!!.findItem(R.id.menu_signin).isVisible = FirebaseAuth.getInstance().currentUser == null
        menu!!.findItem(R.id.menu_signout).isVisible = FirebaseAuth.getInstance().currentUser != null
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_signin -> {
                onSignInButtonClicked()
            }
            R.id.menu_signout -> {
                AuthUI.getInstance().signOut(this)
            }
        }
        return super.onOptionsItemSelected(item)
    }

}
