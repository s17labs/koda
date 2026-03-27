package com.s17labs.koda.model

data class KodaMenuItem(
    val id: Int,
    val title: String,
    val icon: Int,
    val action: () -> Unit
)