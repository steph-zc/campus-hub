package br.com.uri.campushub

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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.Normalizer

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

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

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

        // a Toolbar do layout passa a ser a barra da tela, com o menu
        setSupportActionBar(toolbar)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding()

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

    // no Android 15+ o app desenha atrás das barras do sistema, então
    // o topo desce até sair da barra de status e a lista termina acima da barra de navegação
    private fun applySystemBarsPadding() {
        val space = resources.getDimensionPixelSize(R.dimen.list_padding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(0, bars.top, 0, 0)
            rvEvents.setPadding(space, space, space, space + bars.bottom)
            insets
        }
    }

    private fun loadUserName() {
        val uid = auth.currentUser!!.uid

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name")
                txtHello.text = getString(R.string.hello_user, name)
            }
    }

    private fun loadEvents() {
        db.collection("events").orderBy("date").get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    // primeira execução: cria os eventos de exemplo e busca de novo
                    SampleEvents.save(db)
                        .addOnSuccessListener { loadEvents() }
                        .addOnFailureListener { error -> showError(error) }
                    return@addOnSuccessListener
                }

                // próximos primeiro, do mais perto para o mais longe;
                // depois os encerrados, do mais recente para o mais antigo
                val (ended, upcoming) = result.toObjects(Event::class.java).partition { it.hasEnded() }
                allEvents = upcoming + ended.reversed()
                progressBar.visibility = View.GONE
                // reaplica a busca, caso o aluno tenha voltado de outra tela com texto digitado
                applyFilters()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    // os três filtros valem juntos: título, situação e categoria
    private fun applyFilters() {
        val typed = edtSearch.text.toString().trim()
        val query = normalize(typed)
        val status = groupStatus.checkedChipId
        val category = findViewById<Chip>(groupCategory.checkedChipId)?.tag as String? ?: ""

        val events = allEvents.filter { event ->
            val matchesTitle = normalize(event.title).contains(query)
            val matchesStatus = when (status) {
                R.id.chipUpcoming -> !event.hasEnded()
                R.id.chipEnded -> event.hasEnded()
                else -> true
            }
            val matchesCategory = category.isEmpty() || event.category == category
            matchesTitle && matchesStatus && matchesCategory
        }

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

    // "Palestra Inteligência" e "palestra inteligencia" ficam iguais:
    // tira os acentos e deixa tudo minúsculo antes de comparar
    private fun normalize(text: String): String {
        val withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase()
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
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
