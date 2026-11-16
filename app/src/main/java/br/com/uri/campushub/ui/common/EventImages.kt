package br.com.uri.campushub.ui.common

import android.content.Context
import android.view.View
import android.widget.ImageView
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.repository.EventImageRepository
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.nio.ByteBuffer

// mostra a imagem de capa do evento numa ImageView (lista, detalhes e formulário do organizador).
// Ensina o Glide a buscar a capa no Firestore (EventImageRepository, coleção eventImages):
// o Glide cuida do cache em memória e em disco, da redução para o tamanho da view e de cancelar
// a busca quando o item sai da tela. A chave do cache é o id do evento + imageVersion,
// então uma capa trocada é baixada de novo e a mesma capa não é baixada duas vezes
object EventImages {

    // o que o Glide carrega: a capa do evento numa versão específica (data class = chave do cache)
    data class Cover(val eventId: String, val version: Long)

    private var registered = false

    // capa do evento na view; sem imagem, a view some (a tela decide o espaço que sobra)
    fun show(view: ImageView, event: Event) {
        if (!event.withImage()) {
            Glide.with(view).clear(view)
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        load(view, Cover(event.id, event.imageVersion))
    }

    // carrega qualquer modelo aceito pelo Glide (a capa salva ou os bytes de uma imagem nova)
    fun load(view: ImageView, model: Any) {
        register(view.context.applicationContext)
        Glide.with(view).load(model).centerCrop().into(view)
    }

    // registra o carregador uma vez só, na primeira capa mostrada
    @Synchronized
    private fun register(context: Context) {
        if (registered) return
        Glide.get(context).registry.prepend(Cover::class.java, ByteBuffer::class.java, Factory(EventImageRepository()))
        registered = true
    }

    private class Factory(private val repository: EventImageRepository) : ModelLoaderFactory<Cover, ByteBuffer> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Cover, ByteBuffer> = Loader(repository)
        override fun teardown() = Unit
    }

    private class Loader(private val repository: EventImageRepository) : ModelLoader<Cover, ByteBuffer> {
        override fun buildLoadData(model: Cover, width: Int, height: Int, options: Options) =
            ModelLoader.LoadData(ObjectKey(model), Fetcher(repository, model))

        override fun handles(model: Cover) = true
    }

    // busca os bytes no Firestore; o Glide aceita a resposta em qualquer thread
    private class Fetcher(
        private val repository: EventImageRepository,
        private val cover: Cover
    ) : DataFetcher<ByteBuffer> {

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in ByteBuffer>) {
            repository.read(cover.eventId)
                .addOnSuccessListener { jpeg ->
                    if (jpeg == null) {
                        callback.onLoadFailed(IllegalStateException("evento sem imagem"))
                    } else {
                        callback.onDataReady(ByteBuffer.wrap(jpeg))
                    }
                }
                .addOnFailureListener { error -> callback.onLoadFailed(error) }
        }

        // a busca do Firestore não tem como ser interrompida; o Glide só ignora a resposta
        override fun cleanup() = Unit
        override fun cancel() = Unit
        override fun getDataClass() = ByteBuffer::class.java

        // REMOTE: o Glide guarda os bytes no cache em disco, para a próxima abertura não buscar de novo
        override fun getDataSource() = DataSource.REMOTE
    }
}
