package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng

// Pedido de um usuário para incluir uma lixeira no mapa.
// Com VOTOS_PARA_APROVAR confirmações de pessoas diferentes ela vira uma lixeira.
data class Solicitacao(
    val id: String,
    val latLng: LatLng,
    val referencia: String,
    val votos: Int
)

// Os mesmos valores estão no firestore.rules: mudar nos dois lugares
const val VOTOS_PARA_APROVAR = 5

// Distância máxima do usuário até o ponto para solicitar ou confirmar
const val RAIO_PRESENCA_METROS = 50.0

// Abaixo disso, um pedido novo é considerado o mesmo lugar de um que já existe
const val RAIO_DUPLICATA_METROS = 30.0

// Item mais próximo de `posicao` a até `raio` metros, ou null
fun <T> maisProximoDentroDe(posicao: LatLng, itens: List<T>, raio: Double, latLng: (T) -> LatLng): T? =
    itens.map { it to metrosEntre(posicao, latLng(it)) }
        .filter { (_, distancia) -> distancia <= raio }
        .minByOrNull { (_, distancia) -> distancia }
        ?.first

// Próximo nome na sequência "Lixeira N°01", "Lixeira N°02"...
fun proximoNomeDeLixeira(nomesExistentes: List<String>): String {
    val maior = nomesExistentes.mapNotNull { nome ->
        Regex("""N°\s*(\d+)""").find(nome)?.groupValues?.get(1)?.toIntOrNull()
    }.maxOrNull() ?: 0
    return "Lixeira N°%02d".format(maior + 1)
}
