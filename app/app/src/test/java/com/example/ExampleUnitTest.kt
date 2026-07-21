package com.example

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.example.data.api.KodyarRetrofitClient

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testFetchDatabase() {
    runBlocking {
        try {
            val response = KodyarRetrofitClient.service.getDatabase()
            println("SUCCESS: Fetched database successfully!")
            println("Cities: ${response.citiesList}")
            println("Technicians count: ${response.technicians?.size}")
            response.technicians?.forEach { tech ->
                println("TECH -> ID: ${tech.id}, Name: ${tech.name}, City: ${tech.city}, isVerified: ${tech.isVerified}")
            }
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
        }
    }
  }
}


