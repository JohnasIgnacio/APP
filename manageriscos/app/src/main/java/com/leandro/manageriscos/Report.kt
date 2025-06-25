package com.leandro.manageriscos

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties

enum class StatusRisco(val descricao: String) {
    ABERTO("Aberto"),
    EM_ANALISE("Em Análise"),
    EM_ANDAMENTO("Em Andamento"),
    RESOLVIDO("Resolvido"),
    INVALIDO("Inválido");

    companion object {
        fun getAllDescriptions(): List<String> {
            return entries.map { it.descricao }
        }

        fun fromDescricao(descricao: String?): StatusRisco? {
            return entries.find { it.descricao == descricao }
        }
    }
}

@IgnoreExtraProperties
data class Report(
    @get:Exclude var id: String? = null, // ID do Firebase, @get:Exclude para não ser salvo de volta
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val location: String? = null, // "Lat: -22.832183, Long: -47.2176352"
    val reportDate: Long? = null,
    val userId: String? = null,
    var status: String? = StatusRisco.ABERTO.descricao,
    var observacoes: String? = null
) {

    constructor() : this(
        id = null,
        title = null,
        description = null,
        imageUrl = null,
        location = null,
        reportDate = null,
        userId = null,
        status = StatusRisco.ABERTO.descricao,
        observacoes = null
    )


    fun getLatLng(): Pair<Double, Double>? {
        if (location == null) return null
        return try {
            val parts = location.split(", ")
            val latStr = parts[0].substringAfter("Lat: ").trim()
            val lngStr = parts[1].substringAfter("Long: ").trim()
            Pair(latStr.toDouble(), lngStr.toDouble())
        } catch (e: Exception) {

            System.err.println("Erro ao parsear localização: $location - ${e.message}")
            null
        }
    }
}