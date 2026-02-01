package com.da_emi_locker.backend

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<BackendApplication>().with(TestcontainersConfiguration::class).run(*args)
}
