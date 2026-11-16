package br.com.uri.campushub.ui.organizer

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.location.AddressGeocoder
import br.com.uri.campushub.data.media.ImageCompressor
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import br.com.uri.campushub.data.model.EventImageChange
import br.com.uri.campushub.data.model.Role
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.domain.CoordinateParser
import br.com.uri.campushub.domain.EventDateTime
import br.com.uri.campushub.domain.EventFormValidator
import br.com.uri.campushub.domain.OrganizerRules
import br.com.uri.campushub.ui.common.EventImages
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.details.EventMap
import br.com.uri.campushub.ui.common.setupToolbar
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.io.File
import java.util.Locale
import org.osmdroid.views.MapView

// formulário do organizador para criar um evento ou editar um evento dele.
// Sem EXTRA_EVENT_ID cria; com o id, carrega o evento e edita.
// Usa: EventFormValidator e EventDateTime (domain) para validar e montar a data;
// AddressGeocoder (data/location) e CoordinateParser (domain) para achar o ponto a partir do endereço
// ou das coordenadas coladas; MapPointPicker (osmdroid) para o organizador marcar o ponto exato;
// Photo Picker do Android + ImageCompressor (data/media) para a imagem de capa, mostrada pelo EventImages;
// EventRepository (Firestore, coleção events) para gravar; UserRepository para o papel e o curso.
// As permissões de verdade ficam no firestore.rules: só o dono do evento consegue gravar
class EventFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"

        // estado guardado ao girar a tela (os campos de texto o Android já guarda sozinho)
        private const val STATE_PICKER_DAY = "picker_day"
        private const val STATE_HOUR = "hour"
        private const val STATE_MINUTE = "minute"
        private const val STATE_LATITUDE = "latitude"
        private const val STATE_LONGITUDE = "longitude"
        private const val STATE_POINT_CLEARED = "point_cleared"
        private const val STATE_IMAGE = "image"

        // imagem nova ainda não salva fica num arquivo do cache: os bytes são grandes demais
        // para o Bundle do onSaveInstanceState (limite de ~1 MB para a tela inteira)
        private const val PENDING_IMAGE_FILE = "event_form_image.jpg"
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollContent: NestedScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var imgCover: ImageView
    private lateinit var txtNoImage: TextView
    private lateinit var progressImage: ProgressBar
    private lateinit var btnPickImage: MaterialButton
    private lateinit var btnRemoveImage: MaterialButton
    private lateinit var edtTitle: TextInputEditText
    private lateinit var edtDescription: TextInputEditText
    private lateinit var dropCategory: AutoCompleteTextView
    private lateinit var edtDate: TextInputEditText
    private lateinit var edtTime: TextInputEditText
    private lateinit var edtLocation: TextInputEditText
    private lateinit var edtAddress: TextInputEditText
    private lateinit var edtCapacity: TextInputEditText
    private lateinit var btnFindOnMap: MaterialButton
    private lateinit var mapView: MapView
    private lateinit var txtMapPoint: TextView
    private lateinit var btnClearPoint: MaterialButton
    private lateinit var switchExclusive: SwitchMaterial
    private lateinit var btnSave: MaterialButton

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val eventRepository = EventRepository()
    private lateinit var geocoder: AddressGeocoder
    private lateinit var mapPicker: MapPointPicker
    private lateinit var compressor: ImageCompressor

    private lateinit var uid: String

    // evento sendo editado; null ao criar
    private var eventId: String? = null
    private var original: Event? = null

    // curso do evento: o do organizador ao criar, o salvo no evento ao editar
    private var course = ""

    // dia escolhido no seletor (meia-noite UTC, como o MaterialDatePicker devolve) e a hora
    private var pickerDay: Long? = null
    private var hour = 19
    private var minute = 0

    // depois de girar a tela: o ponto que estava marcado (null = nenhum) substitui o salvo no evento
    private var restoredState = false
    private var restoredPoint: GeoPoint? = null

    // o mapa só é montado depois que os dados carregam do Firestore
    private var mapReady = false

    // o organizador tirou o ponto de propósito: o Salvar não procura o endereço de novo
    private var pointCleared = false

    // o que acontece com a imagem ao salvar: manter, trocar pelos bytes novos ou remover
    private var imageChange: EventImageChange = EventImageChange.Keep

    // Photo Picker do Android: abre a galeria só com imagens e não pede permissão de armazenamento
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { compressImage(it) }
    }

    private val brazil = Locale.forLanguageTag("pt-BR")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // o osmdroid precisa ser configurado antes do setContentView, que cria o MapView
        EventMap.init(this)
        setContentView(R.layout.activity_event_form)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        scrollContent = findViewById(R.id.scrollContent)
        progressBar = findViewById(R.id.progressBar)
        imgCover = findViewById(R.id.imgCover)
        txtNoImage = findViewById(R.id.txtNoImage)
        progressImage = findViewById(R.id.progressImage)
        btnPickImage = findViewById(R.id.btnPickImage)
        btnRemoveImage = findViewById(R.id.btnRemoveImage)
        edtTitle = findViewById(R.id.edtTitle)
        edtDescription = findViewById(R.id.edtDescription)
        dropCategory = findViewById(R.id.dropCategory)
        edtDate = findViewById(R.id.edtDate)
        edtTime = findViewById(R.id.edtTime)
        edtLocation = findViewById(R.id.edtLocation)
        edtAddress = findViewById(R.id.edtAddress)
        edtCapacity = findViewById(R.id.edtCapacity)
        btnFindOnMap = findViewById(R.id.btnFindOnMap)
        mapView = findViewById(R.id.mapPicker)
        txtMapPoint = findViewById(R.id.txtMapPoint)
        btnClearPoint = findViewById(R.id.btnClearPoint)
        switchExclusive = findViewById(R.id.switchExclusive)
        btnSave = findViewById(R.id.btnSave)

        geocoder = AddressGeocoder(this)
        mapPicker = MapPointPicker(mapView) { point -> showMapPoint(point) }
        compressor = ImageCompressor(this)
        uid = authRepository.requireUserId()
        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId != null) toolbar.setTitle(R.string.edit_event)

        setupToolbar(toolbar)
        applySystemBarsPadding(findViewById(R.id.root), appBar, scrollContent)

        // mesmas categorias do filtro da lista (strings.xml)
        val categories = resources.getStringArray(R.array.categories)
        dropCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, categories))

        edtDate.setOnClickListener { pickDate() }
        edtTime.setOnClickListener { pickTime() }
        btnPickImage.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        btnRemoveImage.setOnClickListener { removeImage() }
        btnFindOnMap.setOnClickListener { findOnMap() }
        btnClearPoint.setOnClickListener {
            pointCleared = true
            mapPicker.clear()
        }
        btnSave.setOnClickListener { save() }

        if (savedInstanceState != null) {
            pickerDay = savedInstanceState.getLong(STATE_PICKER_DAY, -1).takeIf { it >= 0 }
            hour = savedInstanceState.getInt(STATE_HOUR, hour)
            minute = savedInstanceState.getInt(STATE_MINUTE, minute)
            restoredState = true
            imageChange = restoreImageChange(savedInstanceState.getString(STATE_IMAGE))
            pointCleared = savedInstanceState.getBoolean(STATE_POINT_CLEARED)
            if (savedInstanceState.containsKey(STATE_LATITUDE)) {
                restoredPoint = GeoPoint(
                    savedInstanceState.getDouble(STATE_LATITUDE),
                    savedInstanceState.getDouble(STATE_LONGITUDE)
                )
            }
        }

        // formulário aberto do zero: uma imagem esquecida de uma edição abandonada é apagada
        if (savedInstanceState == null) pendingImageFile().delete()
        loadData(fillFields = savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pickerDay?.let { outState.putLong(STATE_PICKER_DAY, it) }
        outState.putInt(STATE_HOUR, hour)
        outState.putInt(STATE_MINUTE, minute)
        outState.putBoolean(STATE_POINT_CLEARED, pointCleared)
        outState.putString(STATE_IMAGE, when (imageChange) {
            EventImageChange.Keep -> "keep"
            is EventImageChange.Replace -> "replace"
            EventImageChange.Remove -> "remove"
        })
        // antes do mapa ser montado, guarda de novo o que veio da rotação anterior
        val point = if (mapReady) mapPicker.point else restoredPoint
        point?.let {
            outState.putDouble(STATE_LATITUDE, it.latitude)
            outState.putDouble(STATE_LONGITUDE, it.longitude)
        }
    }

    // o MapView do osmdroid acompanha o ciclo de vida da tela (para de baixar o mapa em segundo plano)
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    // busca no Firestore o papel e o perfil do organizador e, na edição, o evento.
    // fillFields = false depois de girar a tela: os campos já têm o que foi digitado
    private fun loadData(fillFields: Boolean) {
        val roleTask = userRepository.getRole(uid)
        val userTask = userRepository.get(uid)
        val eventTask: Task<Event?> = eventId?.let { eventRepository.get(it) } ?: Tasks.forResult(null)

        Tasks.whenAllSuccess<Any>(roleTask, userTask, eventTask)
            .addOnSuccessListener {
                val event = eventTask.result
                // só o organizador responsável edita; na criação basta ser organizador
                val allowed = if (eventId == null) {
                    roleTask.result == Role.ORGANIZER
                } else {
                    event != null && OrganizerRules.canManage(event, uid, roleTask.result)
                }
                if (!allowed) {
                    Toast.makeText(this, R.string.not_event_organizer, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                original = event
                course = event?.course ?: userTask.result?.course ?: ""
                switchExclusive.text = getString(R.string.exclusive_for_course, course)
                if (fillFields) event?.let { fillFields(it) }
                showDateAndTime()
                // ponto inicial: o da rotação anterior ou o salvo no evento (evento novo começa no campus)
                mapPicker.start(if (restoredState) restoredPoint else event?.coordinates)
                mapReady = true
                showMapPoint(mapPicker.point)
                showImage()

                progressBar.visibility = View.GONE
                scrollContent.visibility = View.VISIBLE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    // edição: o formulário começa com o que está salvo no evento
    private fun fillFields(event: Event) {
        edtTitle.setText(event.title)
        edtDescription.setText(event.description)
        dropCategory.setText(event.category, false)
        edtLocation.setText(event.location)
        edtAddress.setText(event.address)
        edtCapacity.setText(event.capacity.toString())
        switchExclusive.isChecked = !event.openToAll

        event.date?.toDate()?.let { date ->
            pickerDay = EventDateTime.toPickerUtcMillis(date)
            val local = Calendar.getInstance().apply { time = date }
            hour = local.get(Calendar.HOUR_OF_DAY)
            minute = local.get(Calendar.MINUTE)
        }
    }

    // seletor de data do Material; só deixa escolher de hoje em diante
    private fun pickDate() {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointForward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.event_date)
            .setSelection(pickerDay ?: MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { selection ->
            pickerDay = selection
            showDateAndTime()
        }
        picker.show(supportFragmentManager, "date")
    }

    private fun pickTime() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(R.string.event_time)
            .build()
        picker.addOnPositiveButtonClickListener {
            hour = picker.hour
            minute = picker.minute
            showDateAndTime()
        }
        picker.show(supportFragmentManager, "time")
    }

    private fun showDateAndTime() {
        edtDate.setText(selectedDate()?.let { SimpleDateFormat("dd/MM/yyyy", brazil).format(it) } ?: "")
        edtTime.setText(String.format(brazil, "%02d:%02d", hour, minute))
    }

    private fun selectedDate(): Date? = pickerDay?.let { EventDateTime.combine(it, hour, minute) }

    // ---------- imagem de capa ----------

    // reduz e comprime a foto escolhida fora da thread da tela; o resultado só é gravado no Salvar
    private fun compressImage(uri: Uri) {
        setImageBusy(true)
        compressor.compress(uri)
            .addOnSuccessListener { jpeg ->
                pendingImageFile().writeBytes(jpeg)
                imageChange = EventImageChange.Replace(jpeg)
                setImageBusy(false)
                showImage()
            }
            .addOnFailureListener {
                setImageBusy(false)
                Toast.makeText(this, R.string.image_error, Toast.LENGTH_LONG).show()
            }
    }

    // tirar a imagem: o evento salvo perde a capa; uma imagem só escolhida (não salva) é descartada
    private fun removeImage() {
        pendingImageFile().delete()
        imageChange = if (original?.withImage() == true) EventImageChange.Remove else EventImageChange.Keep
        showImage()
    }

    // mostra a imagem nova, a salva no evento ou o aviso de "nenhuma imagem"
    private fun showImage() {
        val change = imageChange
        val saved = original
        val hasImage = when (change) {
            is EventImageChange.Replace -> {
                EventImages.load(imgCover, change.jpeg)
                true
            }
            EventImageChange.Keep -> saved?.withImage() == true && run {
                EventImages.show(imgCover, saved)
                true
            }
            EventImageChange.Remove -> false
        }
        imgCover.visibility = if (hasImage) View.VISIBLE else View.GONE
        txtNoImage.visibility = if (hasImage) View.GONE else View.VISIBLE
        btnRemoveImage.visibility = if (hasImage) View.VISIBLE else View.GONE
        btnPickImage.setText(if (hasImage) R.string.change_image else R.string.pick_image)
    }

    private fun setImageBusy(busy: Boolean) {
        progressImage.visibility = if (busy) View.VISIBLE else View.GONE
        btnPickImage.isEnabled = !busy
        btnRemoveImage.isEnabled = !busy
    }

    private fun pendingImageFile() = File(cacheDir, PENDING_IMAGE_FILE)

    // depois de girar a tela: a imagem nova volta do arquivo do cache
    private fun restoreImageChange(state: String?): EventImageChange = when (state) {
        "replace" -> pendingImageFile().takeIf { it.exists() }
            ?.let { EventImageChange.Replace(it.readBytes()) } ?: EventImageChange.Keep
        "remove" -> EventImageChange.Remove
        else -> EventImageChange.Keep
    }

    // ---------- mapa ----------

    // botão Localizar no mapa: coordenadas coladas vão direto para o marcador;
    // um endereço é procurado no Geocoder do Android (precisa de internet)
    private fun findOnMap() {
        val text = edtAddress.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.event_fill_required, Toast.LENGTH_SHORT).show()
            return
        }
        btnFindOnMap.isEnabled = false
        locate(text).addOnCompleteListener { task ->
            btnFindOnMap.isEnabled = true
            val point = task.result
            if (point == null) {
                Toast.makeText(this, R.string.address_not_found, Toast.LENGTH_LONG).show()
            } else {
                pointCleared = false
                mapPicker.moveTo(point)
            }
        }
    }

    // texto do campo de endereço → ponto: primeiro tenta ler como coordenadas, depois o Geocoder.
    // O AddressGeocoder não falha: sem resultado ou sem internet ele devolve null
    private fun locate(text: String): Task<GeoPoint?> =
        CoordinateParser.parse(text)?.let { Tasks.forResult<GeoPoint?>(it) } ?: geocoder.find(text)

    // coordenadas embaixo do mapa, para o organizador conferir (e poder copiar)
    private fun showMapPoint(point: GeoPoint?) {
        txtMapPoint.text = point?.let { CoordinateParser.format(it) } ?: getString(R.string.map_point_none)
        btnClearPoint.isEnabled = point != null
        // tocar ou arrastar no mapa marca um ponto de novo
        if (point != null) pointCleared = false
    }

    // valida com o EventFormValidator, define o ponto do mapa e grava no Firestore
    private fun save() {
        val form = EventFormValidator.Form(
            title = edtTitle.text.toString(),
            description = edtDescription.text.toString(),
            category = dropCategory.text.toString(),
            date = selectedDate(),
            location = edtLocation.text.toString(),
            address = edtAddress.text.toString(),
            capacity = edtCapacity.text.toString()
        )
        val saved = original
        val result = EventFormValidator.validate(
            form,
            originalDate = saved?.date?.toDate(),
            enrolledCount = saved?.enrolledCount ?: 0
        )
        if (result != EventFormValidator.Result.VALID) {
            showValidationError(result, saved?.enrolledCount ?: 0)
            return
        }

        setSaving(true)
        val address = form.address.trim()
        // o ponto marcado no mapa vale; sem ponto, procura o endereço (como o botão Localizar no mapa),
        // a não ser que o organizador tenha tirado o ponto de propósito
        val marked = mapPicker.point
        val coordinatesTask: Task<GeoPoint?> = when {
            marked != null -> Tasks.forResult(marked)
            pointCleared -> Tasks.forResult(null)
            else -> locate(address)
        }

        coordinatesTask
            .onSuccessTask { coordinates ->
                val draft = EventDraft(
                    title = form.title.trim(),
                    description = form.description.trim(),
                    category = form.category.trim(),
                    date = Timestamp(form.date!!),
                    location = form.location.trim(),
                    address = address,
                    coordinates = coordinates,
                    capacity = form.capacity.trim().toInt(),
                    course = course,
                    openToAll = !switchExclusive.isChecked
                )
                val id = eventId
                val change = imageChange
                val write: Task<*> = if (id == null) {
                    eventRepository.create(uid, draft, (change as? EventImageChange.Replace)?.jpeg)
                } else {
                    eventRepository.update(id, draft, change)
                }
                // whenAll repassa a falha da gravação (por exemplo, recusada pelas regras)
                Tasks.whenAll(write)
            }
            .addOnSuccessListener {
                val message = if (eventId == null) R.string.event_created else R.string.event_updated
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                pendingImageFile().delete()
                // avisa a tela anterior para recarregar o evento
                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { error ->
                setSaving(false)
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun showValidationError(result: EventFormValidator.Result, enrolledCount: Int) {
        val message = when (result) {
            EventFormValidator.Result.EMPTY_FIELDS -> getString(R.string.event_fill_required)
            EventFormValidator.Result.TOO_LONG -> getString(R.string.event_text_too_long)
            EventFormValidator.Result.PAST_DATE -> getString(R.string.event_past_date)
            EventFormValidator.Result.INVALID_CAPACITY ->
                getString(R.string.event_invalid_capacity, EventFormValidator.MAX_CAPACITY)
            EventFormValidator.Result.CAPACITY_BELOW_ENROLLED ->
                getString(R.string.event_capacity_below_enrolled, enrolledCount)
            EventFormValidator.Result.VALID -> return
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // evita dois toques no Salvar enquanto o Firestore responde
    private fun setSaving(saving: Boolean) {
        btnSave.isEnabled = !saving
        btnPickImage.isEnabled = !saving
        btnRemoveImage.isEnabled = !saving
        progressBar.visibility = if (saving) View.VISIBLE else View.GONE
    }
}
