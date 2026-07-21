package com.example.data.model

data class ErrorCode(
    val code: String,
    val brand: String, // LG, Samsung, Bosch, Snowa, Pars, etc.
    val category: String, // لباسشویی, یخچال, ظرفشویی, کولر گازی, غیره
    val description: String, // علت خطا
    val solution: String, // راه حل رفع عیب
    val severity: String // آسان, متوسط, بحرانی
)
