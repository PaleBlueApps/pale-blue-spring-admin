package com.paleblueapps.springadminexample.example

import com.paleblueapps.springadmin.web.AdminIndexController
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice(assignableTypes = [AdminIndexController::class])
class AdminExampleIndexAdvice {

    @ModelAttribute("adminWelcomeMessage")
    fun adminWelcomeMessage(): String = "Custom index content rendered before entities"
}
