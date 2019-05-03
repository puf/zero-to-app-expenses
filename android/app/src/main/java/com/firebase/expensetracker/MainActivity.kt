package com.firebase.expensetracker

import android.app.Activity
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*;
import android.content.Intent
import android.util.Log
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage


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


    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            onImageSelected(data)
        } else if (requestCode == RC_SIGN_IN) {
            onSignInCompleted(data, resultCode)
        }
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

    private fun getUserId() = FirebaseAuth.getInstance().currentUser?.uid ?: "yes"
    private fun formatAmount(amount: Any?): String {
        if (amount != null && amount is Number) {
            return String.format("%10.2f", amount.toDouble())
        }
        else return "";
    }
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

    private fun onImageSelected(data: Intent?) {
        // Get a reference to the image the user selected
        val file = data?.data!!

        // Get a reference to the location where we'll store our receipts
        val expenseId = generateUniqueId()
        val filename = "receipts/${getUserId()}/${expenseId}"
        val storageRef = FirebaseStorage.getInstance().getReference(filename)

        // Upload file to Firebase Storage
        showMessage("Uploading receipt ")
        storageRef.putFile(file)
                .addOnSuccessListener {
                    showMessage("Receipt uploaded, waiting for results...")
                }
                .addOnFailureListener { error ->
                    showMessage("Upload failed: ${error.message}")
                    Log.e("TAG", "Upload failed", error)
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

    private fun onSignInCompleted(data: Intent?, resultCode: Int) {
        val response = IdpResponse.fromResultIntent(data)

        if (resultCode == Activity.RESULT_OK) {
            val user = FirebaseAuth.getInstance().currentUser!!
            showMessage("Signed in: ${user.displayName}")
        } else {
            showMessage(response?.error, Log.ERROR)
        }
    }

    private fun attachFirestoreListeners() {
        // TODO: listen for latest expense document

        val firestore = FirebaseFirestore.getInstance()

        // Listen for documents with expense data
        firestore.collection("users/${getUserId()}/expenses")
          .orderBy("created_at", Query.Direction.DESCENDING)
          .limit(1)
          .addSnapshotListener { querySnapshot, e ->
            if (e != null) showError("Error reading expenses", e)

            if (querySnapshot?.documents?.size ?: 0 > 0) {
                val data = querySnapshot?.documents!![0].data ?: mapOf<String, Object>()
                lastItemLabel.text = formatAmount(data["item_cost"])
            }
          }

        // TODO: listen for totals
        // Listen for the user doc for aggregated totals
        firestore.document("users/${getUserId()}")
          .addSnapshotListener { documentSnapshot, _ ->
            if (documentSnapshot != null && documentSnapshot.exists()) {
                yourSpendLabel.text = formatAmount(documentSnapshot.get("user_cost"))
                teamSpendLabel.text = formatAmount(documentSnapshot.get("team_cost"))
            }

        }
    }
}


