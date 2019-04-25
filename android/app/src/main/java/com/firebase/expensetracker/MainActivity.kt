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

        FirebaseAuth.getInstance().addAuthStateListener { _ ->
            invalidateOptionsMenu()
        }

        fab.setOnClickListener { _ ->
            //val intent = Intent(Intent.ACTION_PICK)//, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            //val intent = Intent(Intent.ACTION_GET_CONTENT, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            //val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            // 1. Show file picker
            val getImageIntent = Intent(Intent.ACTION_GET_CONTENT)
            getImageIntent.type = "image/jpeg"
            startActivityForResult(getImageIntent, RC_PHOTO_PICKER)

            // 2. Show camera
            //val cameraIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            //startActivityForResult(cameraIntent, RC_PHOTO_PICKER)
            // TODO: the result code for this doesn't work yet

            // 3. Show chooser
            //val chooser = Intent.createChooser(getImageIntent, "Select Picture")
            //chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            //startActivityForResult(chooser, RC_PHOTO_PICKER)
        }

        val userDocRef = FirebaseFirestore.getInstance().collection("users").document(FirebaseAuth.getInstance().currentUser?.uid ?: "yes")
        userDocRef.addSnapshotListener { documentSnapshot, _ ->
            if (documentSnapshot != null && documentSnapshot.exists()) {
                var text = formatAmount(documentSnapshot.get("user_cost"))
                if (!user_amount.text.equals(text)) {
                    showMessage("My cost was updated to "+text)
                    user_amount.text = text
                }

                text = formatAmount(documentSnapshot.get("team_cost"))
                if (!team_amount.text.equals(text)) {
                    showMessage("Team cost was updated to "+text)
                    team_amount.text = text
                }
            }
        }

    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            val selectedImageUri = data?.data!!

            // Get a reference to the location where we'll store our receipts
            val receiptsRef = FirebaseStorage.getInstance().getReference("receipts")
            val storageRef = receiptsRef.child(FirebaseAuth.getInstance().currentUser?.uid ?: "yes")
            // TODO: generate UUID?
            val expenseId = System.currentTimeMillis().toString()
            val photoRef = storageRef.child(expenseId)

            // Upload file to Firebase Storage
            showMessage("Uploading receipt")
            photoRef.putFile(selectedImageUri).addOnSuccessListener {
                showMessage("Receipt uploaded, waiting for results...")

                // Wait for document with expense data
                val userDoc = FirebaseFirestore.getInstance().collection("users").document(FirebaseAuth.getInstance().currentUser!!.uid)
                val expensesCollection = userDoc.collection("expenses")
                val expenseDoc= expensesCollection.document(expenseId)
                expenseDoc.addSnapshotListener{snapshot, e ->
                    if (e != null) throw e;
                    if (snapshot?.exists() ?: false) {
                        showExpense(snapshot!!.data!!);
                    }
                }

            }.addOnFailureListener{ error ->
                showMessage("Upload failed: ${error.message}")
                        Log.e("TAG", "Upload failed", error)
            }
        }
        else if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
                val user = FirebaseAuth.getInstance().currentUser!!
                showMessage("Signed in: ${user.displayName}")
            } else {
                showMessage(response?.error)
            }
        }
    }
    private fun showMessage(msg: Any?) {
        if (msg != null) {
            Snackbar.make(fab, msg.toString(), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

    private fun showExpense(data: Map<String, Any>) {
        val amountView = this.findViewById<TextView>(R.id.amount)
        //val currencyView = this.findViewById<TextView>(R.id.currency)
        //val receiptView = this.findViewById<ImageView>(R.id.receipt)
        showMessage("Found amount ${data["item_cost"]}")
        amountView.text = formatAmount(data["item_cost"])
        //if (data.containsKey("currency")) currencyView.text = data["currency"].toString();
        //if (data.contains("receipt")) Glide.with(this).load(data["receipt"]).into(receiptView);
    }

    private fun roundAmount(amount: Any): Double { return roundAmount(amount as Double) }
    private fun roundAmount(amount: Double): Double { return Math.round(amount * 100.0) / 100.0 }
    private fun formatAmount(amount: Any?): String {
        return String.format("%10.2f", amount as Double)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        val title = if (FirebaseAuth.getInstance().currentUser == null) "Sign in" else "Sign out"
        //var icon =  if (FirebaseAuth.getInstance().currentUser == null) R.drawable.ic_person_outline_white_24dp else R.drawable.ic_person_outline_white_24dp
        menu!!.findItem(R.id.menu_auth).title = title
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_auth -> {
                if (FirebaseAuth.getInstance().currentUser == null) {
                    val providers = arrayListOf(
                            AuthUI.IdpConfig.EmailBuilder().build(),
                            AuthUI.IdpConfig.GoogleBuilder().build())

                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN)
                }
                else {
                    AuthUI.getInstance().signOut(this)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
