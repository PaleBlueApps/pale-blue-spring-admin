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
    // region: List endpoint helpers
    private fun getDescriptorOr404(entity: String) = registry.get(entity) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

    private fun computePageSize(requested: Int): Int =
        if (requested <= 0) props.pagination.defaultSize else requested.coerceAtMost(props.pagination.maxSize)

    private fun extractAttributeTitlesForList(entityDesc: com.paleblueapps.springadmin.core.AdminEntityDescriptor): List<String> =
        entityDesc.attributes.map { it.name }

    private fun buildRows(
        attributeTitles: List<String>,
        content: List<Any>,
    ): List<List<Any?>> =
        content.map { entityObj ->
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

    private fun buildRowIds(content: List<Any>): List<Any?> = content.map { crud.getId(it) }

    private fun pageSizeOptions(): List<Int> = listOf(10, 25, 50, 100, 200).filter { it <= props.pagination.maxSize }

    private fun addCommonUiContext(model: Model) {
        model.addAttribute("title", props.ui.title)
        model.addAttribute("basePath", props.basePath)
        model.addAttribute("entities", registry.all())
    }

    // endregion
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
        val desc = getDescriptorOr404(entity)
        val attributeTitles = extractAttributeTitlesForList(desc)
        val pageSize = computePageSize(size)
        val data = crud.list(entity, page, pageSize, sort, dir, q)
        val rows = buildRows(attributeTitles, data.content)
        val rowIds = buildRowIds(data.content)
        val paginationInfo = PaginationInfo.from(data)

        addCommonUiContext(model)
        model.addAttribute("descriptor", desc)
        model.addAttribute("attributeTitles", attributeTitles)
        model.addAttribute("data", data)
        model.addAttribute("rows", rows)
        model.addAttribute("rowIds", rowIds)
        model.addAttribute("pagination", paginationInfo)
        model.addAttribute("sort", sort)
        model.addAttribute("dir", dir)
        model.addAttribute("q", q)
        model.addAttribute("size", pageSize)
        model.addAttribute("pageSizes", pageSizeOptions())

        return "sda/entity-list"
    }

    @GetMapping("{entity}/{id}")
    fun detail(
        @PathVariable entity: String,
        @PathVariable id: String,
        model: Model,
    ): String {
        val desc = getDescriptorOr404(entity)
        val found = crud.findById(entity, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        val (attributes, values, links) = buildDetailRows(found, desc.detailAttributes)
        val collectionTables = buildCollectionPreviews(found)

        addCommonUiContext(model)
        model.addAttribute("descriptor", desc)
        model.addAttribute("attributes", attributes)
        model.addAttribute("values", values)
        model.addAttribute("links", links)
        model.addAttribute("collectionTables", collectionTables)
        model.addAttribute("id", id)

        return "sda/entity-detail"
    }

    private fun buildDetailRows(
        found: Any,
        detailAttrs: List<jakarta.persistence.metamodel.Attribute<*, *>>,
    ): Triple<List<String>, List<String?>, List<String?>> {
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
            } catch (_: Exception) {
                values += null
                links += null
            }
        }

        return Triple(attributes, values, links)
    }

    private fun buildCollectionPreviews(found: Any): List<Map<String, Any?>> {
        val collectionTables: MutableList<Map<String, Any?>> = mutableListOf()
        found.javaClass.declaredFields
            .filter { field ->
                field.isAnnotationPresent(OneToMany::class.java) || field.isAnnotationPresent(ManyToMany::class.java)
            }.forEach { field ->
                try {
                    field.isAccessible = true
                    val raw = field.get(found)

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

                    val maxRows = props.pagination.defaultSize
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
                            "previewLimit" to maxRows,
                        )
                } catch (_: Exception) {
                    // ignore individual relation failures
                }
            }
        return collectionTables
    }

    // Paginated and searchable view for collection relations from an entity detail page
    @GetMapping("{entity}/{id}/rel/{rel}")
    fun relationList(
        @PathVariable entity: String,
        @PathVariable id: String,
        @PathVariable rel: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "-1") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) dir: String?,
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val parentDesc = getDescriptorOr404(entity)
        val parent = crud.findById(entity, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val pageSize = computePageSize(size)

        val items = loadRelationItems(parent, rel)
        val targetDesc = determineTargetDescriptor(items)
        val attributeTitles = targetDesc.attributes.map { it.name }

        val filtered = filterItems(items, attributeTitles, q)
        val sorted = sortItems(filtered, sort, dir, attributeTitles)
        val dataPage = paginate(sorted, page, pageSize)
        val rows = buildRows(attributeTitles, dataPage.content)
        val rowIds = buildRowIds(dataPage.content)

        val listBaseUrl = props.basePath + "/" + parentDesc.entityName + "/" + id + "/rel/" + rel
        val paginationInfo = PaginationInfo.from(dataPage)

        addCommonUiContext(model)
        model.addAttribute("parentDescriptor", parentDesc)
        model.addAttribute("descriptor", targetDesc)
        model.addAttribute("relName", rel)
        model.addAttribute("attributeTitles", attributeTitles)
        model.addAttribute("rows", rows)
        model.addAttribute("rowIds", rowIds)
        model.addAttribute("pagination", paginationInfo)
        model.addAttribute("listBaseUrl", listBaseUrl)
        model.addAttribute("sort", sort)
        model.addAttribute("dir", dir)
        model.addAttribute("q", q)
        model.addAttribute("size", pageSize)
        model.addAttribute("pageSizes", pageSizeOptions())

        return "sda/relation-list"
    }

    private fun loadRelationItems(
        parent: Any,
        rel: String,
    ): List<Any> {
        val field =
            try {
                parent.javaClass.getDeclaredField(rel).apply { isAccessible = true }
            } catch (ex: Exception) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown relation: $rel")
            }
        val raw = field.get(parent)
        return when (raw) {
            is Iterable<*> -> raw.filterNotNull().map { it as Any }
            else -> emptyList()
        }
    }

    private fun determineTargetDescriptor(items: List<Any>) =
        items.firstOrNull()?.let { registry.getByJavaType(it.javaClass) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Empty or unknown relation type")

    private fun filterItems(
        items: List<Any>,
        attributeTitles: List<String>,
        q: String?,
    ): List<Any> {
        if (q.isNullOrBlank()) return items
        val qlc = q.trim().lowercase()
        return items.filter { elem ->
            try {
                val idVal = crud.getId(elem)?.toString()?.lowercase()
                val idMatch = idVal?.contains(qlc) == true
                val fieldMatch =
                    attributeTitles.any { an ->
                        try {
                            val f = elem.javaClass.getDeclaredField(an)
                            f.isAccessible = true
                            val v = f.get(elem)
                            (v as? String)?.lowercase()?.contains(qlc) == true
                        } catch (_: Exception) {
                            false
                        }
                    }
                idMatch || fieldMatch
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun sortItems(
        items: List<Any>,
        sort: String?,
        dir: String?,
        attributeTitles: List<String>,
    ): List<Any> {
        if (sort.isNullOrBlank() || !attributeTitles.contains(sort)) return items
        val comparator =
            Comparator<Any> { a, b ->
                val av =
                    try {
                        val f = a.javaClass.getDeclaredField(sort).apply { isAccessible = true }
                        @Suppress("UNCHECKED_CAST")
                        f.get(a) as? Comparable<Any>
                    } catch (_: Exception) {
                        null
                    }
                val bv =
                    try {
                        val f = b.javaClass.getDeclaredField(sort).apply { isAccessible = true }
                        @Suppress("UNCHECKED_CAST")
                        f.get(b) as? Comparable<Any>
                    } catch (_: Exception) {
                        null
                    }

                when {
                    av == null && bv == null -> 0
                    av == null -> -1
                    bv == null -> 1
                    else -> av.compareTo(bv)
                }
            }
        return if (dir.equals("desc", true)) items.sortedWith(comparator.reversed()) else items.sortedWith(comparator)
    }

    private fun paginate(
        items: List<Any>,
        page: Int,
        pageSize: Int,
    ): com.paleblueapps.springadmin.core.DataPage<Any> {
        val total = items.size
        val from = (page.coerceAtLeast(0) * pageSize).coerceAtMost(total)
        val to = (from + pageSize).coerceAtMost(total)
        val pageContent = items.subList(from, to)
        return com.paleblueapps.springadmin.core.DataPage(
            content = pageContent,
            page = page,
            size = pageSize,
            totalElements = total.toLong(),
        )
    }
}
