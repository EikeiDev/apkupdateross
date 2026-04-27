package com.apkupdateross

import com.aurora.gplayapi.data.models.details.TestingProgram
import org.junit.Test
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Field

class ScratchTest {
    @Test
    fun printTestingProgram() {
        val output = StringBuilder()
        output.append("FIELDS:\n")
        val fields: Array<Field> = TestingProgram::class.java.declaredFields
        fields.forEach { field ->
            output.append(field.name + " : " + field.type.name + "\n")
        }
        File("test_output2.txt").writeText(output.toString())
    }
}
