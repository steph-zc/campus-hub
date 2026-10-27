package br.com.uri.campushub.ui.events

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.domain.EventFilter
import br.com.uri.campushub.ui.auth.MainActivity
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.common.setupToolbar
import br.com.uri.campushub.ui.details.EventDetailsActivity
import br.com.uri.campushub.ui.profile.ProfileActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class EventsActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var txtHello: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvEvents: RecyclerView
    private lateinit var edtSearch: TextInputEditText
    private lateinit var groupStatus: ChipGroup
    private lateinit var groupCategory: ChipGroup
    private lateinit var txtNoResults: TextView

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()
    private val eventRepository = EventRepository()

    // todos os eventos do Firestore; a lista mostrada é esta depois de filtrada
    private var allEvents = listOf<Event>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        txtHello = findViewById(R.id.txtHello)
        progressBar = findViewById(R.id.progressBar)
        rvEvents = findViewById(R.id.rvEvents)
        edtSearch = findViewById(R.id.edtSearch)
        groupStatus = findViewById(R.id.groupStatus)
        groupCategory = findViewById(R.id.groupCategory)
        txtNoResults = findViewById(R.id.txtNoResults)

        // tela principal: barra com o menu, sem seta de voltar
        setupToolbar(toolbar, showBackArrow = false)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding(findViewById(R.id.root), appBar, rvEvents)

        // filtra a cada letra digitada, sem precisar consultar o Firestore de novo
        edtSearch.doAfterTextChanged { applyFilters() }
        edtSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) hideKeyboard()
            false
        }

        createCategoryChips()
        groupStatus.setOnCheckedStateChangeListener { _, _ -> applyFilters() }
        groupCategory.setOnCheckedStateChangeListener { _, _ -> applyFilters() }
    }

    // uma opção "Todas" e uma para cada categoria; a tag guarda o nome usado no filtro
    private fun createCategoryChips() {
        val names = listOf("") + resources.getStringArray(R.array.categories)
        for (name in names) {
            val chip = layoutInflater.inflate(R.layout.item_filter_chip, groupCategory, false) as Chip
            chip.id = View.generateViewId()
            chip.tag = name
            chip.text = name.ifEmpty { getString(R.string.filter_all_categories) }
            groupCategory.addView(chip)
            if (name.isEmpty()) chip.isChecked = true
        }
    }

    // recarrega ao voltar de outra tela: as vagas mudam com as inscrições
    // e o nome pode ter sido trocado no perfil
    override fun onResume() {
        super.onResume()
        loadUserName()
        loadEvents()
    }

    private fun loadUserName() {
        userRepository.get(authRepository.requireUserId())
            .addOnSuccessListener { user ->
                txtHello.text = getString(R.string.hello_user, user?.name ?: "")
            }
    }

    private fun loadEvents() {
        eventRepository.getAll()
            .addOnSuccessListener { events ->
                if (events.isEmpty()) {
                    // primeira execução: cria os eventos de exemplo e busca de novo
                    eventRepository.saveSampleEvents()
                        .addOnSuccessListener { loadEvents() }
                        .addOnFailureListener { error -> showError(error) }
                    return@addOnSuccessListener
                }

                allEvents = EventFilter.sortForList(events)
                progressBar.visibility = View.GONE
                // reaplica a busca, caso o aluno tenha voltado de outra tela com texto digitado
                applyFilters()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    // lê o que está marcado na tela e deixa o EventFilter decidir quais eventos aparecem
    private fun applyFilters() {
        val typed = edtSearch.text.toString().trim()
        val status = when (groupStatus.checkedChipId) {
            R.id.chipUpcoming -> EventFilter.Status.UPCOMING
            R.id.chipEnded -> EventFilter.Status.ENDED
            else -> EventFilter.Status.ALL
        }
        val category = findViewById<Chip>(groupCategory.checkedChipId)?.tag as String? ?: ""

        val events = EventFilter.filter(allEvents, typed, status, category)

        rvEvents.adapter = EventAdapter(events) { event -> openDetails(event) }

        // a mensagem só faz sentido depois que os eventos carregaram
        val noResults = events.isEmpty() && allEvents.isNotEmpty()
        txtNoResults.visibility = if (noResults) View.VISIBLE else View.GONE
        txtNoResults.text = if (typed.isNotEmpty()) {
            getString(R.string.no_results, typed)
        } else {
            getString(R.string.no_results_filters)
        }
    }

    private fun hideKeyboard() {
        val keyboard = getSystemService(InputMethodManager::class.java)
        keyboard.hideSoftInputFromWindow(edtSearch.windowToken, 0)
        edtSearch.clearFocus()
    }

    private fun openDetails(event: Event) {
        // manda só o id; a tela de detalhes busca o evento atualizado no Firestore
        val intent = Intent(this, EventDetailsActivity::class.java)
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.id)
        startActivity(intent)
    }

    private fun showError(error: Exception) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_events, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuMyEvents -> {
                openMyList(MyEventsActivity.LIST_ENROLLMENTS)
                return true
            }
            R.id.menuFavorites -> {
                openMyList(MyEventsActivity.LIST_FAVORITES)
                return true
            }
            R.id.menuProfile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                return true
            }
            R.id.menuLogout -> {
                logout()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    // a mesma tela mostra as inscrições ou os favoritos
    private fun openMyList(list: String) {
        val intent = Intent(this, MyEventsActivity::class.java)
        intent.putExtra(MyEventsActivity.EXTRA_LIST, list)
        startActivity(intent)
    }

    private fun logout() {
        authRepository.logout()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
