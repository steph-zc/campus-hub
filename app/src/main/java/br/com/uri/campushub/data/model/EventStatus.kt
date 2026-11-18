package br.com.uri.campushub.data.model

// situação do evento, gravada no campo "status" do documento events/{id}:
// DRAFT (rascunho, só o organizador vê) → PUBLISHED (aparece para os alunos e aceita inscrições)
// → CLOSED (inscrições encerradas; o evento continua visível) → FINISHED (o evento acabou,
// encerrado pelo organizador depois do horário de início). PUBLISHED também pode ir direto para FINISHED.
// O caminho só anda para a frente, e o firestore.rules confere a mesma ordem no servidor
enum class EventStatus(val value: String) {
    DRAFT("draft"),
    PUBLISHED("published"),
    CLOSED("closed"),
    FINISHED("finished");

    companion object {
        // situações que os alunos enxergam; é o filtro da consulta da lista de eventos
        val VISIBLE = listOf(PUBLISHED.value, CLOSED.value, FINISHED.value)

        // valor gravado → situação; eventos antigos (sem o campo) ou com valor desconhecido
        // contam como publicados, igual ao padrão do firestore.rules
        fun from(value: String?) = entries.find { it.value == value } ?: PUBLISHED
    }
}
