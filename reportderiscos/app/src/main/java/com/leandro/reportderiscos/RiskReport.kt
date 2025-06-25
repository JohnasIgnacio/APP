package com.leandro.reportderiscos

//enum StatusRisco

data class RiskReport(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val imageUrl: String = "",
    val userId: String = "",
    val reportDate: Long = 0L,
    val status: String = "Aberto" // NOVO CAMPO com valor padrão
)