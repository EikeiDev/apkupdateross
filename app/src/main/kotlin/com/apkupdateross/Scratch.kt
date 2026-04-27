package com.apkupdateross

import com.aurora.gplayapi.data.models.App
import java.lang.reflect.Method

fun printAppMethods() {
    val methods: Array<Method> = App::class.java.methods
    methods.forEach { method ->
        println(method.name + " : " + method.returnType.name)
    }
}
