package com.paleblueapps.springadmin.web

import com.paleblueapps.springadmin.autoconfigure.AdminProperties
import com.paleblueapps.springadmin.core.AdminEntityRegistry
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping(path = ["\${spring.data.admin.base-path:/admin}"])
class AdminIndexController(
    private val registry: AdminEntityRegistry,
    private val props: AdminProperties,
) {
    @GetMapping("")
    fun index(model: Model): String {
        model.addAttribute("title", props.ui.title)
        model.addAttribute("entities", registry.all())
        model.addAttribute("basePath", props.basePath)
        return "sda/index"
    }
}
