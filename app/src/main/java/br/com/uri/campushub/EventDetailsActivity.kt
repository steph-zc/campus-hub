package br.com.uri.campushub

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore

class EventDetailsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollContent: NestedScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var chipCourse: Chip
    private lateinit var chipAccess: Chip
    private lateinit var txtTitle: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtLocation: TextView
    private lateinit var txtCapacity: TextView
    private lateinit var txtDescription: TextView

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        scrollContent = findViewById(R.id.scrollContent)
        progressBar = findViewById(R.id.progressBar)
        chipCourse = findViewById(R.id.chipCourse)
        chipAccess = findViewById(R.id.chipAccess)
        txtTitle = findViewById(R.id.txtTitle)
        txtDate = findViewById(R.id.txtDate)
        txtTime = findViewById(R.id.txtTime)
        txtLocation = findViewById(R.id.txtLocation)
        txtCapacity = findViewById(R.id.txtCapacity)
        txtDescription = findViewById(R.id.txtDescription)

        // seta de voltar na barra
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySystemBarsPadding()

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)!!
        loadEvent(eventId)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applySystemBarsPadding() {
        val space = resources.getDimensionPixelSize(R.dimen.list_padding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(0, bars.top, 0, 0)
            scrollContent.setPadding(space, space, space, space + bars.bottom)
            insets
        }
    }

    private fun loadEvent(eventId: String) {
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { document ->
                val event = document.toObject(Event::class.java)
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                showEvent(event)
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showEvent(event: Event) {
        EventChips.bind(chipCourse, chipAccess, event)
        txtTitle.text = event.title
        txtDate.text = event.fullDate()
        txtTime.text = event.time()
        txtLocation.text = event.location
        txtCapacity.text = getString(R.string.capacity, event.capacity)
        txtDescription.text = event.description

        progressBar.visibility = View.GONE
        scrollContent.visibility = View.VISIBLE
    }
}
