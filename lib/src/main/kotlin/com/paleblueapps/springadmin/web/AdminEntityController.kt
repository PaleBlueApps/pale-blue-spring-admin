package com.paleblueapps.springadmin.web

import com.paleblueapps.springadmin.autoconfigure.AdminProperties
import com.paleblueapps.springadmin.core.AdminCrudService
import com.paleblueapps.springadmin.core.AdminEntityDescriptor
import com.paleblueapps.springadmin.core.AdminEntityRegistry
import com.paleblueapps.springadmin.core.PaginationInfo
import jakarta.persistence.Column
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.metamodel.Attribute
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
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

    private fun extractAttributeTitlesForList(entityDesc: AdminEntityDescriptor): List<String> =
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
        model.addAttribute("selectedAction", "")

        return "sda/entity-list"
    }

    @PostMapping("{entity}/actions")
    fun listAction(
        @PathVariable entity: String,
        @RequestParam(required = false) action: String?,
        @RequestParam(name = "selectedIds", required = false) selectedIds: List<String>?,
        @RequestParam(defaultValue = "false") selectAllMatching: Boolean,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) dir: String?,
        @RequestParam(required = false) q: String?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val desc = getDescriptorOr404(entity)
        val ids = resolveSelectedIds(entity, selectedIds, selectAllMatching, sort, dir, q)

        if (action.isNullOrBlank()) {
            redirectAttributes.addFlashAttribute("error", "Please choose an action.")
            return "redirect:${props.basePath}/${desc.entityName}"
        }

        if (ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Items must be selected in order to perform actions on them. No items have been changed.")
            return "redirect:${props.basePath}/${desc.entityName}"
        }

        return when (action) {
            "delete" -> {
                redirectAttributes.addFlashAttribute("selectedIds", ids)
                "redirect:${props.basePath}/${desc.entityName}/delete"
            }

            else -> {
                redirectAttributes.addFlashAttribute("error", "Unknown action: $action")
                "redirect:${props.basePath}/${desc.entityName}"
            }
        }
    }

    @GetMapping("{entity}/delete")
    fun bulkDeleteConfirmation(
        @PathVariable entity: String,
        @ModelAttribute("selectedIds") selectedIds: List<String>?,
        model: Model,
    ): String {
        val desc = getDescriptorOr404(entity)
        val ids = selectedIds?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty()
        if (ids.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No items selected for deletion")
        }

        val objects = ids.mapNotNull { id ->
            crud.findById(entity, id)?.let { found ->
                mapOf(
                    "id" to id,
                    "label" to found.toString(),
                )
            }
        }

        if (objects.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        addCommonUiContext(model)
        model.addAttribute("descriptor", desc)
        model.addAttribute("selectedIds", objects.map { it["id"] })
        model.addAttribute("deleteObjects", objects)

        return "sda/entity-delete-bulk"
    }

    @PostMapping("{entity}/delete")
    fun bulkDelete(
        @PathVariable entity: String,
        @RequestParam(name = "selectedIds", required = false) selectedIds: List<String>?,
        redirectAttributes: RedirectAttributes,
    ): String {
        val desc = getDescriptorOr404(entity)
        val ids = selectedIds?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty()

        if (ids.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Items must be selected in order to perform actions on them. No items have been changed.")
            return "redirect:${props.basePath}/${desc.entityName}"
        }

        return try {
            val deletedCount = crud.deleteAllByIds(entity, ids)
            val missingCount = ids.size - deletedCount
            redirectAttributes.addFlashAttribute("message", buildBulkDeleteMessage(desc.displayName, deletedCount, missingCount))
            "redirect:${props.basePath}/${desc.entityName}"
        } catch (ex: Exception) {
            redirectAttributes.addFlashAttribute(
                "error",
                "Could not delete the selected ${desc.displayName.lowercase()} records. Remove related records first and try again.",
            )
            redirectAttributes.addFlashAttribute("selectedIds", ids)
            "redirect:${props.basePath}/${desc.entityName}/delete"
        }
    }

    @GetMapping("{entity}/{id}")
    fun detail(
        @PathVariable entity: String,
        @PathVariable id: String,
        model: Model,
    ): String {
        val desc = getDescriptorOr404(entity)
        val found = crud.findById(entity, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val collectionTables = buildCollectionPreviews(found)
        val editFields = buildEditFields(found, desc)

        addCommonUiContext(model)
        model.addAttribute("descriptor", desc)
        model.addAttribute("editFields", editFields)
        model.addAttribute("collectionTables", collectionTables)
        model.addAttribute("id", id)

        return "sda/entity-detail"
    }

    @PostMapping("{entity}/{id}")
    fun update(
        @PathVariable entity: String,
        @PathVariable id: String,
        @RequestParam params: Map<String, String>,
        redirectAttributes: RedirectAttributes,
    ): String {
        val desc = getDescriptorOr404(entity)

        return try {
            crud.update(entity, id, params)
            redirectAttributes.addFlashAttribute("message", "The ${desc.displayName} \"$id\" was saved successfully.")
            "redirect:${props.basePath}/${desc.entityName}/$id"
        } catch (ex: IllegalArgumentException) {
            redirectAttributes.addFlashAttribute("error", ex.message ?: "Could not save ${desc.displayName} \"$id\".")
            "redirect:${props.basePath}/${desc.entityName}/$id"
        }
    }

    @GetMapping("{entity}/{id}/delete")
    fun deleteConfirmation(
        @PathVariable entity: String,
        @PathVariable id: String,
        model: Model,
    ): String {
        val desc = getDescriptorOr404(entity)
        val found = crud.findById(entity, id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)

        addCommonUiContext(model)
        model.addAttribute("descriptor", desc)
        model.addAttribute("id", id)
        model.addAttribute("deleteObjectLabel", found.toString())

        return "sda/entity-delete"
    }

    @PostMapping("{entity}/{id}/delete")
    fun delete(
        @PathVariable entity: String,
        @PathVariable id: String,
        redirectAttributes: RedirectAttributes,
    ): String {
        val desc = getDescriptorOr404(entity)

        return try {
            val deleted = crud.deleteById(entity, id)
            if (deleted) {
                redirectAttributes.addFlashAttribute("message", "The ${desc.displayName} \"$id\" was deleted successfully.")
            } else {
                redirectAttributes.addFlashAttribute("error", "The ${desc.displayName} \"$id\" no longer exists.")
            }
            "redirect:${props.basePath}/${desc.entityName}"
        } catch (ex: Exception) {
            redirectAttributes.addFlashAttribute(
                "error",
                "Could not delete ${desc.displayName} \"$id\". Remove related records first and try again.",
            )
            "redirect:${props.basePath}/${desc.entityName}/$id/delete"
        }
    }

    private fun buildEditFields(
        found: Any,
        desc: AdminEntityDescriptor,
    ): List<Map<String, Any?>> =
        desc.detailAttributes.map { attr ->
            val value = readFieldValue(found, attr.name)
            val relationDesc = if (isAssociation(attr)) registry.getByJavaType(attr.javaType) else null
            val relationOptions =
                relationDesc?.let { related ->
                    crud.listAll(related.entityName).map { option ->
                        mapOf(
                            "id" to crud.getId(option)?.toString(),
                            "label" to option.toString(),
                        )
                    }
                }.orEmpty()

            mapOf(
                "name" to attr.name,
                "label" to humanize(attr.name),
                "inputType" to inputTypeFor(attr),
                "value" to formatValue(attr, value),
                "required" to (attr.name != desc.idAttribute && isRequired(attr)),
                "readonly" to (attr.name == desc.idAttribute),
                "relation" to (relationDesc != null),
                "relationOptions" to relationOptions,
                "relationValue" to (if (relationDesc != null) value?.let { crud.getId(it)?.toString() } else null),
                "relationEntityName" to relationDesc?.entityName,
            )
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

    private fun readFieldValue(
        target: Any,
        fieldName: String,
    ): Any? =
        try {
            val field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(target)
        } catch (_: Exception) {
            null
        }

    private fun isAssociation(attr: Attribute<*, *>): Boolean =
        attr.persistentAttributeType in
            setOf(
                Attribute.PersistentAttributeType.MANY_TO_ONE,
                Attribute.PersistentAttributeType.ONE_TO_ONE,
            )

    private fun isRequired(attr: Attribute<*, *>): Boolean {
        val annotated = attr.javaMember as? java.lang.reflect.AnnotatedElement ?: return false
        val column = annotated.getAnnotation(Column::class.java)
        val joinColumn = annotated.getAnnotation(JoinColumn::class.java)
        return when {
            joinColumn != null -> !joinColumn.nullable
            column != null -> !column.nullable
            attr.javaType.isPrimitive -> true
            else -> false
        }
    }

    private fun inputTypeFor(attr: Attribute<*, *>): String =
        if (isTextArea(attr)) {
            "textarea"
        } else {
        when (attr.javaType) {
            Int::class.java,
            java.lang.Integer::class.java,
            Long::class.java,
            java.lang.Long::class.java,
            Short::class.java,
            java.lang.Short::class.java,
            Double::class.java,
            java.lang.Double::class.java,
            Float::class.java,
            java.lang.Float::class.java,
            -> "number"
            LocalDate::class.java -> "date"
            LocalDateTime::class.java, OffsetDateTime::class.java -> "datetime-local"
            java.lang.Boolean::class.java, Boolean::class.java -> "checkbox"
            else -> "text"
        }
        }

    private fun formatValue(
        attr: Attribute<*, *>,
        value: Any?,
    ): String? =
        when (value) {
            null -> null
            is LocalDate -> value.toString()
            is LocalDateTime -> value.toString().substringBeforeLast(":")
            is OffsetDateTime -> value.toLocalDateTime().toString().substringBeforeLast(":")
            is Boolean -> value.toString()
            else -> if (isAssociation(attr)) null else value.toString()
        }

    private fun isTextArea(attr: Attribute<*, *>): Boolean {
        val annotated = attr.javaMember as? java.lang.reflect.AnnotatedElement ?: return false
        val column = annotated.getAnnotation(Column::class.java)
        return annotated.getAnnotation(Lob::class.java) != null || column?.columnDefinition?.contains("TEXT", ignoreCase = true) == true
    }

    private fun humanize(name: String): String =
        name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

    private fun buildBulkDeleteMessage(
        displayName: String,
        deletedCount: Int,
        missingCount: Int,
    ): String {
        val deletedPart =
            when (deletedCount) {
                0 -> "No ${displayName.lowercase()} records were deleted"
                1 -> "Successfully deleted 1 $displayName"
                else -> "Successfully deleted $deletedCount ${displayName.lowercase()} records"
            }

        return if (missingCount > 0) {
            "$deletedPart. $missingCount selected record(s) no longer existed."
        } else {
            "$deletedPart."
        }
    }

    private fun resolveSelectedIds(
        entity: String,
        selectedIds: List<String>?,
        selectAllMatching: Boolean,
        sort: String?,
        dir: String?,
        q: String?,
    ): List<String> =
        if (selectAllMatching) {
            crud.listIds(entity, sort, dir, q).distinct()
        } else {
            selectedIds?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty()
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
        val allRowIds = buildRowIds(sorted)

        val listBaseUrl = props.basePath + "/" + parentDesc.entityName + "/" + id + "/rel/" + rel
        val paginationInfo = PaginationInfo.from(dataPage)

        addCommonUiContext(model)
        model.addAttribute("parentDescriptor", parentDesc)
        model.addAttribute("descriptor", targetDesc)
        model.addAttribute("relName", rel)
        model.addAttribute("attributeTitles", attributeTitles)
        model.addAttribute("rows", rows)
        model.addAttribute("rowIds", rowIds)
        model.addAttribute("allRowIds", allRowIds)
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
