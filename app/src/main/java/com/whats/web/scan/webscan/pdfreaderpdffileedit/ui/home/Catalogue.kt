package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.whats.web.scan.webscan.pdfreaderpdffileedit.R
import com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.DocType
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.FolderYellow
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.ToolBlue
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.ToolPurple
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeExcel
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePdf
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypePpt
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeText
import com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.theme.TypeWord

/**
 * The "All Files" cards on Home, and the list screen each one opens. [type] is the one family the
 * list is limited to; ALL, FOLDERS and FAVOURITES are not limited by type.
 */
enum class LibraryCategory(
    @StringRes val label: Int,
    @StringRes val listTitle: Int,
    val color: Color,
    val type: DocType? = null,
) {
    ALL(R.string.category_all, R.string.list_all_files, ToolBlue),
    PDF(R.string.category_pdf, R.string.list_pdf_files, TypePdf, DocType.PDF),
    WORD(R.string.category_word, R.string.list_word_files, TypeWord, DocType.WORD),
    EXCEL(R.string.category_excel, R.string.list_excel_files, TypeExcel, DocType.EXCEL),
    PPT(R.string.category_ppt, R.string.list_ppt_files, TypePpt, DocType.PPT),
    TEXT(R.string.category_txt, R.string.list_txt_files, TypeText, DocType.TEXT),
    FOLDERS(R.string.category_directories, R.string.category_directories, ToolPurple),
    FAVOURITES(R.string.category_favorites, R.string.category_favorites, FolderYellow),
    ;

    companion object {
        fun of(type: DocType): LibraryCategory = entries.first { it.type == type }
    }
}

/** Which Home section a tool is listed under. */
enum class ToolSection(@StringRes val title: Int) {
    CREATE(R.string.section_create_convert),
    EDIT(R.string.section_edit_manage),
    AI(R.string.section_ai_tools),
}

/**
 * Every tool on Home, in the order shown. [input] is the family of file the tool asks for first
 * (null: the tool needs no file, or picks its own). [badge] is the small label on the tile's corner that
 * says what the tool turns the file into, as the converter tiles in One Read and Document Reader do.
 */
enum class Tool(
    val section: ToolSection,
    @StringRes val label: Int,
    val color: Color,
    val input: DocType? = null,
    val badge: String? = null,
) {
    IMAGE_TO_PDF(ToolSection.CREATE, R.string.tool_image_to_pdf, TypePpt, badge = "PDF"),
    SCAN_TO_PDF(ToolSection.CREATE, R.string.tool_scan_to_pdf, TypeExcel),
    CREATE_PDF(ToolSection.CREATE, R.string.tool_create_pdf, TypePdf),
    PDF_TO_WORD(ToolSection.CREATE, R.string.tool_pdf_to_word, TypeWord, DocType.PDF, badge = "W"),
    WORD_TO_PDF(ToolSection.CREATE, R.string.tool_word_to_pdf, TypePdf, DocType.WORD, badge = "PDF"),
    PDF_TO_IMAGE(ToolSection.CREATE, R.string.tool_pdf_to_image, ToolBlue, DocType.PDF, badge = "JPG"),
    PPT_TO_PDF(ToolSection.CREATE, R.string.tool_ppt_to_pdf, TypePpt, DocType.PPT, badge = "PDF"),
    EXCEL_TO_PDF(ToolSection.CREATE, R.string.tool_excel_to_pdf, TypeExcel, DocType.EXCEL, badge = "PDF"),

    EDIT_TEXT(ToolSection.EDIT, R.string.tool_edit_text, ToolBlue, DocType.PDF),
    ANNOTATE(ToolSection.EDIT, R.string.tool_annotate, ToolPurple, DocType.PDF),
    ADD_TEXT(ToolSection.EDIT, R.string.tool_add_text, TypePdf, DocType.PDF),
    FILL_SIGN(ToolSection.EDIT, R.string.tool_fill_sign, TypePpt, DocType.PDF),
    MERGE_PDF(ToolSection.EDIT, R.string.tool_merge_pdf, TypePpt),
    SPLIT_PDF(ToolSection.EDIT, R.string.tool_split_pdf, ToolBlue, DocType.PDF),
    MANAGE_PAGES(ToolSection.EDIT, R.string.tool_manage_pages, ToolPurple, DocType.PDF),
    COMPRESS_PDF(ToolSection.EDIT, R.string.tool_compress_pdf, TypeExcel, DocType.PDF),
    PROTECT_PDF(ToolSection.EDIT, R.string.tool_protect_pdf, TypeText, DocType.PDF),
    RECYCLE_BIN(ToolSection.EDIT, R.string.tool_recycle_bin, TypeText),

    AI_TRANSLATE(ToolSection.AI, R.string.tool_ai_translate, ToolBlue),
    AI_SUMMARY(ToolSection.AI, R.string.tool_ai_summary, ToolPurple),
    AI_EXTRACT(ToolSection.AI, R.string.tool_ai_extract, TypeExcel),
}

/** The tools the file menu offers for a file of [type] (besides share, info and delete). */
fun toolsFor(type: DocType): List<Tool> = when (type) {
    DocType.PDF -> listOf(Tool.EDIT_TEXT, Tool.PDF_TO_WORD, Tool.PDF_TO_IMAGE, Tool.COMPRESS_PDF)
    DocType.WORD -> listOf(Tool.WORD_TO_PDF)
    DocType.PPT -> listOf(Tool.PPT_TO_PDF)
    DocType.EXCEL -> listOf(Tool.EXCEL_TO_PDF)
    DocType.TEXT -> emptyList()
}.filter { it in readyTools }

/** Tools whose screens exist; the rest stay off Home until their step lands. */
val readyTools: Set<Tool> = setOf(
    Tool.IMAGE_TO_PDF,
    Tool.SCAN_TO_PDF,
    Tool.CREATE_PDF,
    Tool.EDIT_TEXT,
    Tool.ANNOTATE,
    Tool.ADD_TEXT,
    Tool.FILL_SIGN,
    Tool.MERGE_PDF,
    Tool.SPLIT_PDF,
    Tool.MANAGE_PAGES,
    Tool.RECYCLE_BIN,
    Tool.AI_TRANSLATE,
    Tool.AI_SUMMARY,
    Tool.AI_EXTRACT,
)

/** How the tool reads in a file's ⋮ menu, where the file is already chosen. */
val Tool.menuLabel: Int
    get() = when (this) {
        Tool.EDIT_TEXT -> R.string.menu_edit_pdf
        Tool.PDF_TO_WORD -> R.string.menu_convert_word
        Tool.PDF_TO_IMAGE -> R.string.menu_convert_images
        Tool.WORD_TO_PDF, Tool.PPT_TO_PDF, Tool.EXCEL_TO_PDF -> R.string.menu_convert_pdf
        else -> label
    }
