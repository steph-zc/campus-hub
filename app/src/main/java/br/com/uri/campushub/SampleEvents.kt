package br.com.uri.campushub

import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

// eventos de exemplo gravados quando a coleção "events" ainda está vazia
object SampleEvents {

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
            category = "Palestra"
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
            category = "Workshop"
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
            category = "Visita técnica"
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
            category = "Seminário"
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
            category = "Semana acadêmica"
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
            category = "Palestra"
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
            category = "Workshop"
        ),
        Event(
            title = "Júri Simulado",
            description = "Os alunos de Direito encenam um julgamento completo. Aberto ao público.",
            location = "Auditório do Direito",
            date = date(2026, Calendar.OCTOBER, 29, 19, 30),
            capacity = 80,
            course = "Direito",
            openToAll = true,
            category = "Workshop"
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
            category = "Feira"
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
            category = "Visita técnica"
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
            category = "Competição"
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
            category = "Seminário"
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

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Timestamp {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute, 0)
        return Timestamp(calendar.time)
    }
}
