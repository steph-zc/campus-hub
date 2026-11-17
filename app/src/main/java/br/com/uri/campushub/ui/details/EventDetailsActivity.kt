package br.com.uri.campushub.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Comment
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.CommentRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.FavoriteRepository
import br.com.uri.campushub.data.repository.RatingRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.data.repository.WaitlistRepository
import br.com.uri.campushub.domain.CampusTime
import br.com.uri.campushub.domain.EnrollmentRules
import br.com.uri.campushub.domain.OrganizerRules
import br.com.uri.campushub.domain.RatingRules
import br.com.uri.campushub.domain.WaitlistRules
import br.com.uri.campushub.ui.common.EventImages
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.common.setupToolbar
import br.com.uri.campushub.ui.events.EventChips
import br.com.uri.campushub.ui.media.MediaSection
import br.com.uri.campushub.ui.organizer.EventFormActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import org.osmdroid.views.MapView

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
    private lateinit var imgCover: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtDate: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtLocation: TextView
    private lateinit var txtCapacity: TextView
    private lateinit var txtRatingAverage: TextView
    private lateinit var txtEnrolled: TextView
    private lateinit var txtEventStatus: TextView
    private lateinit var btnPublish: MaterialButton
    private lateinit var btnCloseEnrollments: MaterialButton
    private lateinit var btnFinish: MaterialButton
    private lateinit var txtWaitlist: TextView
    private lateinit var btnLeaveWaitlist: MaterialButton
    private lateinit var btnEnroll: MaterialButton
    private lateinit var btnCancelEnrollment: MaterialButton
    private lateinit var cardRating: MaterialCardView
    private lateinit var ratingBar: RatingBar
    private lateinit var txtRatingHint: TextView
    private lateinit var txtDescription: TextView
    private lateinit var cardLocation: MaterialCardView
    private lateinit var txtAddress: TextView
    private lateinit var mapView: MapView
    private lateinit var btnOpenMap: MaterialButton
    private lateinit var txtCommentsTitle: TextView
    private lateinit var layoutComment: TextInputLayout
    private lateinit var edtComment: TextInputEditText
    private lateinit var txtNoComments: TextView
    private lateinit var rvComments: RecyclerView

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val eventRepository = EventRepository()
    private val enrollmentRepository = EnrollmentRepository()
    private val favoriteRepository = FavoriteRepository()
    private val commentRepository = CommentRepository()
    private val ratingRepository = RatingRepository()
    private val waitlistRepository = WaitlistRepository()

    private lateinit var eventId: String
    private lateinit var uid: String
    private lateinit var mediaSection: MediaSection

    // null enquanto os dados não chegaram; o coração só aparece depois
    private var isFavorite: Boolean? = null

    // true quando a conta logada é a organizadora responsável pelo evento e ele ainda não foi encerrado
    private var canEdit = false

    // evento carregado, usado ao excluir (a capa vai junto); canDelete = dono e sem inscritos
    private var loadedEvent: Event? = null
    private var canDelete = false

    // true quando o evento está lotado e o botão de inscrever vira "Entrar na lista de espera"
    private var joinsWaitlist = false

    // abre o formulário de edição; se o organizador salvar, os detalhes são lidos de novo
    private val editEvent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) loadData()
    }

    // nome do aluno logado, gravado como autor dos comentários dele
    private var userName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventMap.init(this)
        setContentView(R.layout.activity_event_details)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        scrollContent = findViewById(R.id.scrollContent)
        progressBar = findViewById(R.id.progressBar)
        chipCourse = findViewById(R.id.chipCourse)
        chipAccess = findViewById(R.id.chipAccess)
        txtCategory = findViewById(R.id.txtCategory)
        imgCover = findViewById(R.id.imgCover)
        txtTitle = findViewById(R.id.txtTitle)
        txtDate = findViewById(R.id.txtDate)
        txtTime = findViewById(R.id.txtTime)
        txtLocation = findViewById(R.id.txtLocation)
        txtCapacity = findViewById(R.id.txtCapacity)
        txtRatingAverage = findViewById(R.id.txtRatingAverage)
        txtEnrolled = findViewById(R.id.txtEnrolled)
        txtEventStatus = findViewById(R.id.txtEventStatus)
        btnPublish = findViewById(R.id.btnPublish)
        btnCloseEnrollments = findViewById(R.id.btnCloseEnrollments)
        btnFinish = findViewById(R.id.btnFinish)
        txtWaitlist = findViewById(R.id.txtWaitlist)
        btnLeaveWaitlist = findViewById(R.id.btnLeaveWaitlist)
        btnEnroll = findViewById(R.id.btnEnroll)
        btnCancelEnrollment = findViewById(R.id.btnCancelEnrollment)
        cardRating = findViewById(R.id.cardRating)
        ratingBar = findViewById(R.id.ratingBar)
        txtRatingHint = findViewById(R.id.txtRatingHint)
        txtDescription = findViewById(R.id.txtDescription)
        cardLocation = findViewById(R.id.cardLocation)
        txtAddress = findViewById(R.id.txtAddress)
        mapView = findViewById(R.id.mapView)
        btnOpenMap = findViewById(R.id.btnOpenMap)
        txtCommentsTitle = findViewById(R.id.txtCommentsTitle)
        layoutComment = findViewById(R.id.layoutComment)
        edtComment = findViewById(R.id.edtComment)
        txtNoComments = findViewById(R.id.txtNoComments)
        rvComments = findViewById(R.id.rvComments)

        rvComments.layoutManager = LinearLayoutManager(this)

        setupToolbar(toolbar)
        applySystemBarsPadding(findViewById(R.id.root), appBar, scrollContent)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID)!!
        uid = authRepository.requireUserId()

        mediaSection = MediaSection(this, eventId, uid)
        mediaSection.restoreState(savedInstanceState)

        btnEnroll.setOnClickListener { if (joinsWaitlist) joinWaitlist() else enroll() }
        btnLeaveWaitlist.setOnClickListener { confirmLeaveWaitlist() }
        btnCancelEnrollment.setOnClickListener { confirmCancel() }
        btnPublish.setOnClickListener { confirmPublish() }
        btnCloseEnrollments.setOnClickListener { confirmCloseEnrollments() }
        btnFinish.setOnClickListener { confirmFinish() }
        layoutComment.setEndIconOnClickListener { postComment() }

        // fromUser separa o toque do aluno de quando o código preenche a nota salva
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) saveRating(rating.toInt())
        }

        loadData()
        loadComments()
        mediaSection.load()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mediaSection.saveState(outState)
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
        menu.findItem(R.id.menuEditEvent).isVisible = canEdit
        menu.findItem(R.id.menuDeleteEvent).isVisible = canDelete
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuFavorite -> {
                toggleFavorite()
                return true
            }
            R.id.menuDeleteEvent -> {
                confirmDeleteEvent()
                return true
            }
            R.id.menuEditEvent -> {
                val intent = Intent(this, EventFormActivity::class.java)
                intent.putExtra(EventFormActivity.EXTRA_EVENT_ID, eventId)
                editEvent.launch(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleFavorite() {
        val wasFavorite = isFavorite ?: return

        // troca o ícone na hora; se o Firestore falhar, volta ao que era
        isFavorite = !wasFavorite
        invalidateOptionsMenu()

        favoriteRepository.setFavorite(uid, eventId, favorite = !wasFavorite)
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

    // busca o evento, o curso do aluno, a inscrição, o favorito, a avaliação e o papel da conta ao mesmo tempo
    // antes de ler, atende a lista de espera se uma vaga abriu (WaitlistRepository.settle):
    // é aqui que a vaga de quem cancelou vai para o primeiro da fila. Se falhar (por exemplo,
    // um rascunho que o aluno não pode ver), a tela carrega do mesmo jeito
    private fun loadData() {
        waitlistRepository.settle(eventId).addOnCompleteListener { loadDetails() }
    }

    // a tela já foi fechada (voltar, sair da conta, trocar de conta) enquanto o Firestore respondia:
    // a resposta é ignorada, porque desenhar numa Activity destruída derruba o app (o Glide recusa)
    private fun isGone() = isFinishing || isDestroyed

    private fun loadDetails() {
        if (isGone()) return
        val eventTask = eventRepository.get(eventId)
        val userTask = userRepository.get(uid)
        val enrolledTask = enrollmentRepository.isEnrolled(uid, eventId)
        val favoriteTask = favoriteRepository.isFavorite(uid, eventId)
        val ratingTask = ratingRepository.getMyRating(uid, eventId)
        val roleTask = userRepository.getRole(uid)
        // posição na lista de espera (WaitlistRepository, agregação count no Firestore); 0 = fora da fila
        val waitlistTask = waitlistRepository.getPosition(uid, eventId)
        // true uma vez só, quando a inscrição veio da lista de espera (EnrollmentRepository)
        val promotedTask = enrollmentRepository.takePromotionNotice(uid, eventId)

        // espera as oito buscas terminarem e depois lê o resultado de cada uma
        Tasks.whenAllSuccess<Any>(
            eventTask, userTask, enrolledTask, favoriteTask, ratingTask, roleTask, waitlistTask, promotedTask
        )
            .addOnSuccessListener {
                if (isGone()) return@addOnSuccessListener
                val event = eventTask.result
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                val user = userTask.result
                val userCourse = user?.course ?: ""
                userName = user?.name ?: ""
                mediaSection.userName = userName
                val isEnrolled = enrolledTask.result
                isFavorite = favoriteTask.result
                // 0 = ainda não avaliou
                val myRating = ratingTask.result
                val isOwner = OrganizerRules.canManage(event, uid, roleTask.result)
                canEdit = isOwner && OrganizerRules.canEdit(event)
                canDelete = isOwner && OrganizerRules.canDelete(event)
                loadedEvent = event
                invalidateOptionsMenu()
                showEvent(event, userCourse, isEnrolled, waitlistTask.result)
                // inscrito automaticamente pela fila: o aviso de inscrição explica o que aconteceu
                if (isEnrolled && promotedTask.result) txtEnrolled.setText(R.string.promoted_banner)
                showStatus(event, isOwner)
                showRatingState(event, isEnrolled, myRating)
                if (event.hasEnded()) loadRatingAverage()
            }
            .addOnFailureListener { error ->
                if (isGone()) return@addOnFailureListener
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showEvent(event: Event, userCourse: String, isEnrolled: Boolean, waitlistPosition: Int) {
        // capa do evento (coleção eventImages, pelo EventImages); some se o evento não tem imagem
        EventImages.show(imgCover, event)
        EventChips.bind(chipCourse, chipAccess, event)
        txtCategory.text = event.category
        txtCategory.visibility = if (event.category.isEmpty()) View.GONE else View.VISIBLE
        txtTitle.text = event.title
        txtDate.text = event.fullDate()
        txtTime.text = event.time()
        txtLocation.text = event.location
        txtCapacity.text = getString(R.string.spots_of_capacity, event.remainingSpots(), event.capacity)
        txtDescription.text = event.description
        showLocation(event)

        showEnrollmentState(event, userCourse, isEnrolled, waitlistPosition)

        progressBar.visibility = View.GONE
        scrollContent.visibility = View.VISIBLE
    }

    private fun showLocation(event: Event) {
        if (event.coordinates == null) {
            cardLocation.visibility = View.GONE
            return
        }
        cardLocation.visibility = View.VISIBLE
        txtAddress.text = event.address
        EventMap.show(mapView, event)
        btnOpenMap.setOnClickListener { EventMap.openExternal(this, event) }
    }

    // o mapa baixa e desenha os pedaços só enquanto a tela está visível
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    private fun showRatingState(event: Event, isEnrolled: Boolean, myRating: Int) {
        if (!RatingRules.canRate(event, isEnrolled)) {
            cardRating.visibility = View.GONE
            return
        }
        cardRating.visibility = View.VISIBLE
        ratingBar.rating = myRating.toFloat()
        showRatingHint(myRating)
    }

    private fun loadRatingAverage() {
        ratingRepository.getSummary(eventId)
            .addOnSuccessListener { summary ->
                val total = summary.count.toInt()
                txtRatingAverage.text = if (total == 0) {
                    getString(R.string.no_ratings)
                } else {
                    // uma casa decimal com vírgula: 4,5
                    val value = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", summary.average)
                    resources.getQuantityString(R.plurals.rating_average, total, value, total)
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
        val value = RatingRules.normalizeStars(stars)
        // se o aluno arrastou até 0, as estrelas voltam para a nota que vai ser salva
        if (value != stars) ratingBar.rating = value.toFloat()

        ratingRepository.save(uid, eventId, value)
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

    private fun showEnrollmentState(event: Event, userCourse: String, isEnrolled: Boolean, waitlistPosition: Int) {
        txtEnrolled.visibility = if (isEnrolled) View.VISIBLE else View.GONE
        txtEnrolled.setText(if (event.hasEnded()) R.string.attended_banner else R.string.enrolled_banner)
        btnCancelEnrollment.visibility =
            if (EnrollmentRules.canCancel(event, isEnrolled)) View.VISIBLE else View.GONE

        // na fila: mostra a posição e o botão de sair, e esconde o de inscrever
        val waiting = !isEnrolled && WaitlistRules.canLeave(event, waitlistPosition)
        txtWaitlist.visibility = if (waiting) View.VISIBLE else View.GONE
        txtWaitlist.text = getString(R.string.waitlist_position, waitlistPosition)
        btnLeaveWaitlist.visibility = if (waiting) View.VISIBLE else View.GONE
        btnLeaveWaitlist.isEnabled = true

        // evento lotado que o aluno poderia fazer: o botão vira "Entrar na lista de espera"
        joinsWaitlist = WaitlistRules.canJoin(event, userCourse, isEnrolled)

        // a regra diz se pode; a tela escolhe o texto do botão para cada motivo.
        // Quem está na fila só vê o botão de inscrever se uma vaga abriu
        val status = EnrollmentRules.check(event, userCourse)
        val canEnrollNow = status == EnrollmentRules.Status.ALLOWED
        btnEnroll.visibility = if (isEnrolled || (waiting && !canEnrollNow)) View.GONE else View.VISIBLE
        btnEnroll.isEnabled = status == EnrollmentRules.Status.ALLOWED || joinsWaitlist
        btnEnroll.text = when (status) {
            EnrollmentRules.Status.ENDED -> getString(R.string.event_ended)
            EnrollmentRules.Status.IN_PROGRESS -> getString(R.string.in_progress)
            EnrollmentRules.Status.NOT_PUBLISHED -> getString(R.string.not_published)
            EnrollmentRules.Status.CLOSED -> getString(R.string.enrollments_closed)
            EnrollmentRules.Status.OTHER_COURSE -> getString(R.string.only_for_course, event.course)
            EnrollmentRules.Status.SOLD_OUT -> getString(if (joinsWaitlist) R.string.join_waitlist else R.string.sold_out)
            EnrollmentRules.Status.ALLOWED -> getString(R.string.enroll)
        }
        btnCancelEnrollment.isEnabled = true
    }

    // aviso da situação do evento e, para o organizador dono, os botões de publicar,
    // encerrar as inscrições e encerrar o evento (OrganizerRules decide quando cada um vale)
    private fun showStatus(event: Event, isOwner: Boolean) {
        val banner = when {
            event.currentStatus() == EventStatus.FINISHED -> getString(R.string.finished_banner)
            event.currentStatus() == EventStatus.DRAFT ->
                getString(if (OrganizerRules.canPublish(event)) R.string.draft_banner else R.string.draft_past_banner)
            event.inProgress() -> inProgressText(event, isOwner)
            event.currentStatus() == EventStatus.CLOSED -> getString(R.string.closed_banner)
            else -> null
        }
        txtEventStatus.visibility = if (banner == null) View.GONE else View.VISIBLE
        txtEventStatus.text = banner

        btnPublish.visibility = visibleIf(isOwner && OrganizerRules.canPublish(event))
        btnCloseEnrollments.visibility = visibleIf(isOwner && OrganizerRules.canCloseEnrollments(event))
        btnFinish.visibility = visibleIf(isOwner && OrganizerRules.canFinish(event))
        btnPublish.isEnabled = true
        btnCloseEnrollments.isEnabled = true
        btnFinish.isEnabled = true
    }

    // em andamento: o dono vê a partir de quando pode encerrar, enquanto o botão ainda não vale
    private fun inProgressText(event: Event, isOwner: Boolean): String {
        val availableAt = OrganizerRules.finishAvailableAt(event)
        if (!isOwner || availableAt == null || OrganizerRules.canFinish(event)) {
            return getString(R.string.in_progress_banner)
        }
        val time = CampusTime.formatter("HH:mm").format(availableAt)
        return getString(R.string.finish_available_at, time)
    }

    private fun visibleIf(condition: Boolean) = if (condition) View.VISIBLE else View.GONE

    private fun confirmPublish() {
        confirm(R.string.publish_event_question, R.string.publish_event) {
            changeStatus(btnPublish, eventRepository.publish(eventId), R.string.event_published)
        }
    }

    private fun confirmCloseEnrollments() {
        confirm(R.string.close_enrollments_question, R.string.close_enrollments) {
            changeStatus(btnCloseEnrollments, eventRepository.closeEnrollments(eventId), R.string.enrollments_closed_done)
        }
    }

    private fun confirmFinish() {
        confirm(R.string.finish_event_question, R.string.finish_event) {
            changeStatus(btnFinish, eventRepository.finish(eventId), R.string.event_finished)
        }
    }

    // exclui o evento e a capa (EventRepository.delete); a tela fecha e a lista de quem abriu recarrega
    private fun confirmDeleteEvent() {
        val event = loadedEvent ?: return
        confirm(R.string.delete_event_question, R.string.delete) {
            eventRepository.delete(event)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.event_deleted, Toast.LENGTH_SHORT).show()
                    finish()
                }
                // alguém se inscreveu enquanto a tela estava aberta: as regras recusam e os dados são lidos de novo
                .addOnFailureListener { error -> showError(error) }
        }
    }

    // diálogo de confirmação com o botão positivo e a ação; o negativo só fecha
    private fun confirm(message: Int, positive: Int, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // grava a nova situação no Firestore (EventRepository.publish/closeEnrollments/finish) e lê os detalhes de novo;
    // se as regras recusarem (por exemplo, a data passou enquanto a tela estava aberta), mostra o erro
    private fun changeStatus(button: MaterialButton, task: Task<Void>, done: Int) {
        button.isEnabled = false
        task
            .addOnSuccessListener {
                Toast.makeText(this, done, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun enroll() {
        btnEnroll.isEnabled = false

        enrollmentRepository.enroll(uid, eventId)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.enrolled_success, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error ->
                // outro aluno pegou a última vaga enquanto a tela estava aberta
                if (EnrollmentRepository.isSoldOut(error)) {
                    Toast.makeText(this, R.string.sold_out, Toast.LENGTH_LONG).show()
                    loadData()
                } else if (EnrollmentRepository.isClosed(error)) {
                    // o organizador encerrou as inscrições enquanto a tela estava aberta
                    Toast.makeText(this, R.string.enrollments_closed, Toast.LENGTH_LONG).show()
                    loadData()
                } else {
                    showError(error)
                }
            }
    }

    // entra no fim da fila (WaitlistRepository.join); as regras recusam se uma vaga abriu nesse meio-tempo
    private fun joinWaitlist() {
        btnEnroll.isEnabled = false
        waitlistRepository.join(uid, eventId)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.waitlist_joined, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun confirmLeaveWaitlist() {
        confirm(R.string.leave_waitlist_question, R.string.leave_waitlist) {
            btnLeaveWaitlist.isEnabled = false
            waitlistRepository.leave(uid, eventId)
                .addOnSuccessListener {
                    Toast.makeText(this, R.string.waitlist_left, Toast.LENGTH_SHORT).show()
                    loadData()
                }
                .addOnFailureListener { error -> showError(error) }
        }
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

        enrollmentRepository.cancel(uid, eventId)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.enrollment_canceled, Toast.LENGTH_SHORT).show()
                loadData()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    // mais novos primeiro, logo abaixo do campo de escrever
    private fun loadComments() {
        commentRepository.getAll(eventId)
            .addOnSuccessListener { comments ->
                rvComments.adapter = CommentAdapter(
                    comments,
                    uid,
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

        commentRepository.add(eventId, uid, userName, text)
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

        commentRepository.update(eventId, comment.id, newText)
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
        commentRepository.delete(eventId, comment.id)
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
