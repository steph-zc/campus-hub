package br.com.uri.campushub

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import java.util.Locale

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
    private lateinit var txtCategory: TextView
    private lateinit var txtTitle: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtLocation: TextView
    private lateinit var txtCapacity: TextView
    private lateinit var txtRatingAverage: TextView
    private lateinit var txtEnrolled: TextView
    private lateinit var btnEnroll: MaterialButton
    private lateinit var btnCancelEnrollment: MaterialButton
    private lateinit var cardRating: MaterialCardView
    private lateinit var ratingBar: RatingBar
    private lateinit var txtRatingHint: TextView
    private lateinit var txtDescription: TextView
    private lateinit var txtCommentsTitle: TextView
    private lateinit var layoutComment: TextInputLayout
    private lateinit var edtComment: TextInputEditText
    private lateinit var txtNoComments: TextView
    private lateinit var rvComments: RecyclerView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var eventRef: DocumentReference
    private lateinit var userRef: DocumentReference
    private lateinit var enrollmentRef: DocumentReference
    private lateinit var favoriteRef: DocumentReference
    private lateinit var commentsRef: CollectionReference
    private lateinit var ratingRef: DocumentReference

    // null enquanto os dados não chegaram; o coração só aparece depois
    private var isFavorite: Boolean? = null

    // nome do aluno logado, gravado como autor dos comentários dele
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        scrollContent = findViewById(R.id.scrollContent)
        progressBar = findViewById(R.id.progressBar)
        chipCourse = findViewById(R.id.chipCourse)
        chipAccess = findViewById(R.id.chipAccess)
        txtCategory = findViewById(R.id.txtCategory)
        txtTitle = findViewById(R.id.txtTitle)
        txtDate = findViewById(R.id.txtDate)
        txtTime = findViewById(R.id.txtTime)
        txtLocation = findViewById(R.id.txtLocation)
        txtCapacity = findViewById(R.id.txtCapacity)
        txtRatingAverage = findViewById(R.id.txtRatingAverage)
        txtEnrolled = findViewById(R.id.txtEnrolled)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnCancelEnrollment = findViewById(R.id.btnCancelEnrollment)
        cardRating = findViewById(R.id.cardRating)
        ratingBar = findViewById(R.id.ratingBar)
        txtRatingHint = findViewById(R.id.txtRatingHint)
        txtDescription = findViewById(R.id.txtDescription)
        txtCommentsTitle = findViewById(R.id.txtCommentsTitle)
        layoutComment = findViewById(R.id.layoutComment)
        edtComment = findViewById(R.id.edtComment)
        txtNoComments = findViewById(R.id.txtNoComments)
        rvComments = findViewById(R.id.rvComments)

        rvComments.layoutManager = LinearLayoutManager(this)

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
        // favorito é separado da inscrição: favoritar não reserva vaga
        favoriteRef = userRef.collection("favorites").document(eventId)
        // os comentários ficam dentro do evento, visíveis para todos os alunos
        commentsRef = eventRef.collection("comments")
        // o id da avaliação é o uid: cada aluno tem no máximo uma por evento
        ratingRef = eventRef.collection("ratings").document(uid)

        btnEnroll.setOnClickListener { enroll() }
        btnCancelEnrollment.setOnClickListener { confirmCancel() }
        layoutComment.setEndIconOnClickListener { postComment() }

        // fromUser separa o toque do aluno de quando o código preenche a nota salva
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) saveRating(rating.toInt())
        }

        loadData()
        loadComments()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_event_details, menu)
        return true
    }

    // chamado de novo a cada invalidateOptionsMenu(), para o coração acompanhar o estado
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.menuFavorite)
        val favorite = isFavorite
        item.isVisible = favorite != null
        if (favorite == true) {
            item.setIcon(R.drawable.ic_favorite)
            item.setTitle(R.string.remove_favorite)
        } else {
            item.setIcon(R.drawable.ic_favorite_border)
            item.setTitle(R.string.add_favorite)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menuFavorite) {
            toggleFavorite()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleFavorite() {
        val wasFavorite = isFavorite ?: return

        // troca o ícone na hora; se o Firestore falhar, volta ao que era
        isFavorite = !wasFavorite
        invalidateOptionsMenu()

        val task = if (wasFavorite) {
            favoriteRef.delete()
        } else {
            favoriteRef.set(hashMapOf(
                "eventId" to eventRef.id,
                "favoritedAt" to FieldValue.serverTimestamp()
            ))
        }

        task
            .addOnSuccessListener {
                val message = if (wasFavorite) R.string.favorite_removed else R.string.favorite_added
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                isFavorite = wasFavorite
                invalidateOptionsMenu()
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
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

    // busca o evento, o curso do aluno, a inscrição, o favorito e a avaliação ao mesmo tempo
    private fun loadData() {
        Tasks.whenAllSuccess<DocumentSnapshot>(
            eventRef.get(), userRef.get(), enrollmentRef.get(), favoriteRef.get(), ratingRef.get()
        )
            .addOnSuccessListener { results ->
                val event = results[0].toObject(Event::class.java)
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                val userCourse = results[1].getString("course") ?: ""
                userName = results[1].getString("name") ?: ""
                val isEnrolled = results[2].exists()
                isFavorite = results[3].exists()
                // 0 = ainda não avaliou
                val myRating = results[4].getLong("value")?.toInt() ?: 0
                invalidateOptionsMenu()
                showEvent(event, userCourse, isEnrolled)
                showRatingState(event, isEnrolled, myRating)
                if (event.hasEnded()) loadRatingAverage()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showEvent(event: Event, userCourse: String, isEnrolled: Boolean) {
        EventChips.bind(chipCourse, chipAccess, event)
        txtCategory.text = event.category
        txtCategory.visibility = if (event.category.isEmpty()) View.GONE else View.VISIBLE
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

    // avaliar exige as duas coisas: ter se inscrito e o evento já ter acontecido
    private fun showRatingState(event: Event, isEnrolled: Boolean, myRating: Int) {
        if (!isEnrolled || !event.hasEnded()) {
            cardRating.visibility = View.GONE
            return
        }
        cardRating.visibility = View.VISIBLE
        ratingBar.rating = myRating.toFloat()
        showRatingHint(myRating)
    }

    // o próprio Firestore calcula a média e a quantidade, sem baixar as avaliações uma a uma
    private fun loadRatingAverage() {
        val average = AggregateField.average("value")
        val count = AggregateField.count()

        eventRef.collection("ratings").aggregate(average, count).get(AggregateSource.SERVER)
            .addOnSuccessListener { result ->
                val total = result.get(count)
                txtRatingAverage.text = if (total == 0L) {
                    getString(R.string.no_ratings)
                } else {
                    // uma casa decimal com vírgula: 4,5
                    val value = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", result.get(average))
                    resources.getQuantityString(R.plurals.rating_average, total.toInt(), value, total.toInt())
                }
                txtRatingAverage.visibility = View.VISIBLE
            }
    }

    private fun showRatingHint(rating: Int) {
        txtRatingHint.text = if (rating == 0) {
            getString(R.string.rating_hint)
        } else {
            getString(R.string.your_rating_value, rating)
        }
    }

    private fun saveRating(stars: Int) {
        // o RatingBar deixa arrastar até 0, mas a nota mínima é 1
        val value = stars.coerceAtLeast(1)
        if (stars < 1) ratingBar.rating = 1f

        val rating = hashMapOf(
            "userId" to auth.currentUser!!.uid,
            "value" to value,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        // set no mesmo documento: a primeira vez cria, as próximas trocam a nota
        ratingRef.set(rating)
            .addOnSuccessListener {
                showRatingHint(value)
                Toast.makeText(this, R.string.rating_saved, Toast.LENGTH_SHORT).show()
                // a nota nova muda a média
                loadRatingAverage()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun showEnrollmentState(event: Event, userCourse: String, isEnrolled: Boolean) {
        txtEnrolled.visibility = if (isEnrolled) View.VISIBLE else View.GONE
        txtEnrolled.setText(if (event.hasEnded()) R.string.attended_banner else R.string.enrolled_banner)
        // depois que o evento acontece não dá mais para cancelar
        btnCancelEnrollment.visibility = if (isEnrolled && !event.hasEnded()) View.VISIBLE else View.GONE
        btnEnroll.visibility = if (isEnrolled) View.GONE else View.VISIBLE

        // o botão fica desativado mostrando o motivo
        when {
            event.hasEnded() -> {
                btnEnroll.isEnabled = false
                btnEnroll.setText(R.string.event_ended)
            }
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

    // mais novos primeiro, logo abaixo do campo de escrever
    private fun loadComments() {
        commentsRef.orderBy("createdAt", Query.Direction.DESCENDING).get()
            .addOnSuccessListener { result ->
                val comments = result.toObjects(Comment::class.java)
                rvComments.adapter = CommentAdapter(
                    comments,
                    auth.currentUser!!.uid,
                    onEdit = { comment -> showEditDialog(comment) },
                    onDelete = { comment -> confirmDelete(comment) }
                )
                txtCommentsTitle.text = getString(R.string.comments_count, comments.size)
                txtNoComments.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun postComment() {
        val text = edtComment.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.empty_comment, Toast.LENGTH_SHORT).show()
            return
        }

        // evita publicar duas vezes com toques repetidos
        layoutComment.isEnabled = false

        val comment = hashMapOf(
            "userId" to auth.currentUser!!.uid,
            "authorName" to userName,
            "text" to text,
            // a data vem do servidor, não do relógio do celular
            "createdAt" to FieldValue.serverTimestamp()
        )

        commentsRef.add(comment)
            .addOnSuccessListener {
                layoutComment.isEnabled = true
                edtComment.text = null
                hideKeyboard()
                loadComments()
            }
            .addOnFailureListener { error ->
                layoutComment.isEnabled = true
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun showEditDialog(comment: Comment) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_comment, null)
        val edtEditComment = view.findViewById<TextInputEditText>(R.id.edtEditComment)
        edtEditComment.setText(comment.text)
        // cursor no fim do texto, pronto para continuar escrevendo
        edtEditComment.setSelection(comment.text.length)

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_comment)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                updateComment(comment, edtEditComment.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateComment(comment: Comment, newText: String) {
        if (newText.isEmpty()) {
            Toast.makeText(this, R.string.empty_comment, Toast.LENGTH_SHORT).show()
            return
        }
        // nada mudou, não precisa gravar nem marcar como editado
        if (newText == comment.text) return

        val changes = mapOf(
            "text" to newText,
            "editedAt" to FieldValue.serverTimestamp()
        )
        commentsRef.document(comment.id).update(changes)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.comment_updated, Toast.LENGTH_SHORT).show()
                loadComments()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun confirmDelete(comment: Comment) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_comment_question)
            .setPositiveButton(R.string.delete) { _, _ -> deleteComment(comment) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteComment(comment: Comment) {
        commentsRef.document(comment.id).delete()
            .addOnSuccessListener {
                Toast.makeText(this, R.string.comment_deleted, Toast.LENGTH_SHORT).show()
                loadComments()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun hideKeyboard() {
        val keyboard = getSystemService(InputMethodManager::class.java)
        keyboard.hideSoftInputFromWindow(edtComment.windowToken, 0)
        edtComment.clearFocus()
    }

    private fun showError(error: Exception) {
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
        loadData()
    }
}
