package br.com.uri.campushub.data.model

// o que o formulário do organizador faz com a imagem do evento ao salvar a edição.
// O EventRepository transforma isso em gravações no Firestore: o campo imageVersion do evento
// e o documento eventImages/{eventId} com os bytes do JPEG
sealed interface EventImageChange {

    // a imagem salva continua como está (ou o evento continua sem imagem)
    data object Keep : EventImageChange

    // imagem nova escolhida na galeria, já comprimida em JPEG pelo ImageCompressor (data/media)
    class Replace(val jpeg: ByteArray) : EventImageChange

    // o organizador tirou a imagem
    data object Remove : EventImageChange
}
