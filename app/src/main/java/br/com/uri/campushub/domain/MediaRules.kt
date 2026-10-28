package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.MediaPost

// regras das publicações de fotos, áudios e vídeos
object MediaRules {

    // qualquer aluno logado publica, mas só o autor exclui
    fun canDelete(post: MediaPost, userId: String) =
        userId.isNotEmpty() && post.userId == userId
}
