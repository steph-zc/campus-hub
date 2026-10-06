package br.com.uri.campushub

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

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
    private lateinit var txtEnrolled: TextView
    private lateinit var btnEnroll: MaterialButton
    private lateinit var btnCancelEnrollment: MaterialButton
    private lateinit var txtDescription: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var eventRef: DocumentReference
    private lateinit var userRef: DocumentReference
    private lateinit var enrollmentRef: DocumentReference

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
        txtEnrolled = findViewById(R.id.txtEnrolled)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnCancelEnrollment = findViewById(R.id.btnCancelEnrollment)
        txtDescription = findViewById(R.id.txtDescription)

        // seta de voltar na barra
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySystemBarsPadding()

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)!!
        val uid = auth.currentUser!!.uid
        eventRef = db.collection("events").document(eventId)
        userRef = db.collection("users").document(uid)
        // a inscrição usa o id do evento, então cada aluno só tem uma por evento
        enrollmentRef = userRef.collection("enrollments").document(eventId)

        btnEnroll.setOnClickListener { enroll() }
        btnCancelEnrollment.setOnClickListener { confirmCancel() }

        loadData()
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

    // busca o evento, o curso do aluno e a inscrição ao mesmo tempo
    private fun loadData() {
        Tasks.whenAllSuccess<DocumentSnapshot>(eventRef.get(), userRef.get(), enrollmentRef.get())
            .addOnSuccessListener { results ->
                val event = results[0].toObject(Event::class.java)
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                val userCourse = results[1].getString("course") ?: ""
                val isEnrolled = results[2].exists()
                showEvent(event, userCourse, isEnrolled)
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showEvent(event: Event, userCourse: String, isEnrolled: Boolean) {
        EventChips.bind(chipCourse, chipAccess, event)
        txtTitle.text = event.title
        txtDate.text = event.fullDate()
        txtTime.text = event.time()
        txtLocation.text = event.location
        txtCapacity.text = getString(R.string.spots_of_capacity, event.remainingSpots(), event.capacity)
        txtDescription.text = event.description

        showEnrollmentState(event, userCourse, isEnrolled)

        progressBar.visibility = View.GONE
        scrollContent.visibility = View.VISIBLE
    }

    private fun showEnrollmentState(event: Event, userCourse: String, isEnrolled: Boolean) {
        txtEnrolled.visibility = if (isEnrolled) View.VISIBLE else View.GONE
        btnCancelEnrollment.visibility = if (isEnrolled) View.VISIBLE else View.GONE
        btnEnroll.visibility = if (isEnrolled) View.GONE else View.VISIBLE

        // o botão fica desativado mostrando o motivo
        when {
            !event.acceptsCourse(userCourse) -> {
                btnEnroll.isEnabled = false
                btnEnroll.text = getString(R.string.only_for_course, event.course)
            }
            event.remainingSpots() == 0 -> {
                btnEnroll.isEnabled = false
                btnEnroll.setText(R.string.sold_out)
            }
            else -> {
                btnEnroll.isEnabled = true
                btnEnroll.setText(R.string.enroll)
            }
        }
        btnCancelEnrollment.isEnabled = true
    }

    private fun enroll() {
        btnEnroll.isEnabled = false

        // a transação lê e grava junto: se dois alunos pegarem a última vaga
        // ao mesmo tempo, o Firestore repete uma delas e ela encontra o evento lotado
        db.runTransaction { transaction ->
            val event = transaction.get(eventRef).toObject(Event::class.java)!!
            val enrollment = transaction.get(enrollmentRef)

            if (enrollment.exists()) {
                return@runTransaction null
            }
            if (event.remainingSpots() == 0) {
                throw FirebaseFirestoreException(getString(R.string.sold_out),
                    FirebaseFirestoreException.Code.ABORTED)
            }

            val data = hashMapOf(
                "eventId" to eventRef.id,
                "enrolledAt" to FieldValue.serverTimestamp()
            )
            transaction.set(enrollmentRef, data)
            transaction.update(eventRef, "enrolledCount", FieldValue.increment(1))
            null
        }
            .addOnSuccessListener {
                Toast.makeText(this, R.string.enrolled_success, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun confirmCancel() {
        AlertDialog.Builder(this)
            .setMessage(R.string.cancel_enrollment_question)
            .setPositiveButton(R.string.cancel_enrollment) { _, _ -> cancelEnrollment() }
            .setNegativeButton(R.string.keep_enrollment, null)
            .show()
    }

    private fun cancelEnrollment() {
        btnCancelEnrollment.isEnabled = false

        db.runTransaction { transaction ->
            val enrollment = transaction.get(enrollmentRef)

            // só devolve a vaga se a inscrição existia mesmo
            if (enrollment.exists()) {
                transaction.delete(enrollmentRef)
                transaction.update(eventRef, "enrolledCount", FieldValue.increment(-1))
            }
            null
        }
            .addOnSuccessListener {
                Toast.makeText(this, R.string.enrollment_canceled, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showError(error: Exception) {
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
        loadData()
    }
}
