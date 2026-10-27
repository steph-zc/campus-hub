package br.com.uri.campushub.data

import br.com.uri.campushub.data.model.Event
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import java.util.Calendar

// eventos de exemplo gravados quando a coleção "events" ainda está vazia
object SampleEvents {

    // todos os eventos acontecem no campus da URI Santiago
    private const val CAMPUS_ADDRESS = "Rua Batista Bonoto Sobrinho, 733 - São Vicente, Santiago - RS"
    private val CAMPUS_POINT = GeoPoint(-29.1940912, -54.8853573)

    private val events = listOf(
        // eventos que já aconteceram, para testar avaliações e o filtro de encerrados
        Event(
            title = "Palestra: Carreira em Tecnologia",
            description = "Profissionais da região contam como entraram no mercado de " +
                "tecnologia e o que as empresas procuram nos recém-formados.",
            location = "Auditório Central",
            date = date(2026, Calendar.SEPTEMBER, 16, 19, 0),
            capacity = 80,
            course = "Ciência da Computação",
            openToAll = true,
            category = "Palestra",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Workshop de Oratória Jurídica",
            description = "Técnicas de argumentação e postura para sustentações orais. " +
                "Exclusivo para alunos de Direito.",
            location = "Sala 101, Prédio 2",
            date = date(2026, Calendar.SEPTEMBER, 24, 14, 0),
            capacity = 35,
            course = "Direito",
            openToAll = false,
            category = "Workshop",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Dia de Campo: Manejo do Solo",
            description = "Demonstração prática de análise e correção do solo na área " +
                "experimental, com almoço no local.",
            location = "Área Experimental da Agronomia",
            date = date(2026, Calendar.OCTOBER, 2, 8, 0),
            capacity = 50,
            course = "Agronomia",
            openToAll = true,
            category = "Visita técnica",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Seminário de Gestão e Empreendedorismo",
            description = "Ex-alunos que abriram o próprio negócio falam sobre planejamento, " +
                "finanças e os primeiros anos da empresa.",
            location = "Auditório Central",
            date = date(2026, Calendar.OCTOBER, 8, 19, 0),
            capacity = 150,
            course = "Administração",
            openToAll = true,
            category = "Seminário",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),

        // próximos eventos
        Event(
            title = "Semana Acadêmica de Ciência da Computação",
            description = "Palestras, minicursos e maratona de programação. Exclusiva para " +
                "alunos de Ciência da Computação.",
            location = "Auditório Central",
            date = date(2026, Calendar.OCTOBER, 20, 19, 0),
            capacity = 120,
            course = "Ciência da Computação",
            openToAll = false,
            category = "Semana acadêmica",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Palestra: Inteligência Artificial no Agronegócio",
            description = "Como drones, sensores e IA estão mudando o campo. Com a participação " +
                "de produtores da região.",
            location = "Sala 205, Prédio 3",
            date = date(2026, Calendar.OCTOBER, 22, 14, 0),
            capacity = 60,
            course = "Agronomia",
            openToAll = true,
            category = "Palestra",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Workshop de Primeiros Socorros",
            description = "Treinamento prático de atendimento inicial em emergências, " +
                "ministrado por professores da Medicina. Exclusivo para alunos do curso.",
            location = "Laboratório de Habilidades Clínicas",
            date = date(2026, Calendar.OCTOBER, 27, 9, 0),
            capacity = 30,
            course = "Medicina",
            openToAll = false,
            category = "Workshop",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Júri Simulado",
            description = "Os alunos de Direito encenam um julgamento completo. Aberto ao público.",
            location = "Auditório do Direito",
            date = date(2026, Calendar.OCTOBER, 29, 19, 30),
            capacity = 80,
            course = "Direito",
            openToAll = true,
            category = "Workshop",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Feira de Profissões",
            description = "Estandes de todos os cursos da universidade para quem quer conhecer " +
                "as áreas de atuação. Organizada pela Administração.",
            location = "Ginásio de Esportes",
            date = date(2026, Calendar.NOVEMBER, 5, 8, 30),
            capacity = 300,
            course = "Administração",
            openToAll = true,
            category = "Feira",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Visita Técnica a Obra",
            description = "Visita guiada a uma obra de edifício residencial no centro da cidade. " +
                "Exclusiva para alunos de Engenharia Civil, com uso obrigatório de EPI.",
            location = "Saída em frente ao Prédio 5",
            date = date(2026, Calendar.NOVEMBER, 11, 13, 30),
            capacity = 25,
            course = "Engenharia Civil",
            openToAll = false,
            category = "Visita técnica",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Hackathon CampusHub",
            description = "24 horas para criar um app que resolva um problema do campus. " +
                "Equipes de até 4 pessoas, só para alunos de Ciência da Computação.",
            location = "Laboratório de Informática 1",
            date = date(2026, Calendar.NOVEMBER, 13, 8, 0),
            capacity = 40,
            course = "Ciência da Computação",
            openToAll = false,
            category = "Competição",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        ),
        Event(
            title = "Seminário de Arquitetura Sustentável",
            description = "Projetos que reduzem o consumo de energia e água, com cases de " +
                "construções da região.",
            location = "Auditório Central",
            date = date(2026, Calendar.NOVEMBER, 18, 19, 0),
            capacity = 100,
            course = "Arquitetura",
            openToAll = true,
            category = "Seminário",
            address = CAMPUS_ADDRESS,
            coordinates = CAMPUS_POINT
        )
    )

    fun save(db: FirebaseFirestore): Task<Void> {
        // grava todos os eventos de uma vez
        val batch = db.batch()
        for (event in events) {
            batch.set(db.collection("events").document(), event)
        }
        return batch.commit()
    }

    // eventos gravados antes de existir o endereço: completa com os dados do exemplo de mesmo título
    fun addMissingLocations(db: FirebaseFirestore, saved: List<Event>): Task<Void> {
        val batch = db.batch()
        var count = 0
        for (event in saved) {
            if (event.coordinates != null) continue
            val sample = events.find { it.title == event.title } ?: continue
            batch.update(
                db.collection("events").document(event.id),
                mapOf("address" to sample.address, "coordinates" to sample.coordinates)
            )
            count++
        }
        if (count == 0) return Tasks.forResult(null)
        return batch.commit()
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Timestamp {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute, 0)
        return Timestamp(calendar.time)
    }
}
