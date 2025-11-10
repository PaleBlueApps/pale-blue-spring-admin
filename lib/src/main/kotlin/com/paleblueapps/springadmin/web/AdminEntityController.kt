package com.paleblueapps.springadmin.web

import com.paleblueapps.springadmin.autoconfigure.AdminProperties
import com.paleblueapps.springadmin.core.AdminCrudService
import com.paleblueapps.springadmin.core.AdminEntityRegistry
import com.paleblueapps.springadmin.core.PaginationInfo
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.metamodel.Attribute
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
            data.content.map { entityObj ->
                attributeTitles.map { attribute ->
                    try {
                        val field = entityObj.javaClass.getDeclaredField(attribute)
                        field.isAccessible = true
                        field.get(entityObj)
                    } catch (ex: Exception) {
                        null
                    }
                }
            }
        val rowIds = data.content.map { crud.getId(it) }
        val paginationInfo = PaginationInfo.from(data)

        model.addAttribute("title", props.ui.title)
        model.addAttribute("descriptor", desc)
        model.addAttribute("attributeTitles", attributeTitles)
        model.addAttribute("data", data)
        model.addAttribute("rows", rows)
        model.addAttribute("rowIds", rowIds)
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

    @GetMapping("{entity}/{id}")
    fun detail(
        @PathVariable entity: String,
        @PathVariable id: String,
        model: Model,
    ): String {
        val desc = registry.get(entity) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val found = crud.findById(entity, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val detailAttrs = desc.detailAttributes
        val attributes = mutableListOf<String>()
        val values = mutableListOf<String?>()
        val links = mutableListOf<String?>()

        detailAttrs.forEach { attr ->
            attributes += attr.name
            try {
                val field = found.javaClass.getDeclaredField(attr.name)
                field.isAccessible = true
                val value = field.get(found)
                if (value == null) {
                    values += null
                    links += null
                } else if (
                    attr.persistentAttributeType in
                    setOf(
                        Attribute.PersistentAttributeType.MANY_TO_ONE,
                        Attribute.PersistentAttributeType.ONE_TO_ONE,
                    )
                ) {
                    val targetDesc = registry.getByJavaType(value.javaClass)
                    val idVal = crud.getId(value)
                    val text = value.toString()
                    val href =
                        if (targetDesc != null && idVal != null) {
                            props.basePath.trimEnd('/') + "/" + targetDesc.entityName + "/" + idVal
                        } else {
                            null
                        }
                    values += text
                    links += href
                } else {
                    values += value.toString()
                    links += null
                }
            } catch (ex: Exception) {
                values += null
                links += null
            }
        }

        // Discover collection relations annotated with @OneToMany or @ManyToMany
        val collectionTables: MutableList<Map<String, Any?>> = mutableListOf()
        found.javaClass.declaredFields
            .filter { field ->
                field.isAnnotationPresent(OneToMany::class.java) || field.isAnnotationPresent(ManyToMany::class.java)
            }.forEach { field ->
                try {
                    field.isAccessible = true
                    val raw = field.get(found)

                    // Determine target descriptor by inspecting a sample element; avoids relying on targetEntity (works with proxies too)
                    var sampleElem: Any? = null
                    var totalCount = 0
                    if (raw is Iterable<*>) {
                        for (elem in raw) {
                            if (elem != null) {
                                sampleElem = elem
                                break
                            }
                        }
                        totalCount = (raw as? Collection<*>)?.size ?: raw.count { true }
                    }

                    val targetDesc = if (sampleElem != null) registry.getByJavaType(sampleElem.javaClass) else null

                    val attributeTitles: List<String> = targetDesc?.attributes?.map { it.name } ?: emptyList()

                    // Build rows limited to a reasonable number to avoid huge pages
                    val maxRows = 100
                    val rows: MutableList<List<Any?>> = mutableListOf()
                    val rowIds: MutableList<Any?> = mutableListOf()
                    if (raw is Iterable<*>) {
                        var i = 0
                        for (elem in raw) {
                            if (elem == null) continue
                            if (i >= maxRows) break
                            val row =
                                attributeTitles.map { attrName ->
                                    try {
                                        val fld = elem.javaClass.getDeclaredField(attrName)
                                        fld.isAccessible = true
                                        fld.get(elem)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            rows += row
                            rowIds += crud.getId(elem)
                            i++
                        }
                    }

                    collectionTables +=
                        mapOf(
                            "name" to field.name,
                            "targetEntity" to (targetDesc?.entityName ?: ""),
                            "targetDisplayName" to (targetDesc?.displayName ?: field.name),
                            "attributeTitles" to attributeTitles,
                            "rows" to rows,
                            "rowIds" to rowIds,
                            "totalCount" to totalCount,
                            "limited" to (totalCount > rows.size),
                        )
                } catch (_: Exception) {
                    // ignore individual relation failures
                }
            }

        model.addAttribute("title", props.ui.title)
        model.addAttribute("descriptor", desc)
        model.addAttribute("attributes", attributes)
        model.addAttribute("values", values)
        model.addAttribute("links", links)
        model.addAttribute("collectionTables", collectionTables)
        model.addAttribute("id", id)
        model.addAttribute("basePath", props.basePath)
        model.addAttribute("entities", registry.all())

        return "sda/entity-detail"
    }
}
