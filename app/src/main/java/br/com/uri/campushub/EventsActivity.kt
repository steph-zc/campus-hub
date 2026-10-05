package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EventsActivity : AppCompatActivity() {

    private lateinit var txtHello: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        txtHello = findViewById(R.id.txtHello)

        loadUserName()
    }

    private fun loadUserName() {
        val uid = auth.currentUser!!.uid

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name")
                txtHello.text = getString(R.string.hello_user, name)
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_events, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menuLogout) {
            logout()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
