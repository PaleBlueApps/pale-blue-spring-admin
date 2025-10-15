package com.paleblueapps.springadmin.web

import com.paleblueapps.springadmin.autoconfigure.AdminProperties
import com.paleblueapps.springadmin.core.AdminCrudService
import com.paleblueapps.springadmin.core.AdminEntityRegistry
import com.paleblueapps.springadmin.core.PaginationInfo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
@RequestMapping(path = ["\${spring.data.admin.base-path:/admin}"])
class AdminEntityController(
    private val crud: AdminCrudService,
    private val registry: AdminEntityRegistry,
    private val props: AdminProperties,
) {
    @GetMapping("{entity}")
    fun list(
        @PathVariable entity: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "-1") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) dir: String?,
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val desc = registry.get(entity) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val attributeTitles = desc.attributes.map { it.name }
        val pageSize =
            if (size <= 0) {
                props.pagination.defaultSize
            } else {
                size.coerceAtMost(props.pagination.maxSize)
            }
        val data = crud.list(entity, page, pageSize, sort, dir, q)
        val rows =
            data.content.map { entity ->
                attributeTitles.map { attribute ->
                    try {
                        val field = entity.javaClass.getDeclaredField(attribute)
                        field.isAccessible = true
                        field.get(entity)
                    } catch (ex: Exception) {
                        null
                    }
                }
            }
        // Create pagination info with pre-calculated values for the view
        val paginationInfo = PaginationInfo.from(data)

        model.addAttribute("title", props.ui.title)
        model.addAttribute("descriptor", desc)
        model.addAttribute("attributeTitles", attributeTitles)
        model.addAttribute("data", data)
        model.addAttribute("rows", rows)
        model.addAttribute("pagination", paginationInfo)
        model.addAttribute("basePath", props.basePath)
        model.addAttribute("sort", sort)
        model.addAttribute("dir", dir)
        model.addAttribute("q", q)
        model.addAttribute("size", pageSize)
        // Page size options for UI (bounded by max)
        val options = listOf(10, 25, 50, 100, 200).filter { it <= props.pagination.maxSize }
        model.addAttribute("pageSizes", options)
        // UI context for sidebar and branding
        model.addAttribute("entities", registry.all())

        return "sda/entity-list"
    }
}
