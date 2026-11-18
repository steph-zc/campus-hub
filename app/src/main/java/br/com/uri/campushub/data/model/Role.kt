package br.com.uri.campushub.data.model

// perfil de acesso da conta: decide quais telas e ações o app mostra
// (as permissões de verdade ficam no firestore.rules, que confere o mesmo documento no servidor)
enum class Role {
    // padrão de toda conta criada pelo cadastro: consulta eventos e se inscreve
    STUDENT,

    // conta com organizers/{uid}, criado no console do Firebase: cuida dos próprios eventos
    ORGANIZER
}
